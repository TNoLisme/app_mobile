package com.example.appmobile.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("learning_reminder_enabled", false)
        if (!enabled) return

        NotificationUtils.showLearningReminder(context)
        val hour = prefs.getInt("learning_reminder_hour", ReminderScheduler.DEFAULT_HOUR)
        val minute = prefs.getInt("learning_reminder_minute", ReminderScheduler.DEFAULT_MINUTE)
        ReminderScheduler.scheduleDailyReminder(context, hour, minute)
    }
}
