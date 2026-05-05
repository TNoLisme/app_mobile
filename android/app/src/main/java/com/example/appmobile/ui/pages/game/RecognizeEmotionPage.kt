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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
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

private data class RecognizeQuestionUi(
    val questionText: String,
    val imageRes: Int,
    val correctEmotion: String
)

@Composable
fun RecognizeEmotionPage(level: Int = 1, onBack: () -> Unit) {
    val currentIndex = remember(level) { mutableStateOf(0) }
    val selectedEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val questions = remember(level) { mutableStateOf(fallbackRecognizeQuestions()) }
    val context = LocalContext.current
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }

    LaunchedEffect(level) {
        val backendQuestions = runCatching {
            repository.getContentForLevel(GameUiCatalog.GAME_RECOGNIZE_EMOTION, level)
                .mapNotNull { content ->
                    val emotion = content.answer.ifBlank { content.emotion }
                    if (emotion.isBlank()) return@mapNotNull null
                    RecognizeQuestionUi(
                        questionText = content.text.ifBlank { "Đây là cảm xúc gì?" },
                        imageRes = emotionImageResource(emotion),
                        correctEmotion = emotion
                    )
                }
        }.getOrDefault(emptyList())

        questions.value = backendQuestions.ifEmpty { fallbackRecognizeQuestions() }
        currentIndex.value = 0
        selectedEmotionId.value = null
    }

    val currentQuestion = questions.value[currentIndex.value % questions.value.size]
    val options = GameUiCatalog.emotions

    GameScreenShell(contentMaxWidth = 800) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Thoát") }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color(0xFFE7F1FF),
                    border = BorderStroke(1.dp, Color(0xFFBFD7FF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { (currentIndex.value + 1).toFloat() / questions.value.size },
                            modifier = Modifier.width(60.dp).height(6.dp),
                            color = Color(0xFF3B82F6)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Câu ${currentIndex.value + 1}/${questions.value.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Chiếc hộp cảm xúc",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E4E8C)
            )
            Text(
                "Bé hãy nhìn hình và chọn cảm xúc đúng nhất nhé",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = currentQuestion.imageRes),
                        contentDescription = null,
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        currentQuestion.questionText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { item ->
                            val isSelected = selectedEmotionId.value == item.id
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedEmotionId.value = item.id },
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(
                                    2.dp,
                                    if (isSelected) Color(0xFF3B82F6) else Color(0xFFF1F5F9)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFE7F1FF) else Color.White
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.emoji, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    currentIndex.value = (currentIndex.value + 1) % questions.value.size
                    selectedEmotionId.value = null
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = selectedEmotionId.value != null
            ) {
                Text("Trả lời")
            }
        }
    }
}

private fun fallbackRecognizeQuestions(): List<RecognizeQuestionUi> {
    return listOf(
        RecognizeQuestionUi("Đây là cảm xúc gì?", R.drawable.happy_1, "happy"),
        RecognizeQuestionUi("Đây là cảm xúc gì?", R.drawable.sad_1, "sad"),
        RecognizeQuestionUi("Đây là cảm xúc gì?", R.drawable.surprise_1, "surprise"),
        RecognizeQuestionUi("Đây là cảm xúc gì?", R.drawable.angry_1, "angry"),
        RecognizeQuestionUi("Đây là cảm xúc gì?", R.drawable.fear_1, "fear"),
        RecognizeQuestionUi("Đây là cảm xúc gì?", R.drawable.disgust_1, "disgust")
    )
}

private fun emotionImageResource(emotion: String): Int {
    return when (emotion) {
        "happy" -> R.drawable.happy_1
        "sad" -> R.drawable.sad_1
        "angry" -> R.drawable.angry_1
        "fear" -> R.drawable.fear_1
        "surprise" -> R.drawable.surprise_1
        "disgust" -> R.drawable.disgust_1
        else -> R.drawable.recognize_emotion
    }
}
