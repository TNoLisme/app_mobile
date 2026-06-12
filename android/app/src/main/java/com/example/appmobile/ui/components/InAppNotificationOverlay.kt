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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.notifications.InAppNotification
import com.example.appmobile.notifications.InAppNotificationManager
import kotlinx.coroutines.delay

@Composable
fun InAppNotificationOverlay() {
    var current by remember { mutableStateOf<InAppNotification?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        InAppNotificationManager.flow.collect { notification ->
            if (visible) {
                visible = false
                delay(180)
            }
            current = notification
            visible = true
            delay(2600)
            visible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, bottom = 86.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            current?.let { notification ->
                InAppNotificationToast(
                    title = notification.title,
                    message = notification.message
                )
            }
        }
    }
}

@Composable
private fun InAppNotificationToast(title: String, message: String) {
    Row(
        modifier = Modifier
            .widthIn(max = 326.dp)
            .heightIn(min = 54.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color(0x551E293B),
                spotColor = Color(0x661E293B)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xEE1B2A3F))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2F80ED)),
            contentAlignment = Alignment.Center
        ) {
            EgVectorEmojiIcon(
                value = iconForNotification(title),
                size = 20.dp,
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = message,
                color = Color(0xFFD7E2F0),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun iconForNotification(title: String): String {
    val lower = title.lowercase()
    return when {
        "khóa" in lower || "khoa" in lower || "locked" in lower || "lock" in lower -> "lock"
        "nhắc" in lower || "reminder" in lower -> "bell"
        "hồ sơ" in lower || "profile" in lower -> "user"
        "báo cáo" in lower || "report" in lower -> "report"
        "đăng nhập" in lower || "login" in lower -> "check"
        else -> "check"
    }
}
