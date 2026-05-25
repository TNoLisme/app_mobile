package com.example.appmobile.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("learning_reminder_enabled", false)
            val hour = prefs.getInt("learning_reminder_hour", ReminderScheduler.DEFAULT_HOUR)
            val minute = prefs.getInt("learning_reminder_minute", ReminderScheduler.DEFAULT_MINUTE)
            if (enabled) {
                ReminderScheduler.scheduleDailyReminder(context, hour, minute)
            }
        }
    }
}
