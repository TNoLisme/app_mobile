package com.example.appmobile.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Dữ liệu một thông báo in-app popup. */
data class InAppNotification(
    val title: String,
    val message: String
)

/**
 * Singleton quản lý thông báo popup trong app.
 * Mỗi lần gọi [emit] sẽ hiển thị một popup banner 3 giây ngay trong app.
 */
object InAppNotificationManager {
    private val _flow = MutableSharedFlow<InAppNotification>(extraBufferCapacity = 8)
    val flow = _flow.asSharedFlow()

    fun emit(title: String, message: String) {
        _flow.tryEmit(InAppNotification(title = title, message = message))
    }
}
