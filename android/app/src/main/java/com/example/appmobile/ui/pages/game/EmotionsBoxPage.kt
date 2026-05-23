package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private data class RecognizeQuestionUi(
    val questionId: String,
    val questionText: String,
    val imageRes: Int,
    val mediaPath: String?,
    val correctEmotion: String,
    val optionEmotionIds: List<String> = GameUiCatalog.emotions.map { it.id },
    val explanation: String? = null
)

@Composable
fun EmotionsBoxPage(level: Int = 1, onBack: () -> Unit, onOpenAssistant: () -> Unit = {}) {
    val currentIndex = remember(level) { mutableIntStateOf(0) }
    val score = remember(level) { mutableIntStateOf(0) }
    val selectedEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val feedback = remember(level) { mutableStateOf<String?>(null) }
    val questions = remember(level) { mutableStateOf(fallbackRecognizeQuestions()) }
    val sessionId = remember(level) { mutableStateOf<String?>(null) }
    val results = remember(level) { mutableStateOf<List<AnswerResultDto>>(emptyList()) }
    val summary = remember(level) { mutableStateOf<String?>(null) }
    val replayCount = remember { mutableIntStateOf(0) }
    val isSubmitting = remember(level) { mutableStateOf(false) }
    val questionStartMs = remember(level) { mutableStateOf(System.currentTimeMillis()) }
    val maxErrors = remember(level) { mutableIntStateOf(3) }
    val emotionErrors = remember(level) { mutableStateMapOf<String, Int>() }
    val accumulatedErrors = remember(level) { mutableStateMapOf<String, Int>() }
    val learnedEmotions = remember(level) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val pendingLearnEmotion = remember(level) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }

    var summaryData = remember(level) { mutableStateOf<LevelSummaryData?>(null) }

    fun finishLevel(finalResults: List<AnswerResultDto>) {
        if (isSubmitting.value || summary.value != null) return
        scope.launch {
            try {
                isSubmitting.value = true
                val response = sessionId.value?.let {
                    repository.endLevel(it, finalResults, learnedEmotions.distinct())
                }
                if (response != null) {
                    summaryData.value = LevelSummaryData(
                        passed = response.passed,
                        score = response.score,
                        totalScore = 50,
                        accuracy = response.accuracy,
                        correctCount = finalResults.count { it.isCorrect },
                        totalQuestions = finalResults.size
                    )
                    repository.invalidateProgressCache(GameUiCatalog.GAME_RECOGNIZE_EMOTION, userId)
                    val status = if (response.passed) "Đã qua level" else "Chưa qua level"
                    summary.value = "$status. Điểm: ${response.score}/50."
                } else {
                    val totalQ = finalResults.size
                    val correctC = finalResults.count { it.isCorrect }
                    val acc = if (totalQ > 0) (correctC.toFloat() / totalQ) * 100f else 0f
                    summaryData.value = LevelSummaryData(
                        passed = acc >= 50f,
                        score = score.intValue,
                        totalScore = totalQ * 10,
                        accuracy = acc,
                        correctCount = correctC,
                        totalQuestions = totalQ
                    )
                    summary.value = "Hoàn thành."
                }
            } catch (_: Exception) {
                val totalQ = finalResults.size
                val correctC = finalResults.count { it.isCorrect }
                val acc = if (totalQ > 0) (correctC.toFloat() / totalQ) * 100f else 0f
                summaryData.value = LevelSummaryData(
                    passed = acc >= 50f,
                    score = score.intValue,
                    totalScore = totalQ * 10,
                    accuracy = acc,
                    correctCount = correctC,
                    totalQuestions = totalQ
                )
                summary.value = "Hoàn thành."
            } finally {
                isSubmitting.value = false
            }
        }
    }

    LaunchedEffect(level, userId, replayCount.intValue) {
        val started = repository.startGame(GameUiCatalog.GAME_RECOGNIZE_EMOTION, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotion = normalizeEmotionForLearning((content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null })
                RecognizeQuestionUi(
                    questionId = content.contentId,
                    questionText = content.questionText?.ifBlank { "Đây là cảm xúc gì?" } ?: "Đây là cảm xúc gì?",
                    imageRes = emotionDrawableResource(emotion),
                    mediaPath = content.mediaPath,
                    correctEmotion = emotion,
                    optionEmotionIds = optionEmotionIdsFromBackend(content.options, emotion),
                    explanation = content.explanation
                )
            }
            .orEmpty()

        questions.value = backendQuestions.ifEmpty { fallbackRecognizeQuestions() }
        currentIndex.intValue = 0
        score.intValue = 0
        selectedEmotionId.value = null
        feedback.value = null
        results.value = emptyList()
        summary.value = null
        summaryData.value = null
        emotionErrors.clear()
        accumulatedErrors.clear()
        started?.reviewEmotions?.forEach { (emotion, count) ->
            accumulatedErrors[normalizeEmotionForLearning(emotion)] = count
        }
        learnedEmotions.clear()
        learningEmotionId.value = null
        pendingLearnEmotion.value = null
        questionStartMs.value = System.currentTimeMillis()
    }

    val currentQuestion = questions.value[currentIndex.intValue % questions.value.size]
    val options = GameUiCatalog.emotions

    GameScreenShell(
        contentMaxWidth = 800,
        onOpenAssistant = onOpenAssistant,
        scrollEnabled = false,
        bottomSpacerHeight = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Chiếc hộp cảm xúc", style = MaterialTheme.typography.titleLarge, color = EgDesign.textPrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameStatChip("Câu ${currentIndex.intValue + 1}/${questions.value.size}")
                GameStatChip("Điểm ${score.intValue}")
                GameStatChip("Level $level")
            }

            if (summary.value != null) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(20.dp))
                    GameLevelSummaryCard(
                        summaryData = summaryData.value,
                        summary = summary.value.orEmpty(),
                        onBack = onBack,
                        onReplay = { replayCount.intValue++ }
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!currentQuestion.mediaPath.isNullOrBlank()) {
                                    val assetPath = if (currentQuestion.mediaPath.startsWith("/")) {
                                        "file:///android_asset${currentQuestion.mediaPath}"
                                    } else {
                                        "file:///android_asset/${currentQuestion.mediaPath}"
                                    }
                                    AsyncImage(
                                        model = assetPath,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = currentQuestion.imageRes),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            Text(currentQuestion.questionText, style = MaterialTheme.typography.titleMedium, color = EgDesign.textSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { item ->
                                    val visualState = answerVisualState(
                                        optionId = item.id,
                                        correctEmotion = currentQuestion.correctEmotion,
                                        selectedEmotionId = selectedEmotionId.value,
                                        hasFeedback = feedback.value != null
                                    )
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable(enabled = feedback.value == null) { selectedEmotionId.value = item.id },
                                        shape = MaterialTheme.shapes.large,
                                        border = BorderStroke(
                                            2.dp,
                                            visualState.borderColor
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = visualState.containerColor
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item.emoji, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(item.name, color = EgDesign.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val hintVisible = remember(currentIndex.intValue) { mutableStateOf(false) }
                val usedHint = remember(currentIndex.intValue) { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!hintVisible.value) {
                            Surface(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        hintVisible.value = true
                                        usedHint.value = true
                                    },
                                shape = CircleShape,
                                color = EgDesign.accentSoft,
                                border = BorderStroke(1.dp, EgDesign.cardBorder)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("💡", fontSize = 20.sp)
                                }
                            }
                        } else {
                            Text(
                                text = currentQuestion.explanation ?: "Gợi ý: Hãy quan sát kỹ ánh mắt và khuôn miệng của người trong ảnh nhé!",
                                color = EgDesign.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (feedback.value != null) {
                            GameFeedbackCard(feedback.value.orEmpty())
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                if (feedback.value == null) {
                                    val selected = selectedEmotionId.value ?: return@Button
                                    val isCorrect = selected == currentQuestion.correctEmotion
                                    if (isCorrect) score.intValue += 10
                                    val reviewEmotion = normalizeEmotionForLearning(currentQuestion.correctEmotion)
                                    if (!isCorrect) {
                                        emotionErrors[reviewEmotion] = (emotionErrors[reviewEmotion] ?: 0) + 1
                                        val sessionErrors = emotionErrors[reviewEmotion] ?: 0
                                        val totalErrors = sessionErrors + (accumulatedErrors[reviewEmotion] ?: 0)
                                        if (totalErrors >= maxErrors.intValue && reviewEmotion !in learnedEmotions) {
                                            learnedEmotions.add(reviewEmotion)
                                            pendingLearnEmotion.value = reviewEmotion
                                            learningEmotionId.value = reviewEmotion
                                        }
                                    }
                                    val updatedResults = results.value + AnswerResultDto(
                                        questionId = currentQuestion.questionId,
                                        answer = selected,
                                        isCorrect = isCorrect,
                                        responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt(),
                                        usedHint = usedHint.value
                                    )
                                    results.value = updatedResults
                                    val targetName = GameUiCatalog.emotionById(currentQuestion.correctEmotion)?.name
                                        ?: currentQuestion.correctEmotion
                                    feedback.value = if (isCorrect) "Đúng rồi." else "Chưa đúng. Đáp án là $targetName."
                                    return@Button
                                }

                                if (pendingLearnEmotion.value != null) {
                                    learningEmotionId.value = pendingLearnEmotion.value
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
                                    hintVisible.value = false
                                    usedHint.value = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = selectedEmotionId.value != null && !isSubmitting.value && learningEmotionId.value == null
                        ) {
                            val learnTarget = pendingLearnEmotion.value?.let {
                                GameUiCatalog.emotionById(it)?.name ?: it
                            }
                            Text(
                                when {
                                    isSubmitting.value -> "Đang lưu..."
                                    feedback.value == null -> "Trả lời"
                                    pendingLearnEmotion.value != null -> "Học về $learnTarget"
                                    currentIndex.intValue >= questions.value.lastIndex -> "Hoàn thành"
                                    else -> "Câu tiếp theo"
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            EmotionLearningDialog(
                emotionId = learningEmotionId.value,
                onDismiss = {
                    val emotion = learningEmotionId.value
                    learningEmotionId.value = null
                    pendingLearnEmotion.value = null
                    if (emotion != null) {
                        emotionErrors[emotion] = 0
                        accumulatedErrors[emotion] = 0
                        scope.launch {
                            try {
                                repository.resetReviewEmotions(GameUiCatalog.GAME_RECOGNIZE_EMOTION, userId, listOf(emotion))
                            } catch (_: Exception) {}
                        }
                    }
                }
            )
        }
    }
}

private fun fallbackRecognizeQuestions(): List<RecognizeQuestionUi> {
    return listOf(
        RecognizeQuestionUi("fallback-recognize-angry", "Đây là cảm xúc gì?", R.drawable.angry_1, null, "angry"),
        RecognizeQuestionUi("fallback-recognize-fear", "Đây là cảm xúc gì?", R.drawable.fear_1, null, "fear"),
        RecognizeQuestionUi("fallback-recognize-disgust", "Đây là cảm xúc gì?", R.drawable.disgust_1, null, "disgust")
    )
}
