package com.evdeman.flip2calendar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// --- HTTP helpers -----------------------------------------------------
// Centralizes connection handling so every caller gets:
//   1) a guaranteed conn.disconnect() (success or failure)
//   2) the real response body on error (conn.errorStream), not just a
//      generic IOException message
//   3) a typed HttpException carrying the real status code, so callers
//      can check `e.code == 401` instead of string-matching e.message

private class HttpException(val code: Int, val body: String) : Exception("HTTP $code: $body")

private fun HttpURLConnection.readResponseOrThrow(): String {
    try {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw HttpException(code, text)
        return text
    } finally {
        disconnect()
    }
}

private fun httpGet(url: URL, accessToken: String): String {
    val conn = url.openConnection() as HttpURLConnection
    conn.setRequestProperty("Authorization", "Bearer $accessToken")
    return conn.readResponseOrThrow()
}

private fun httpPostForm(url: URL, body: String): String {
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    return conn.readResponseOrThrow()
}

data class CalendarInfo(
    val id: String,
    val name: String,
    val color: Int,
    val isReadOnly: Boolean
)

data class CalendarEvent(
    val title: String,
    val startTime: String,
    val endTime: String,
    val isAllDay: Boolean,
    val calendarName: String,
    val calendarColor: Int,
    val isHoliday: Boolean,
    val dateKey: String,
    val eventId: String = "",
    val calendarId: String = "",
    val isRecurring: Boolean = false,
    val location: String = "",
    val description: String = "",
    val isReadOnly: Boolean = false
)

sealed class ListItem {
    data class DateHeader(val label: String) : ListItem()
    data class Event(val event: CalendarEvent) : ListItem()
}

class MainActivity : AppCompatActivity() {
    companion object {
        const val REDIRECT_URI = "http://localhost"
        const val SCOPE = "https://www.googleapis.com/auth/calendar"
        const val PREFS_NAME = "flip2cal_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_SHOW_HOLIDAYS = "show_holidays"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_CLIENT_SECRET = "client_secret"
        const val KEY_HIDE_FUTURE = "hide_future"
        const val KEY_DAYS_AHEAD = "days_ahead"
        const val DEFAULT_DAYS_AHEAD = 7

        fun getClientId(context: android.content.Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            return prefs.getString(KEY_CLIENT_ID, "") ?: ""
        }

        fun getClientSecret(context: android.content.Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            return prefs.getString(KEY_CLIENT_SECRET, "") ?: ""
        }
    }

    private lateinit var listView: ListView
    private lateinit var tvStatus: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var tvNewEvent: TextView
    private lateinit var tvOptions: TextView

    private val optionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            if (accessToken != null) {
                fetchCalendarEvents(accessToken)
            }
        }
    }

    private var allEvents = mutableListOf<CalendarEvent>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect to setup if credentials not configured
        val storedClientId = getClientId(this)
        if (storedClientId.isEmpty()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        listView = findViewById(R.id.listView)
        listView.itemsCanFocus = false
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        tvStatus = findViewById(R.id.tvStatus)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        updateTitle()

        tvNewEvent = findViewById(R.id.tvNewEvent)
        tvNewEvent.setOnClickListener {
            val selectedPos = listView.selectedItemPosition
            val dateStr = if (selectedPos >= 0) {
                val adapter = listView.adapter
                val item = adapter.getItem(selectedPos)
                when (item) {
                    is ListItem.Event -> item.event.dateKey
                    is ListItem.DateHeader -> {
                        // find next event after this header
                        val nextEvent = (selectedPos + 1 until adapter.count)
                            .map { adapter.getItem(it) }
                            .filterIsInstance<ListItem.Event>()
                            .firstOrNull()
                        nextEvent?.event?.dateKey ?: todayDateStr()
                    }
                    else -> todayDateStr()
                }
            } else todayDateStr()
            EventEditActivity.startForNew(this, dateStr)
        }
        tvNewEvent.setOnFocusChangeListener { _, hasFocus ->
            tvNewEvent.setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#4a9aff"))
            tvNewEvent.setBackgroundColor(if (hasFocus) Color.parseColor("#2a4a7a") else Color.TRANSPARENT)
        }

        tvOptions = findViewById(R.id.tvOptions)
        tvOptions.setOnClickListener {
            optionsLauncher.launch(Intent(this, OptionsActivity::class.java))
        }
        tvOptions.setOnFocusChangeListener { _, hasFocus ->
            tvOptions.setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#AAAAAA"))
            tvOptions.setBackgroundColor(if (hasFocus) Color.parseColor("#2a4a7a") else Color.TRANSPARENT)
        }

        // Ensure DPad can reach the New Event and Options buttons
        tvNewEvent.isFocusable = true
        tvNewEvent.isFocusableInTouchMode = true
        tvOptions.isFocusable = true
        tvOptions.isFocusableInTouchMode = true

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        // Handle OAuth redirect if app was launched fresh from browser
        val code = intent?.data?.getQueryParameter("code")
        if (code != null) {
            scope.launch {
                delay(500)
                exchangeCodeForToken(code)
            }
        } else {
            val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            if (accessToken == null) {
                startOAuth()
            } else {
                fetchCalendarEvents(accessToken)
            }
        }
    }

    private fun updateTitle() {
        val dayFormat = SimpleDateFormat("EEEE", Locale.US)
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
        val today = Date()
        findViewById<TextView>(R.id.tvTitle).text =
            "${dayFormat.format(today)}\n${dateFormat.format(today)}"
    }

    private fun startOAuth() {
        tvStatus.text = "Opening browser for Google sign-in..."
        val authUrl = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
            .buildUpon()
            .appendQueryParameter("client_id", getClientId(this))
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
        startActivity(Intent(Intent.ACTION_VIEW, authUrl))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val code = intent.data?.getQueryParameter("code")
        if (code != null) {
            // Small delay to ensure activity is fully resumed
            scope.launch {
                delay(500)
                exchangeCodeForToken(code)
            }
        }
    }

    private fun exchangeCodeForToken(code: String) {
        tvStatus.text = "Authenticating..."
        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    val body = "code=${Uri.encode(code)}" +
                            "&client_id=${Uri.encode(getClientId(this@MainActivity))}" +
                            "&client_secret=${Uri.encode(getClientSecret(this@MainActivity))}" +
                            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
                            "&grant_type=authorization_code"
                    val response = httpPostForm(URL("https://oauth2.googleapis.com/token"), body)
                    JSONObject(response)
                }
                val accessToken = token.getString("access_token")
                val refreshToken = token.optString("refresh_token")
                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, accessToken)
                    .putString(KEY_REFRESH_TOKEN, refreshToken)
                    .apply()
                fetchCalendarEvents(accessToken)
            } catch (e: Exception) {
                tvStatus.text = "Auth failed: ${e.message}"
                Toast.makeText(this@MainActivity, "Auth error: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun fetchCalendarEvents(accessToken: String) {
        tvStatus.text = "Fetching calendars..."
        scope.launch {
            try {
                val events = withContext(Dispatchers.IO) {
                    val calendars = getCalendarList(accessToken)
                    val eventsList = mutableListOf<CalendarEvent>()
                    val now = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val end = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 60) }
                    val timeMin = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .format(now.time)
                    val timeMax = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .format(end.time)

                    for (calendar in calendars) {
                        val calId = calendar.id
                        val calName = calendar.name
                        val calColor = calendar.color
                        val isReadOnly = calendar.isReadOnly
                        val isHoliday = calName.contains("Holiday", ignoreCase = true)

                        // Per-calendar try/catch: one bad/inaccessible calendar
                        // should not blank out every other calendar's events.
                        // A 401 is the exception - that means the *token* is
                        // bad, not just this calendar, so it's rethrown to be
                        // handled by the outer catch (which triggers refresh).
                        try {
                            val eventsUrl = URL(
                                "https://www.googleapis.com/calendar/v3/calendars/" +
                                        "${Uri.encode(calId)}/events" +
                                        "?timeMin=$timeMin&timeMax=$timeMax" +
                                        "&singleEvents=true&orderBy=startTime&maxResults=300"
                            )
                            val response = httpGet(eventsUrl, accessToken)
                            val json = JSONObject(response)
                            val items = json.optJSONArray("items") ?: continue
                            for (i in 0 until items.length()) {
                                val item = items.getJSONObject(i)
                                val summary = item.optString("summary", "(No title)")
                                val start = item.optJSONObject("start")
                                val end2 = item.optJSONObject("end")
                                // All-day events carry a "date" field instead of
                                // "dateTime" - this used to be checked twice
                                // (start?.has("date") == true && start.has("date")),
                                // which is redundant and not the same as verifying
                                // it's NOT a timed event.
                                val isAllDay = start?.has("date") == true
                                val startStr = if (isAllDay) {
                                    start?.optString("date") ?: ""
                                } else {
                                    start?.optString("dateTime") ?: ""
                                }
                                val endStr = end2?.optString("dateTime")
                                    ?: end2?.optString("date") ?: ""
                                val dateKey = if (isAllDay) startStr else if (startStr.length >= 10) startStr.substring(0, 10) else ""
                                android.util.Log.d("Flip2Cal", "Event: $summary dateKey: $dateKey startStr: $startStr isAllDay: $isAllDay")
                                val startFormatted = formatTime(startStr, isAllDay)
                                val endFormatted = formatTime(endStr, isAllDay)
                                val eventId = item.optString("id", "")
                                val isRecurring = item.has("recurringEventId") || item.has("recurrence")
                                val location = item.optString("location", "")
                                val description = item.optString("description", "")
                                eventsList.add(
                                    CalendarEvent(
                                        title = summary,
                                        startTime = startFormatted,
                                        endTime = endFormatted,
                                        isAllDay = isAllDay,
                                        calendarName = calName,
                                        calendarColor = calColor,
                                        isHoliday = isHoliday,
                                        dateKey = dateKey,
                                        eventId = eventId,
                                        calendarId = calId,
                                        isRecurring = isRecurring,
                                        location = location,
                                        description = description,
                                        isReadOnly = isReadOnly
                                    )
                                )
                            }
                        } catch (e: HttpException) {
                            if (e.code == 401) throw e
                            android.util.Log.d("Flip2Cal", "Skipping calendar '$calName' (HTTP ${e.code}): ${e.body}")
                        } catch (e: Exception) {
                            android.util.Log.d("Flip2Cal", "Skipping calendar '$calName': ${e.message}")
                        }
                    }
                    eventsList.sortedWith(compareBy { it.dateKey })
                }
                android.util.Log.d("Flip2Cal", "Total events fetched: ${events.size}")
                allEvents.clear()
                allEvents.addAll(events)
                tvStatus.visibility = View.GONE
                renderEvents()

                // Only reschedule reminders once per hour
                val lastScheduled = prefs.getLong("last_scheduled", 0)
                val oneHour = 60 * 60 * 1000L
                if (System.currentTimeMillis() - lastScheduled > oneHour) {
                    prefs.edit().putLong("last_scheduled", System.currentTimeMillis()).apply()
                    scheduleReminders()
                }
            } catch (e: Exception) {
                android.util.Log.d("Flip2Cal", "Fetch error: ${e.message}")
                // Check the real HTTP status code instead of string-matching
                // e.message for "401" - IOException text isn't guaranteed to
                // contain the status code.
                if (e is HttpException && e.code == 401) {
                    refreshToken()
                } else {
                    tvStatus.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun refreshToken() {
        val refreshTokenValue = prefs.getString(KEY_REFRESH_TOKEN, null) ?: run {
            prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
            startOAuth()
            return
        }
        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    val body = "refresh_token=${Uri.encode(refreshTokenValue)}" +
                            "&client_id=${Uri.encode(getClientId(this@MainActivity))}" +
                            "&client_secret=${Uri.encode(getClientSecret(this@MainActivity))}" +
                            "&grant_type=refresh_token"
                    val response = httpPostForm(URL("https://oauth2.googleapis.com/token"), body)
                    JSONObject(response)
                }
                val newAccessToken = token.getString("access_token")
                prefs.edit().putString(KEY_ACCESS_TOKEN, newAccessToken).apply()
                fetchCalendarEvents(newAccessToken)
            } catch (e: Exception) {
                prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
                startOAuth()
            }
        }
    }

    private fun getCalendarList(accessToken: String): List<CalendarInfo> {
        val response = httpGet(
            URL("https://www.googleapis.com/calendar/v3/users/me/calendarList"),
            accessToken
        )
        val json = JSONObject(response)
        val items = json.optJSONArray("items") ?: return emptyList()
        val result = mutableListOf<CalendarInfo>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val id = item.getString("id")
            val name = item.optString("summary", "Unknown")
            val colorHex = item.optString("backgroundColor", "#4285F4")
            val color = try {
                Color.parseColor(colorHex)
            } catch (e: Exception) {
                Color.parseColor("#4285F4")
            }
            // "reader" and "freeBusyReader" are both non-writable access
            // roles - previously only "reader" was treated as read-only, so
            // freeBusyReader calendars would appear editable and fail on save.
            val accessRole = item.optString("accessRole", "")
            val isReadOnly = accessRole == "reader" || accessRole == "freeBusyReader"
            result.add(CalendarInfo(id, name, color, isReadOnly))
        }
        return result
    }

    private fun formatTime(dateTimeStr: String, isAllDay: Boolean): String {
        if (isAllDay) return "All day"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val date = sdf.parse(dateTimeStr) ?: return dateTimeStr
            SimpleDateFormat("h:mm a", Locale.US).format(date)
        } catch (e: Exception) {
            dateTimeStr
        }
    }

    private fun renderEvents() {
        val showHolidays = prefs.getBoolean(OptionsActivity.KEY_SHOW_HOLIDAYS, false)
        val hideFuture = prefs.getBoolean(OptionsActivity.KEY_HIDE_FUTURE, false)
        val daysAhead = prefs.getInt(OptionsActivity.KEY_DAYS_AHEAD, OptionsActivity.DEFAULT_DAYS_AHEAD)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayKey = sdf.format(Date())
        val cutoffDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysAhead) }
        val cutoffKey = sdf.format(cutoffDate.time)
        val filtered = allEvents.filter { event ->
            if (!showHolidays && event.isHoliday) return@filter false
            if (hideFuture && event.dateKey > todayKey) return@filter false
            if (event.dateKey > cutoffKey) return@filter false
            true
        }
        val items = mutableListOf<ListItem>()
        var lastDate = ""
        for (event in filtered) {
            if (event.dateKey != lastDate) {
                items.add(ListItem.DateHeader(formatDateHeader(event.dateKey)))
                lastDate = event.dateKey
            }
            items.add(ListItem.Event(event))
        }
        if (items.isEmpty()) {
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "No events in the next $daysAhead days"
        }
        listView.adapter = object : ArrayAdapter<ListItem>(this, 0, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return when (val item = items[position]) {
                    is ListItem.DateHeader -> {
                        val tv = TextView(context)
                        tv.text = item.label
                        tv.setTextColor(Color.WHITE)
                        tv.textSize = 18f
                        tv.setPadding(16, 20, 16, 8)
                        tv.setBackgroundColor(Color.parseColor("#1a1a1a"))
                        tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
                        tv.isFocusable = false
                        tv
                    }
                    is ListItem.Event -> {
                        val container = android.widget.LinearLayout(context)
                        container.orientation = android.widget.LinearLayout.HORIZONTAL
                        container.setPadding(16, 12, 16, 12)
                        container.isFocusable = false
                        container.isClickable = false
                        val colorBar = View(context)
                        val params = android.widget.LinearLayout.LayoutParams(6, ViewGroup.LayoutParams.MATCH_PARENT)
                        params.marginEnd = 12
                        colorBar.layoutParams = params
                        colorBar.setBackgroundColor(item.event.calendarColor)
                        val textLayout = android.widget.LinearLayout(context)
                        textLayout.orientation = android.widget.LinearLayout.VERTICAL
                        textLayout.isFocusable = false
                        val tvTitle = TextView(context)
                        tvTitle.text = item.event.title
                        tvTitle.setTextColor(Color.WHITE)
                        tvTitle.textSize = 18f
                        tvTitle.isFocusable = false
                        val tvTime = TextView(context)
                        tvTime.text = if (item.event.isAllDay) "All day"
                        else "${item.event.startTime} – ${item.event.endTime}"
                        tvTime.setTextColor(Color.parseColor("#AAAAAA"))
                        tvTime.textSize = 16f
                        tvTime.isFocusable = false
                        textLayout.addView(tvTitle)
                        textLayout.addView(tvTime)
                        container.addView(colorBar)
                        container.addView(textLayout)
                        container
                    }
                }
            }

            override fun getItemViewType(position: Int): Int {
                return when (items[position]) {
                    is ListItem.DateHeader -> 0
                    is ListItem.Event -> 1
                }
            }

            override fun getViewTypeCount() = 2
            override fun isEnabled(position: Int) = items[position] is ListItem.Event
        }

        var selectedPosition = -1
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = items[position]
            if (item is ListItem.Event) {
                showEventDetail(item.event)
            }
        }
        listView.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                // Unhighlight previous
                if (selectedPosition >= 0) {
                    val prev = listView.getChildAt(selectedPosition - listView.firstVisiblePosition)
                    prev?.setBackgroundColor(Color.TRANSPARENT)
                }
                // Highlight current
                view?.setBackgroundColor(Color.parseColor("#2a4a7a"))
                selectedPosition = position
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                if (selectedPosition >= 0) {
                    val prev = listView.getChildAt(selectedPosition - listView.firstVisiblePosition)
                    prev?.setBackgroundColor(Color.TRANSPARENT)
                    selectedPosition = -1
                }
            }
        })

        // Set focus to first event item
        listView.post {
            val firstEventPos = items.indexOfFirst { it is ListItem.Event }
            if (firstEventPos >= 0) {
                listView.setSelection(firstEventPos)
                listView.requestFocus()
            }
        }
    }

    private fun formatDateHeader(dateKey: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(dateKey) ?: return dateKey
            SimpleDateFormat("EEEE, MMMM d", Locale.US).format(date)
        } catch (e: Exception) {
            dateKey
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (listView.selectedItemPosition <= 0) {
                tvNewEvent.requestFocus()
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            val pos = listView.selectedItemPosition
            if (pos >= 0) {
                val adapter = listView.adapter
                val item = adapter.getItem(pos)
                if (item is ListItem.Event) {
                    showEventDetail(item.event)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showEventDetail(event: CalendarEvent) {
        val msg = buildString {
            if (event.isAllDay) {
                appendLine("All day")
            } else {
                appendLine("${event.startTime} – ${event.endTime}")
            }
            if (event.location.isNotEmpty()) {
                appendLine("📍 ${event.location}")
            }
            if (event.description.isNotEmpty()) {
                appendLine()
                appendLine(event.description)
            }
            appendLine(event.calendarName)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(event.title)
            .setMessage(msg)
            .setPositiveButton(if (event.isReadOnly) "Read Only" else "Edit") { d, _ ->
                d.dismiss()
                if (event.isReadOnly) {
                    Toast.makeText(this, "This event is from a read-only calendar and cannot be edited.", Toast.LENGTH_LONG).show()
                } else {
                    EventEditActivity.startForEdit(
                        this,
                        event.eventId,
                        event.calendarId,
                        event.title,
                        event.dateKey,
                        event.startTime,
                        event.endTime,
                        event.isAllDay,
                        event.isRecurring,
                        event.location,
                        event.description
                    )
                }
            }
            .setNegativeButton("Close") { d, _ -> d.dismiss() }
            .show()
    }

    private fun todayDateStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    override fun onResume() {
        super.onResume()
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (accessToken != null && allEvents.isNotEmpty()) {
            fetchCalendarEvents(accessToken)
        }
    }

    private fun scheduleReminders() {
        val intent = Intent(this, ReminderSchedulerService::class.java)
        startForegroundService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}