package com.evdeman.flip2calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule all alarms after reboot by starting MainActivity invisibly
            val serviceIntent = Intent(context, ReminderSchedulerService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}