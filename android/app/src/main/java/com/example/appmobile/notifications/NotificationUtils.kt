package com.example.appmobile.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationUtils {
    const val CHANNEL_ID = "learning_reminder_channel"
    private const val CHANNEL_NAME = "Nhắc học"
    private const val CHANNEL_DESC = "Thông báo nhắc bé học mỗi ngày"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = CHANNEL_DESC
        manager.createNotificationChannel(channel)
    }

    fun showLearningReminder(context: Context) {
        createChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🌱 Mầm Mầm đang đợi bé nè!")
            .setContentText("Vào chơi game và luyện biểu cảm cùng Mầm Mầm thôi nào! 🚀")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }
}
