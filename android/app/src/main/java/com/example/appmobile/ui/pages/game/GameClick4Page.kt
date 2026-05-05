package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.GameScreenShell

private data class DetectiveQuestionUi(
    val story: String,
    val correctEmotion: String
)

@Composable
fun GameClick4Page(level: Int = 1, onBack: () -> Unit) {
    val currentIndex = remember(level) { mutableStateOf(0) }
    val selectedEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val questions = remember(level) { mutableStateOf(fallbackDetectiveQuestions()) }
    val context = LocalContext.current
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }

    LaunchedEffect(level) {
        val backendQuestions = runCatching {
            repository.getContentForLevel(GameUiCatalog.GAME_DETECTIVE, level)
                .mapNotNull { content ->
                    val emotion = content.answer.ifBlank { content.emotion }
                    if (emotion.isBlank()) return@mapNotNull null
                    DetectiveQuestionUi(
                        story = content.text.ifBlank { "Cảm xúc nào đang ẩn giấu?" },
                        correctEmotion = emotion
                    )
                }
        }.getOrDefault(emptyList())

        questions.value = backendQuestions.ifEmpty { fallbackDetectiveQuestions() }
        currentIndex.value = 0
        selectedEmotionId.value = null
    }

    val question = questions.value[currentIndex.value % questions.value.size]

    GameScreenShell(contentMaxWidth = 700) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Thám tử cảm xúc", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.game_click_4),
                        contentDescription = null,
                        modifier = Modifier.size(150.dp)
                    )
                    Text(
                        question.story,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val selected = selectedEmotionId.value
                    if (selected != null) {
                        val result = if (selected == question.correctEmotion) "Phá án đúng rồi" else "Chưa đúng, đọc lại manh mối nhé"
                        val resultColor = if (selected == question.correctEmotion) Color(0xFF2E7D32) else Color(0xFFC62828)
                        Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFF8FAFC)) {
                            Text(result, modifier = Modifier.padding(12.dp), color = resultColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GameUiCatalog.emotions.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { emotion ->
                            val isSelected = selectedEmotionId.value == emotion.id
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedEmotionId.value = emotion.id },
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(
                                    2.dp,
                                    if (isSelected) Color(0xFF3B82F6) else Color(0xFFF1F5F9)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFE7F1FF) else Color.White
                                )
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(emotion.emoji, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(emotion.name, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    currentIndex.value = (currentIndex.value + 1) % questions.value.size
                    selectedEmotionId.value = null
                },
                enabled = selectedEmotionId.value != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manh mối tiếp theo")
            }
        }
    }
}

private fun fallbackDetectiveQuestions(): List<DetectiveQuestionUi> {
    return listOf(
        DetectiveQuestionUi("Minh bám chặt tay mẹ khi thấy chó lớn. Cảm xúc nào đang ẩn giấu?", "fear")
    )
}
