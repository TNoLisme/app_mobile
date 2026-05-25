package com.example.appmobile.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationUtils.showLearningReminder(context)
        // Schedule next day's reminder again (AlarmManager exact alarm is one-shot)
        ReminderScheduler.scheduleDailyReminder(context)
    }
}
