package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private data class MatchQuestionUi(
    val questionId: String,
    val text: String,
    val correctEmotion: String
)

@Composable
fun GameClick3Page(level: Int = 1, onBack: () -> Unit, onOpenAssistant: () -> Unit = {}) {
    val currentIndex = remember(level) { mutableIntStateOf(0) }
    val score = remember(level) { mutableIntStateOf(0) }
    val selectedEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val feedback = remember(level) { mutableStateOf<String?>(null) }
    val questions = remember(level) { mutableStateOf(fallbackMatchQuestions()) }
    val sessionId = remember(level) { mutableStateOf<String?>(null) }
    val results = remember(level) { mutableStateOf<List<AnswerResultDto>>(emptyList()) }
    val summary = remember(level) { mutableStateOf<String?>(null) }
    val isSubmitting = remember(level) { mutableStateOf(false) }
    val questionStartMs = remember(level) { mutableStateOf(System.currentTimeMillis()) }
    val maxErrors = remember(level) { mutableIntStateOf(3) }
    val emotionErrors = remember(level) { mutableStateMapOf<String, Int>() }
    val learnedEmotions = remember(level) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "local-player" }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }

    fun finishLevel(finalResults: List<AnswerResultDto>) {
        if (isSubmitting.value || summary.value != null) return
        scope.launch {
            isSubmitting.value = true
            val response = sessionId.value?.let {
                repository.endLevel(it, finalResults, learnedEmotions.distinct())
            }
            summary.value = if (response != null) {
                val status = if (response.passed) "Đã qua level" else "Chưa qua level"
                "$status. Điểm: ${response.score}/100."
            } else {
                "Hoàn thành. Điểm tạm tính: ${score.intValue}."
            }
            isSubmitting.value = false
        }
    }

    LaunchedEffect(level, userId) {
        val started = repository.startGame(GameUiCatalog.GAME_EMOTION_MATCH, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotion = (content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null }
                MatchQuestionUi(
                    questionId = content.contentId,
                    text = content.questionText?.ifBlank { "Cảm xúc nào phù hợp?" } ?: "Cảm xúc nào phù hợp?",
                    correctEmotion = emotion
                )
            }
            .orEmpty()

        questions.value = backendQuestions.ifEmpty { fallbackMatchQuestions() }
        currentIndex.intValue = 0
        score.intValue = 0
        selectedEmotionId.value = null
        feedback.value = null
        results.value = emptyList()
        summary.value = null
        emotionErrors.clear()
        learnedEmotions.clear()
        learningEmotionId.value = null
        questionStartMs.value = System.currentTimeMillis()
    }

    val question = questions.value[currentIndex.intValue % questions.value.size]

    GameScreenShell(contentMaxWidth = 800, onOpenAssistant = onOpenAssistant) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Cảm xúc đúng chỗ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameStatChip("Câu ${currentIndex.intValue + 1}/${questions.value.size}")
                GameStatChip("Điểm ${score.intValue}")
                GameStatChip("Level $level")
            }

            if (summary.value != null) {
                Spacer(modifier = Modifier.height(20.dp))
                GameLevelSummaryCard(summary = summary.value.orEmpty(), onBack = onBack)
                return@Column
            }

            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Tình huống", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
                    Text(question.text, style = MaterialTheme.typography.titleMedium)

                    if (feedback.value != null) {
                        GameFeedbackCard(feedback.value.orEmpty())
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
                                    .clickable(enabled = feedback.value == null) { selectedEmotionId.value = emotion.id },
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
                    if (feedback.value == null) {
                        val selected = selectedEmotionId.value ?: return@Button
                        val isCorrect = selected == question.correctEmotion
                        if (isCorrect) score.intValue += 10
                        val reviewEmotion = normalizeEmotionForLearning(question.correctEmotion)
                        if (!isCorrect) {
                            val nextErrorCount = (emotionErrors[reviewEmotion] ?: 0) + 1
                            emotionErrors[reviewEmotion] = nextErrorCount
                            if (nextErrorCount >= maxErrors.intValue && reviewEmotion !in learnedEmotions) {
                                learnedEmotions.add(reviewEmotion)
                                learningEmotionId.value = reviewEmotion
                            }
                        }
                        val updatedResults = results.value + AnswerResultDto(
                            questionId = question.questionId,
                            answer = selected,
                            isCorrect = isCorrect,
                            responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt()
                        )
                        results.value = updatedResults
                        val targetName = GameUiCatalog.emotionById(question.correctEmotion)?.name ?: question.correctEmotion
                        feedback.value = if (isCorrect) "Đúng rồi." else "Chưa đúng. Đáp án là $targetName."
                        return@Button
                    }

                    val isLastQuestion = currentIndex.intValue >= questions.value.lastIndex
                    if (isLastQuestion) {
                        finishLevel(results.value)
                    } else {
                        currentIndex.intValue += 1
                        selectedEmotionId.value = null
                        feedback.value = null
                        questionStartMs.value = System.currentTimeMillis()
                    }
                },
                enabled = selectedEmotionId.value != null && !isSubmitting.value,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        isSubmitting.value -> "Đang lưu..."
                        feedback.value == null -> "Trả lời"
                        currentIndex.intValue >= questions.value.lastIndex -> "Hoàn thành"
                        else -> "Câu tiếp theo"
                    }
                )
            }
            EmotionLearningDialog(
                emotionId = learningEmotionId.value,
                onDismiss = { learningEmotionId.value = null }
            )
        }
    }
}

private fun fallbackMatchQuestions(): List<MatchQuestionUi> {
    return listOf(
        MatchQuestionUi("fallback-match-happy", "Bé được tặng món quà yêu thích. Cảm xúc nào phù hợp?", "happy"),
        MatchQuestionUi("fallback-match-angry", "Bạn giật đồ chơi khỏi tay bé. Cảm xúc nào có thể xuất hiện?", "angry")
    )
}
