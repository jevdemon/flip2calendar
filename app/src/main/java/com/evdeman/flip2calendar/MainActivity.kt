package com.evdeman.flip2calendar
import com.evdeman.flip2calendar.BuildConfig
import android.accounts.Account
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
        const val CLIENT_ID = BuildConfig.GOOGLE_CLIENT_ID
        const val CLIENT_SECRET = BuildConfig.GOOGLE_CLIENT_SECRET
        const val REDIRECT_URI = "http://localhost"
        const val SCOPE = "https://www.googleapis.com/auth/calendar"
        const val PREFS_NAME = "flip2cal_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_SHOW_HOLIDAYS = "show_holidays"
    }

    private lateinit var listView: ListView
    private lateinit var tvStatus: TextView
    private lateinit var tvHolidayToggle: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var tvNewEvent: TextView
    private var showHolidays = false
    private var allEvents = listOf<CalendarEvent>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        listView = findViewById(R.id.listView)
        listView.itemsCanFocus = false
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        tvStatus = findViewById(R.id.tvStatus)
        tvHolidayToggle = findViewById(R.id.tvHolidayToggle)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        showHolidays = prefs.getBoolean(KEY_SHOW_HOLIDAYS, false)
        updateHolidayToggleText()
        updateTitle()

        tvHolidayToggle.setOnClickListener {
            showHolidays = !showHolidays
            prefs.edit().putBoolean(KEY_SHOW_HOLIDAYS, showHolidays).apply()
            updateHolidayToggleText()
            renderEvents()
        }

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

    private fun updateHolidayToggleText() {
        tvHolidayToggle.text = if (showHolidays) "Holidays: ON" else "Holidays: OFF"
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
            .appendQueryParameter("client_id", CLIENT_ID)
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
                    val url = URL("https://oauth2.googleapis.com/token")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    val body = "code=$code" +
                            "&client_id=$CLIENT_ID" +
                            "&client_secret=$CLIENT_SECRET" +
                            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
                            "&grant_type=authorization_code"
                    conn.outputStream.write(body.toByteArray())
                    val response = conn.inputStream.bufferedReader().readText()
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
                    val allEvents = mutableListOf<CalendarEvent>()
                    val now = Calendar.getInstance()
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

                        val eventsUrl = URL(
                            "https://www.googleapis.com/calendar/v3/calendars/" +
                                    "${Uri.encode(calId)}/events" +
                                    "?timeMin=$timeMin&timeMax=$timeMax" +
                                    "&singleEvents=true&orderBy=startTime&maxResults=300"
                        )
                        val conn = eventsUrl.openConnection() as HttpURLConnection
                        conn.setRequestProperty("Authorization", "Bearer $accessToken")
                        val response = conn.inputStream.bufferedReader().readText()
                        val json = JSONObject(response)
                        val items = json.optJSONArray("items") ?: continue

                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            val summary = item.optString("summary", "(No title)")
                            val start = item.optJSONObject("start")
                            val end2 = item.optJSONObject("end")
                            val isAllDay = start?.has("date") == true && start.has("date")
                            val startStr = start?.optString("dateTime")
                                ?: start?.optString("date") ?: ""
                            val endStr = end2?.optString("dateTime")
                                ?: end2?.optString("date") ?: ""
                            val dateKey = if (isAllDay) startStr
                            else startStr.substring(0, 10)
                            val startFormatted = formatTime(startStr, isAllDay)
                            val endFormatted = formatTime(endStr, isAllDay)
                            val eventId = item.optString("id", "")
                            val isRecurring = item.has("recurringEventId") || item.has("recurrence")
                            val location = item.optString("location", "")
                            val description = item.optString("description", "")

                            allEvents.add(
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
                    }
                    allEvents.sortedWith(compareBy { it.dateKey })
                }
                android.util.Log.d("Flip2Cal", "Total events fetched: ${allEvents.size}")
                allEvents = events
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
                if (e.message?.contains("401") == true) {
                    refreshToken()
                } else {
                    tvStatus.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun refreshToken() {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: run {
            prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
            startOAuth()
            return
        }
        scope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    val url = URL("https://oauth2.googleapis.com/token")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    val body = "refresh_token=$refreshToken" +
                            "&client_id=$CLIENT_ID" +
                            "&client_secret=$CLIENT_SECRET" +
                            "&grant_type=refresh_token"
                    conn.outputStream.write(body.toByteArray())
                    val response = conn.inputStream.bufferedReader().readText()
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
    val url = URL("https://www.googleapis.com/calendar/v3/users/me/calendarList")
    val conn = url.openConnection() as HttpURLConnection
    conn.setRequestProperty("Authorization", "Bearer $accessToken")
    val responseCode = conn.responseCode
    if (responseCode == 401) {
        throw Exception("401 Unauthorized")
    }
    if (responseCode !in 200..299) {
        throw Exception("https://www.googleapis.com/calendar/v3/users/me/calendarList")
    }
    val response = conn.inputStream.bufferedReader().readText()
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
        val isReadOnly = item.optString("accessRole", "") == "reader"
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
        val filtered = if (showHolidays) allEvents
        else allEvents.filter { !it.isHoliday }

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
            tvStatus.text = "No events in the next 30 days"
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