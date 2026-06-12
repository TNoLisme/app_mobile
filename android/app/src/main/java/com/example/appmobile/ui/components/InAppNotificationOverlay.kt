package com.example.appmobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.notifications.InAppNotification
import com.example.appmobile.notifications.InAppNotificationManager
import kotlinx.coroutines.delay

/**
 * Overlay toàn màn hình để hiển thị popup thông báo in-app.
 * Đặt composable này ở mức cao nhất trong UI hierarchy (trong AppRoot).
 * Popup xuất hiện từ trên xuống, tự biến mất sau 3 giây.
 */
@Composable
fun InAppNotificationOverlay() {
    var current by remember { mutableStateOf<InAppNotification?>(null) }
    var visible by remember { mutableStateOf(false) }

    // Lắng nghe flow thông báo
    LaunchedEffect(Unit) {
        InAppNotificationManager.flow.collect { notif ->
            // Nếu đang hiển thị một cái khác thì ẩn trước
            if (visible) {
                visible = false
                delay(300) // chờ animation ẩn
            }
            current = notif
            visible = true
            delay(3000) // hiển thị 3 giây
            visible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            current?.let { notif ->
                InAppNotificationBanner(title = notif.title, message = notif.message)
            }
        }
    }
}

@Composable
private fun InAppNotificationBanner(title: String, message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B)) // Dark navy
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon app nhỏ
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF3B82F6)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🌱",
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp
            )
            Text(
                text = message,
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
