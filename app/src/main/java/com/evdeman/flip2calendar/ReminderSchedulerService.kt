package com.evdeman.flip2calendar

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class ReminderSchedulerService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Required foreground notification for background service
        ReminderReceiver.createNotificationChannel(this)
        val notification = androidx.core.app.NotificationCompat.Builder(this, ReminderReceiver.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Flip2Calendar")
            .setContentText("Scheduling reminders...")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        startForeground(9999, notification)

        scope.launch {
            try {
                val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                val accessToken = prefs.getString(MainActivity.KEY_ACCESS_TOKEN, null)
                if (accessToken != null) {
                    scheduleReminders(accessToken)
                }
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun scheduleReminders(accessToken: String) {
        try {
            val now = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 60) }
            val timeMin = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(now.time)
            val timeMax = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(end.time)

            // Get calendar list
            val calUrl = URL("https://www.googleapis.com/calendar/v3/users/me/calendarList")
            val calConn = calUrl.openConnection() as HttpURLConnection
            calConn.setRequestProperty("Authorization", "Bearer $accessToken")
            val calResponse = calConn.inputStream.bufferedReader().readText()
            val calJson = JSONObject(calResponse)
            val calItems = calJson.optJSONArray("items") ?: return

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            var notificationId = 1000

            for (i in 0 until calItems.length()) {
                val cal = calItems.getJSONObject(i)
                val calId = cal.getString("id")
                val calName = cal.optString("summary", "")

                val eventsUrl = URL(
                    "https://www.googleapis.com/calendar/v3/calendars/" +
                            "${java.net.URLEncoder.encode(calId, "UTF-8")}/events" +
                            "?timeMin=$timeMin&timeMax=$timeMax" +
                            "&singleEvents=true&orderBy=startTime&maxResults=300"
                )
                val evConn = eventsUrl.openConnection() as HttpURLConnection
                evConn.setRequestProperty("Authorization", "Bearer $accessToken")

                if (evConn.responseCode != 200) continue

                val evResponse = evConn.inputStream.bufferedReader().readText()
                val evJson = JSONObject(evResponse)
                val events = evJson.optJSONArray("items") ?: continue

                for (j in 0 until events.length()) {
                    val event = events.getJSONObject(j)
                    val summary = event.optString("summary", "(No title)")
                    val start = event.optJSONObject("start") ?: continue
                    val isAllDay = start.has("date") && !start.has("dateTime")
                    if (isAllDay) continue

                    val startStr = start.optString("dateTime") ?: continue
                    val startTime = try {
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(startStr)
                    } catch (e: Exception) { continue } ?: continue

                    // Get reminders for this event
                    val remindersObj = event.optJSONObject("reminders")
                    val useDefault = remindersObj?.optBoolean("useDefault", true) ?: true
                    val overrides = remindersObj?.optJSONArray("overrides")

                    val reminderMinutes = mutableListOf<Int>()
                    if (!useDefault && overrides != null) {
                        for (k in 0 until overrides.length()) {
                            val r = overrides.getJSONObject(k)
                            if (r.optString("method") == "popup") {
                                reminderMinutes.add(r.getInt("minutes"))
                            }
                        }
                    } else if (useDefault) {
                        reminderMinutes.add(10) // Google's default is 10 minutes
                    }

                    for (minutes in reminderMinutes) {
                        val alarmTime = startTime.time - (minutes * 60 * 1000L)
                        if (alarmTime <= System.currentTimeMillis()) continue

                        val message = when {
                            minutes < 60 -> "In $minutes minutes"
                            minutes == 60 -> "In 1 hour"
                            minutes < 1440 -> "In ${minutes / 60} hours"
                            else -> "Tomorrow"
                        }

                        val alarmIntent = Intent(this, ReminderReceiver::class.java).apply {
                            putExtra(ReminderReceiver.EXTRA_TITLE, summary)
                            putExtra(ReminderReceiver.EXTRA_MESSAGE, "$message • $calName")
                            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                        }

                        val pendingIntent = PendingIntent.getBroadcast(
                            this, notificationId, alarmIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        try {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                alarmTime,
                                pendingIntent
                            )
                        } catch (e: SecurityException) {
                            // Exact alarms not permitted — fall back to inexact
                            alarmManager.set(
                                AlarmManager.RTC_WAKEUP,
                                alarmTime,
                                pendingIntent
                            )
                        }

                        notificationId++
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail — reminders are best-effort
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}