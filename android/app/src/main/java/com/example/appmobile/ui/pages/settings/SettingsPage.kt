package com.example.appmobile.ui.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.ui.components.EmoGardenBackground

@Composable
fun SettingsPage(
    assistantBubbleEnabled: Boolean,
    onAssistantBubbleChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmoGardenBackground)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Quay lại", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(
            text = "Cài đặt",
            color = Color(0xFF0B3C7D),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Tuỳ chỉnh trải nghiệm học và chơi cho bé.",
            color = Color(0xFF52616F),
            lineHeight = 20.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(modifier = Modifier.height(48.dp), shape = CircleShape, color = Color(0xFFE3F2FD)) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("💬", fontSize = 26.sp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bong bóng trợ lý", color = Color(0xFF0B3C7D), fontWeight = FontWeight.ExtraBold)
                        Text("Bật để hiện nút chat nổi ở góc màn hình.", color = Color(0xFF6B7280), fontSize = 13.sp)
                    }
                    Switch(
                        checked = assistantBubbleEnabled,
                        onCheckedChange = onAssistantBubbleChanged
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tài khoản", color = Color(0xFF0B3C7D), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("Đăng xuất")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Tiếp tục dùng app")
                }
            }
        }
    }
}
