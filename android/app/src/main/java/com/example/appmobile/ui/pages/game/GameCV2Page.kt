package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appmobile.R
import com.example.appmobile.ui.components.GameScreenShell

@Composable
fun GameCV2Page(onBack: () -> Unit) {
    val isRunning = remember { mutableStateOf(false) }

    GameScreenShell(contentMaxWidth = 1000) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Navigation
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color(0xFFE7F1FF),
                    border = BorderStroke(1.dp, Color(0xFFBFD7FF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = 0.5f,
                            modifier = Modifier.width(80.dp).height(8.dp),
                            color = Color(0xFF3B82F6),
                            trackColor = Color(0xFFD8E9FF)
                        )
                        Text("Màn 1/2", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Thử thách cảm xúc", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
            Text("Hãy thể hiện biểu cảm đúng theo yêu cầu nhé", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

            Spacer(modifier = Modifier.height(20.dp))

            BoxWithConstraints {
                val isMobile = maxWidth < 850.dp
                if (isMobile) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChallengeCard(isRunning.value) { isRunning.value = true }
                        CameraPreviewCard(isRunning.value) { isRunning.value = !isRunning.value }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) { ChallengeCard(isRunning.value) { isRunning.value = true } }
                        Box(modifier = Modifier.weight(1f)) { CameraPreviewCard(isRunning.value) { isRunning.value = !isRunning.value } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeCard(isRunning: Boolean, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Yêu cầu", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
            ) {
                Image(painter = painterResource(id = R.drawable.game_cv_2), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Text("Con hãy thể hiện khuôn mặt đúng với cảm xúc được giao.", style = MaterialTheme.typography.bodyMedium)
            Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFE7F1FF), border = BorderStroke(1.dp, Color(0xFFBFD7FF))) {
                Text("Cảm xúc: vui vẻ", modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold, color = Color(0xFF1E4E8C))
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), enabled = !isRunning) {
                Text(if (isRunning) "Đang thực hiện..." else "▶️ Bắt đầu")
            }
        }
    }
}

@Composable
private fun CameraPreviewCard(isRunning: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(240.dp).background(Color.Black, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                if (isRunning) Text("Camera View Active", color = Color.White)
                else Text("📷 Nhấn Bắt đầu để bật camera", color = Color.LightGray)
            }
            Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFF1F5F9)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Độ khớp")
                    Text("--%", fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
                }
            }
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isRunning) ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)) else ButtonDefaults.buttonColors()
            ) {
                Text(if (isRunning) "Dừng camera" else "Bật camera")
            }
        }
    }
}
