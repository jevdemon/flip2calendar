package com.evdeman.flip2calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class EventEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_CALENDAR_ID = "calendar_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DATE = "date"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_END_TIME = "end_time"
        const val EXTRA_IS_ALL_DAY = "is_all_day"
        const val EXTRA_IS_NEW = "is_new"
        const val EXTRA_IS_RECURRING = "is_recurring"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_DESCRIPTION = "description"

        val REMINDER_OPTIONS = listOf(
            Pair("15 minutes before", 15),
            Pair("30 minutes before", 30),
            Pair("1 hour before", 60),
            Pair("2 hours before", 120),
            Pair("1 day before", 1440),
            Pair("2 days before", 2880)
        )

        val RECURRENCE_OPTIONS = listOf(
            Pair("Does not repeat", ""),
            Pair("Weekly", "RRULE:FREQ=WEEKLY"),
            Pair("Monthly", "RRULE:FREQ=MONTHLY"),
            Pair("Annually", "RRULE:FREQ=YEARLY")
        )

        fun startForNew(context: Context, date: String) {
            val intent = Intent(context, EventEditActivity::class.java)
            intent.putExtra(EXTRA_IS_NEW, true)
            intent.putExtra(EXTRA_DATE, date)
            context.startActivity(intent)
        }

        fun startForEdit(context: Context, eventId: String, calendarId: String,
                         title: String, date: String, startTime: String,
                         endTime: String, isAllDay: Boolean, isRecurring: Boolean,
                         location: String = "", description: String = "") {
            val intent = Intent(context, EventEditActivity::class.java)
            intent.putExtra(EXTRA_IS_NEW, false)
            intent.putExtra(EXTRA_EVENT_ID, eventId)
            intent.putExtra(EXTRA_CALENDAR_ID, calendarId)
            intent.putExtra(EXTRA_TITLE, title)
            intent.putExtra(EXTRA_DATE, date)
            intent.putExtra(EXTRA_START_TIME, startTime)
            intent.putExtra(EXTRA_END_TIME, endTime)
            intent.putExtra(EXTRA_IS_ALL_DAY, isAllDay)
            intent.putExtra(EXTRA_IS_RECURRING, isRecurring)
            intent.putExtra(EXTRA_LOCATION, location)
            intent.putExtra(EXTRA_DESCRIPTION, description)
            context.startActivity(intent)
        }
    }

    private lateinit var etTitle: EditText
    private lateinit var btnDate: Button
    private lateinit var switchAllDay: Switch
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var spinnerCalendar: Spinner
    private lateinit var spinnerRecurrence: Spinner
    private lateinit var labelStartTime: TextView
    private lateinit var labelEndTime: TextView
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var btnCancel: Button
    private lateinit var cbReminder1: CheckBox
    private lateinit var cbReminder2: CheckBox
    private lateinit var cbReminder3: CheckBox
    private lateinit var spinnerReminder1: Spinner
    private lateinit var spinnerReminder2: Spinner
    private lateinit var spinnerReminder3: Spinner
    private lateinit var applyToAllRow: View
    private lateinit var cbApplyToAll: CheckBox
    private lateinit var recurringBanner: View
    private lateinit var etLocation: EditText
    private lateinit var etDescription: EditText

    private var selectedDate = Calendar.getInstance()
    private var startHour = run {
        val cal = Calendar.getInstance()
        // Round up to next hour
        if (cal.get(Calendar.MINUTE) > 0) cal.get(Calendar.HOUR_OF_DAY) + 1
        else cal.get(Calendar.HOUR_OF_DAY)
    }
    private var startMinute = 0
    private var endHour = startHour + 1
    private var endMinute = 0
    private var isAllDay = false
    private var isNew = true
    private var isRecurring = false
    private var eventId = ""
    private var calendarId = ""
    private var calendars = listOf<Triple<String, String, Int>>()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_edit)

        etTitle = findViewById(R.id.etTitle)
        btnDate = findViewById(R.id.btnDate)
        switchAllDay = findViewById(R.id.switchAllDay)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        spinnerCalendar = findViewById(R.id.spinnerCalendar)
        spinnerRecurrence = findViewById(R.id.spinnerRecurrence)
        labelStartTime = findViewById(R.id.labelStartTime)
        labelEndTime = findViewById(R.id.labelEndTime)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
        btnCancel = findViewById(R.id.btnCancel)
        cbReminder1 = findViewById(R.id.cbReminder1)
        cbReminder2 = findViewById(R.id.cbReminder2)
        cbReminder3 = findViewById(R.id.cbReminder3)
        spinnerReminder1 = findViewById(R.id.spinnerReminder1)
        spinnerReminder2 = findViewById(R.id.spinnerReminder2)
        spinnerReminder3 = findViewById(R.id.spinnerReminder3)
        applyToAllRow = findViewById(R.id.applyToAllRow)
        cbApplyToAll = findViewById(R.id.cbApplyToAll)
        recurringBanner = findViewById(R.id.recurringBanner)
        etLocation = findViewById(R.id.etLocation)
        etDescription = findViewById(R.id.etDescription)

        isNew = intent.getBooleanExtra(EXTRA_IS_NEW, true)
        isRecurring = intent.getBooleanExtra(EXTRA_IS_RECURRING, false)
        title = if (isNew) "New Event" else "Edit Event"

        // Setup reminder spinners
        val reminderLabels = REMINDER_OPTIONS.map { it.first }
        val reminderAdapter = { ->
            ArrayAdapter(this, R.layout.spinner_item, reminderLabels).also {
                it.setDropDownViewResource(R.layout.spinner_item)
            }
        }
        spinnerReminder1.adapter = reminderAdapter()
        spinnerReminder2.adapter = reminderAdapter()
        spinnerReminder3.adapter = reminderAdapter()

        // Default: 15 min, 1 hour, 1 day
        spinnerReminder1.setSelection(0) // 15 min
        spinnerReminder2.setSelection(2) // 1 hour
        spinnerReminder3.setSelection(4) // 1 day

        // Setup recurrence spinner
        val recurrenceAdapter = ArrayAdapter(this,
            R.layout.spinner_item,
            RECURRENCE_OPTIONS.map { it.first })
        recurrenceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRecurrence.adapter = recurrenceAdapter

        if (isNew) {
            cbReminder1.isChecked = true
            cbReminder2.isChecked = true
            cbReminder3.isChecked = false
        }

        // Show delete and applyToAll for edits
        if (!isNew) {
            btnDelete.visibility = View.VISIBLE
            etTitle.setText(intent.getStringExtra(EXTRA_TITLE) ?: "")
            eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
            calendarId = intent.getStringExtra(EXTRA_CALENDAR_ID) ?: ""
            isAllDay = intent.getBooleanExtra(EXTRA_IS_ALL_DAY, false)
            switchAllDay.isChecked = isAllDay
            etLocation.setText(intent.getStringExtra(EXTRA_LOCATION) ?: "")
            etDescription.setText(intent.getStringExtra(EXTRA_DESCRIPTION) ?: "")

            if (isRecurring) {
                findViewById<View>(R.id.recurringBanner).visibility = View.VISIBLE
            }
            val startTimeStr = intent.getStringExtra(EXTRA_START_TIME) ?: "9:00 AM"
            val endTimeStr = intent.getStringExtra(EXTRA_END_TIME) ?: "10:00 AM"
            parseTimeInto(startTimeStr, true)
            parseTimeInto(endTimeStr, false)
            loadEventReminders(eventId, calendarId)
            // Hide recurrence option when editing - it only applies to new events
            findViewById<TextView>(R.id.labelRepeat).visibility = View.GONE
            spinnerRecurrence.visibility = View.GONE
        }

        // Parse date
        val dateStr = intent.getStringExtra(EXTRA_DATE) ?: ""
        if (dateStr.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = sdf.parse(dateStr)
                if (date != null) selectedDate.time = date
            } catch (e: Exception) { }
        }
        updateDateButton()
        updateTimeButtons()
        updateTimeVisibility()

        btnDate.setOnClickListener {
            DatePickerDialog(this,
                { _, year, month, day ->
                    selectedDate.set(year, month, day)
                    updateDateButton()
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        switchAllDay.setOnCheckedChangeListener { _, checked ->
            isAllDay = checked
            updateTimeVisibility()
        }

        btnStartTime.setOnClickListener {
            TimePickerDialog(this,
                { _, hour, minute ->
                    startHour = hour
                    startMinute = minute
                    if (endHour < startHour || (endHour == startHour && endMinute <= startMinute)) {
                        endHour = startHour + 1
                        endMinute = startMinute
                    }
                    updateTimeButtons()
                },
                startHour, startMinute, false
            ).show()
        }

        btnEndTime.setOnClickListener {
            TimePickerDialog(this,
                { _, hour, minute ->
                    endHour = hour
                    endMinute = minute
                    updateTimeButtons()
                },
                endHour, endMinute, false
            ).show()
        }

        btnSave.setOnClickListener { saveEvent() }
        btnCancel.setOnClickListener { finish() }
        btnDelete.setOnClickListener { confirmDelete() }

        loadCalendars()
    }

    private fun parseTimeInto(timeStr: String, isStart: Boolean) {
        try {
            val sdf = SimpleDateFormat("h:mm a", Locale.US)
            val date = sdf.parse(timeStr) ?: return
            val cal = Calendar.getInstance()
            cal.time = date
            if (isStart) {
                startHour = cal.get(Calendar.HOUR_OF_DAY)
                startMinute = cal.get(Calendar.MINUTE)
            } else {
                endHour = cal.get(Calendar.HOUR_OF_DAY)
                endMinute = cal.get(Calendar.MINUTE)
            }
        } catch (e: Exception) { }
    }

    private fun updateDateButton() {
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        btnDate.text = sdf.format(selectedDate.time)
    }

    private fun updateTimeButtons() {
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
        }
        btnStartTime.text = sdf.format(startCal.time)
        btnEndTime.text = sdf.format(endCal.time)
    }

    private fun updateTimeVisibility() {
        val visibility = if (isAllDay) View.GONE else View.VISIBLE
        btnStartTime.visibility = visibility
        btnEndTime.visibility = visibility
        labelStartTime.visibility = visibility
        labelEndTime.visibility = visibility
    }

    private fun loadCalendars() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = prefs.getString(MainActivity.KEY_ACCESS_TOKEN, null) ?: return
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL("https://www.googleapis.com/calendar/v3/users/me/calendarList")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("Authorization", "Bearer $accessToken")
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val items = json.optJSONArray("items") ?: return@withContext emptyList()
                    val list = mutableListOf<Triple<String, String, Int>>()
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val accessRole = item.optString("accessRole", "")
                        if (accessRole == "reader") continue
                        val id = item.getString("id")
                        val name = item.optString("summary", "Unknown")
                        list.add(Triple(id, name, 0))
                    }
                    list
                }
                calendars = result
                val names = result.map { it.second }
                val adapter = ArrayAdapter(this@EventEditActivity,
                    R.layout.spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCalendar.adapter = adapter

                if (!isNew && calendarId.isNotEmpty()) {
                    val idx = calendars.indexOfFirst { it.first == calendarId }
                    if (idx >= 0) spinnerCalendar.setSelection(idx)
                }
            } catch (e: Exception) {
                Toast.makeText(this@EventEditActivity,
                    "Failed to load calendars: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildReminders(): JSONArray {
        val overrides = JSONArray()
        listOf(
            Pair(cbReminder1, spinnerReminder1),
            Pair(cbReminder2, spinnerReminder2),
            Pair(cbReminder3, spinnerReminder3)
        ).forEach { (cb, spinner) ->
            if (cb.isChecked) {
                val minutes = REMINDER_OPTIONS[spinner.selectedItemPosition].second
                overrides.put(JSONObject()
                    .put("method", "popup")
                    .put("minutes", minutes))
            }
        }
        return overrides
    }

    private fun saveEvent() {
        val titleText = etTitle.text.toString().trim()
        if (titleText.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
            etTitle.requestFocus()
            return
        }

        if (calendars.isEmpty()) {
            Toast.makeText(this, "No calendar selected", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedCalendar = calendars[spinnerCalendar.selectedItemPosition]
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = prefs.getString(MainActivity.KEY_ACCESS_TOKEN, null) ?: return
        val applyToAll = !isNew && isRecurring && cbApplyToAll.isChecked

        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val dateStr = dateSdf.format(selectedDate.time)
					
                    val eventJson = JSONObject()
                    eventJson.put("summary", titleText)
                    val locationText = etLocation.text.toString().trim()
                    if (locationText.isNotEmpty()) {
                        eventJson.put("location", locationText)
                    }
                    val descriptionText = etDescription.text.toString().trim()
                    if (descriptionText.isNotEmpty()) {
                        eventJson.put("description", descriptionText)
                    }

                    // Reminders
                    val reminders = JSONObject()
                    reminders.put("useDefault", false)
                    reminders.put("overrides", buildReminders())
                    eventJson.put("reminders", reminders)

                    // Recurrence
                    val recurrenceIdx = spinnerRecurrence.selectedItemPosition
                    val rrule = RECURRENCE_OPTIONS[recurrenceIdx].second
                    if (rrule.isNotEmpty() && isNew) {
                        eventJson.put("recurrence", JSONArray().put(rrule))
                    }

                    if (isAllDay) {
                        eventJson.put("start", JSONObject().put("date", dateStr))
                        eventJson.put("end", JSONObject().put("date", dateStr))
                    } else {
                        val tz = TimeZone.getDefault().id
                        val dtSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                        val startCal = selectedDate.clone() as Calendar
                        startCal.set(Calendar.HOUR_OF_DAY, startHour)
                        startCal.set(Calendar.MINUTE, startMinute)
                        startCal.set(Calendar.SECOND, 0)
                        val endCal = selectedDate.clone() as Calendar
                        endCal.set(Calendar.HOUR_OF_DAY, endHour)
                        endCal.set(Calendar.MINUTE, endMinute)
                        endCal.set(Calendar.SECOND, 0)
                        eventJson.put("start", JSONObject()
                            .put("dateTime", dtSdf.format(startCal.time))
                            .put("timeZone", tz))
                        eventJson.put("end", JSONObject()
                            .put("dateTime", dtSdf.format(endCal.time))
                            .put("timeZone", tz))
                    }

                    val calId = selectedCalendar.first
                    val encodedCalId = java.net.URLEncoder.encode(calId, "UTF-8")

                    val masterEventId = if (applyToAll) {
                        // Find underscore followed by date pattern like _20260701T190000Z
                        // This uses a regular expression to find _ followed by exactly 8 digits,
                        // T, 6 digits, and Z at the end of the string — which is the Google
                        // Calendar date suffix format. That way the leading underscore in the
                        // event ID is ignored.
                        val datePattern = Regex("_\\d{8}T\\d{6}Z$")
                        val match = datePattern.find(eventId)
                        if (match != null) eventId.substring(0, match.range.first) else eventId
                    } else eventId

                    val urlStr = when {
                        isNew -> "https://www.googleapis.com/calendar/v3/calendars/$encodedCalId/events"
                        else -> "https://www.googleapis.com/calendar/v3/calendars/$encodedCalId/events/$masterEventId"
                    }

                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = if (isNew) "POST" else "PUT"
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer $accessToken")
                        setRequestProperty("Content-Type", "application/json")
                    }

                    conn.outputStream.write(eventJson.toString().toByteArray())
                    val responseCode = conn.responseCode
                    if (responseCode !in 200..299) {
                        val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                        throw Exception("HTTP $responseCode: $error")
                    }
                }
                Toast.makeText(this@EventEditActivity,
                    if (isNew) "Event created!" else "Event updated!",
                    Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                // Always reschedule after any save
                startForegroundService(Intent(this@EventEditActivity, ReminderSchedulerService::class.java))
                finish()
            } catch (e: Exception) {
                btnSave.isEnabled = true
                btnSave.text = "Save"
                Toast.makeText(this@EventEditActivity,
                    "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete() {
        val msg = if (isRecurring)
            "Delete this occurrence only, or all occurrences?"
        else
            "Are you sure you want to delete this event?"

        val builder = android.app.AlertDialog.Builder(this)
            .setTitle("Delete Event")
            .setMessage(msg)

        if (isRecurring) {
            builder.setPositiveButton("This Only") { d, _ ->
                d.dismiss()
                deleteEvent(allOccurrences = false)
            }
            builder.setNeutralButton("All") { d, _ ->
                d.dismiss()
                deleteEvent(allOccurrences = true)
            }
            builder.setNegativeButton("Cancel") { d, _ -> d.dismiss() }
        } else {
            builder.setPositiveButton("Delete") { d, _ ->
                d.dismiss()
                deleteEvent(allOccurrences = false)
            }
            builder.setNegativeButton("Cancel") { d, _ -> d.dismiss() }
        }
        builder.show()
    }

    private fun deleteEvent(allOccurrences: Boolean) {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = prefs.getString(MainActivity.KEY_ACCESS_TOKEN, null) ?: return
        val calId = if (calendarId.isNotEmpty()) calendarId
        else if (calendars.isNotEmpty()) calendars[spinnerCalendar.selectedItemPosition].first
        else return

        btnDelete.isEnabled = false
        btnDelete.text = "Deleting..."

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val encodedCalId = java.net.URLEncoder.encode(calId, "UTF-8")
                    val urlStr = if (allOccurrences) {
                        // Delete the master recurring event
                        val baseEventId = eventId.substringBefore("_")
                        "https://www.googleapis.com/calendar/v3/calendars/$encodedCalId/events/$baseEventId"
                    } else {
                        "https://www.googleapis.com/calendar/v3/calendars/$encodedCalId/events/$eventId"
                    }
                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = "DELETE"
                        setRequestProperty("Authorization", "Bearer $accessToken")
                    }
                    val responseCode = conn.responseCode
                    if (responseCode !in 200..299 && responseCode != 204) {
                        val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                        throw Exception("HTTP $responseCode: $error")
                    }
                }
                Toast.makeText(this@EventEditActivity,
                    "Event deleted", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                startForegroundService(Intent(this@EventEditActivity, ReminderSchedulerService::class.java))
                finish()
            } catch (e: Exception) {
                btnDelete.isEnabled = true
                btnDelete.text = "Delete"
                Toast.makeText(this@EventEditActivity,
                    "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadEventReminders(eventId: String, calendarId: String) {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = prefs.getString(MainActivity.KEY_ACCESS_TOKEN, null) ?: return
        scope.launch {
            try {
                val reminders = withContext(Dispatchers.IO) {
                    val encodedCalId = java.net.URLEncoder.encode(calendarId, "UTF-8")
                    val url = URL("https://www.googleapis.com/calendar/v3/calendars/$encodedCalId/events/$eventId")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("Authorization", "Bearer $accessToken")
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val remindersObj = json.optJSONObject("reminders")
                    val overrides = remindersObj?.optJSONArray("overrides")
                    val minutes = mutableListOf<Int>()
                    if (overrides != null) {
                        for (i in 0 until overrides.length()) {
                            minutes.add(overrides.getJSONObject(i).getInt("minutes"))
                        }
                    }
                    minutes
                }

                // Uncheck all first
                cbReminder1.isChecked = false
                cbReminder2.isChecked = false
                cbReminder3.isChecked = false

                // Match reminders to spinners
                val checkboxes = listOf(
                    Pair(cbReminder1, spinnerReminder1),
                    Pair(cbReminder2, spinnerReminder2),
                    Pair(cbReminder3, spinnerReminder3)
                )
                reminders.take(3).forEachIndexed { index, minutes ->
                    val cb = checkboxes[index].first
                    val spinner = checkboxes[index].second
                    cb.isChecked = true
                    val optionIdx = REMINDER_OPTIONS.indexOfFirst { it.second == minutes }
                    if (optionIdx >= 0) spinner.setSelection(optionIdx)
                }
            } catch (e: Exception) {
                // Leave defaults if fetch fails
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}