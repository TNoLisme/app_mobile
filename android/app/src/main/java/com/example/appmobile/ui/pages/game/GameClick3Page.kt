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
import com.example.appmobile.data.local.AppSession
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
    val correctEmotion: String,
    val optionEmotionIds: List<String> = GameUiCatalog.emotions.map { it.id }
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
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
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
                val status = if (response.passed) "ÄÃ£ qua level" else "ChÆ°a qua level"
                "$status. Äiá»ƒm: ${response.score}/50."
            } else {
                "HoÃ n thÃ nh. Äiá»ƒm táº¡m tÃ­nh: ${score.intValue}."
            }
            response?.reviewEmotionsToLearn
                ?.firstOrNull()
                ?.let { learningEmotionId.value = normalizeEmotionForLearning(it) }
            isSubmitting.value = false
        }
    }

    LaunchedEffect(level, userId) {
        val started = repository.startGame(GameUiCatalog.GAME_EMOTION_MATCH, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotion = normalizeEmotionForLearning((content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null })
                MatchQuestionUi(
                    questionId = content.contentId,
                    text = content.questionText?.ifBlank { "Cáº£m xÃºc nÃ o phÃ¹ há»£p?" } ?: "Cáº£m xÃºc nÃ o phÃ¹ há»£p?",
                    correctEmotion = emotion,
                    optionEmotionIds = optionEmotionIdsFromBackend(content.options, emotion)
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
    val options = question.optionEmotionIds
        .mapNotNull { GameUiCatalog.emotionById(it) }
        .ifEmpty { GameUiCatalog.emotions }

    GameScreenShell(contentMaxWidth = 800, onOpenAssistant = onOpenAssistant) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("â† Quay láº¡i") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Cáº£m xÃºc Ä‘Ãºng chá»—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameStatChip("CÃ¢u ${currentIndex.intValue + 1}/${questions.value.size}")
                GameStatChip("Äiá»ƒm ${score.intValue}")
                GameStatChip("Level $level")
            }

            if (summary.value != null) {
                Spacer(modifier = Modifier.height(20.dp))
                GameLevelSummaryCard(summary = summary.value.orEmpty(), onBack = onBack)
                EmotionLearningDialog(
                    emotionId = learningEmotionId.value,
                    onDismiss = {
                        val emotion = learningEmotionId.value
                        learningEmotionId.value = null
                        if (emotion != null) {
                            scope.launch {
                                repository.resetReviewEmotions(GameUiCatalog.GAME_EMOTION_MATCH, userId, listOf(emotion))
                            }
                        }
                    }
                )
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
                    Text("TÃ¬nh huá»‘ng", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
                    Text(question.text, style = MaterialTheme.typography.titleMedium)

                    if (feedback.value != null) {
                        GameFeedbackCard(feedback.value.orEmpty())
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { emotion ->
                            val visualState = answerVisualState(
                                optionId = emotion.id,
                                correctEmotion = question.correctEmotion,
                                selectedEmotionId = selectedEmotionId.value,
                                hasFeedback = feedback.value != null
                            )
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = feedback.value == null) { selectedEmotionId.value = emotion.id },
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(
                                    2.dp,
                                    visualState.borderColor
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = visualState.containerColor
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
                        feedback.value = if (isCorrect) "ÄÃºng rá»“i." else "ChÆ°a Ä‘Ãºng. ÄÃ¡p Ã¡n lÃ  $targetName."
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
                        isSubmitting.value -> "Äang lÆ°u..."
                        feedback.value == null -> "Tráº£ lá»i"
                        currentIndex.intValue >= questions.value.lastIndex -> "HoÃ n thÃ nh"
                        else -> "CÃ¢u tiáº¿p theo"
                    }
                )
            }
            EmotionLearningDialog(
                emotionId = learningEmotionId.value,
                onDismiss = {
                    val emotion = learningEmotionId.value
                    learningEmotionId.value = null
                    if (emotion != null) {
                        scope.launch {
                            repository.resetReviewEmotions(GameUiCatalog.GAME_EMOTION_MATCH, userId, listOf(emotion))
                        }
                    }
                }
            )
        }
    }
}

private fun fallbackMatchQuestions(): List<MatchQuestionUi> {
    return listOf(
        MatchQuestionUi("fallback-match-happy", "BÃ© Ä‘Æ°á»£c táº·ng mÃ³n quÃ  yÃªu thÃ­ch. Cáº£m xÃºc nÃ o phÃ¹ há»£p?", "happy"),
        MatchQuestionUi("fallback-match-angry", "Báº¡n giáº­t Ä‘á»“ chÆ¡i khá»i tay bÃ©. Cáº£m xÃºc nÃ o cÃ³ thá»ƒ xuáº¥t hiá»‡n?", "angry")
    )
}

