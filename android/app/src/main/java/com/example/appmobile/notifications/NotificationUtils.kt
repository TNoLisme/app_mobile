package com.example.appmobile.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationUtils {

    // --- Kênh nhắc học ---
    const val CHANNEL_ID = "learning_reminder_channel"
    private const val CHANNEL_NAME = "Nhắc học"
    private const val CHANNEL_DESC = "Thông báo nhắc bé học mỗi ngày"
    const val NOTIFICATION_ID = 1001

    // --- Kênh hoạt động ứng dụng (đăng nhập, cập nhật, lưu ảnh, ...) ---
    const val ACTIVITY_CHANNEL_ID = "app_activity_channel"
    private const val ACTIVITY_CHANNEL_NAME = "Hoạt động ứng dụng"
    private const val ACTIVITY_CHANNEL_DESC = "Thông báo kết quả các thao tác trong ứng dụng"
    private var activityNotifId = 2000

    /** Tạo kênh nhắc học (gọi khi schedule alarm). */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = CHANNEL_DESC
        manager.createNotificationChannel(channel)
    }

    /** Tạo kênh hoạt động ứng dụng. */
    fun createActivityChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ACTIVITY_CHANNEL_ID,
            ACTIVITY_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = ACTIVITY_CHANNEL_DESC
        manager.createNotificationChannel(channel)
    }

    /**
     * Gửi thông báo đẩy dạng "Hoạt động ứng dụng".
     * - Đẩy lên thanh status bar (system notification).
     * - Đồng thời emit vào [InAppNotificationManager] để hiển thị popup 3 giây ngay trong app.
     */
    fun showAppNotification(context: Context, title: String, message: String) {
        createActivityChannel(context)
        val notifId = activityNotifId++
        val builder = NotificationCompat.Builder(context, ACTIVITY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(notifId, builder.build())
        }

        // Hiển thị thêm popup ngay trong app (nếu đang mở app)
        InAppNotificationManager.emit(title, message)
    }


    /** Gửi thông báo nhắc học. */
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
