package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.appmobile.ui.catalog.CvPromptUiItem
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.GameScreenShell

@Composable
fun GameCVPage(onBack: () -> Unit) {
    val isRunning = remember { mutableStateOf(false) }
    val challenge = remember {
        GameUiCatalog.cvStoryPrompt
    }

    GameScreenShell(contentMaxWidth = 1000) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                            progress = { 0.3f },
                            modifier = Modifier.width(80.dp).height(8.dp),
                            color = Color(0xFF3B82F6),
                            trackColor = Color(0xFFD8E9FF)
                        )
                        Text("Màn 1/10", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Câu chuyện khuôn mặt",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E4E8C)
            )
            Text(
                "Đọc tình huống và thể hiện cảm xúc phù hợp",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(20.dp))

            BoxWithConstraints {
                val isMobile = maxWidth < 850.dp
                if (isMobile) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SituationCard(challenge, isRunning.value) { isRunning.value = true }
                        CameraPreviewCard(isRunning.value) { isRunning.value = !isRunning.value }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            SituationCard(challenge, isRunning.value) { isRunning.value = true }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CameraPreviewCard(isRunning.value) { isRunning.value = !isRunning.value }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SituationCard(challenge: CvPromptUiItem, isRunning: Boolean, onStart: () -> Unit) {
    val targetEmotion = GameUiCatalog.emotionById(challenge.correctAnswer)?.name ?: challenge.correctAnswer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tình huống", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.game_cv),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Text(challenge.questionText, style = MaterialTheme.typography.bodyMedium)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color(0xFFE7F1FF),
                border = BorderStroke(1.dp, Color(0xFFBFD7FF))
            ) {
                Text(
                    "Cảm xúc cần đạt: $targetEmotion",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E4E8C)
                )
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), enabled = !isRunning) {
                Text(if (isRunning) "Đang thực hiện..." else "Bắt đầu")
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
                if (isRunning) Text("Camera đang hoạt động", color = Color.White)
                else Text("Nhấn Bắt đầu để bật camera", color = Color.LightGray)
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
                colors = if (isRunning) {
                    ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isRunning) "Dừng camera" else "Bật camera")
            }
        }
    }
}
