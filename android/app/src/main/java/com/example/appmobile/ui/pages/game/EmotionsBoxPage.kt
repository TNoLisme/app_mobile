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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.GameScreenShell
import com.example.appmobile.ui.state.AppSettingsState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private data class RecognizeQuestionUi(
    val questionId: String,
    val questionText: String,
    val imageRes: Int,
    val correctEmotion: String,
    val optionEmotionIds: List<String> = GameUiCatalog.emotions.map { it.id }
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
                    }
            } catch (_: Exception) {
                summary.value = "Hoàn thành. Điểm tạm tính: ${score.intValue}."
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
                    correctEmotion = emotion,
                    optionEmotionIds = optionEmotionIdsFromBackend(content.options, emotion)
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
    val options = currentQuestion.optionEmotionIds
        .mapNotNull { GameUiCatalog.emotionById(it) }
        .ifEmpty { GameUiCatalog.emotions }

    GameScreenShell(
        contentMaxWidth = 800,
        onOpenAssistant = onOpenAssistant,
        scrollEnabled = false,
        bottomSpacerHeight = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Thoát") }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = EgDesign.cardSoft,
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { (currentIndex.intValue + 1).toFloat() / questions.value.size },
                            modifier = Modifier.width(60.dp).height(6.dp),
                            color = Color(0xFF3B82F6)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Câu ${currentIndex.intValue + 1}/${questions.value.size}", color = EgDesign.textPrimary)
                    }
                }
            }

            if (summary.value != null) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
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
                                fontWeight = FontWeight.Bold,
                                color = EgDesign.textPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        options.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item.emoji, fontSize = 24.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(item.name, color = EgDesign.textPrimary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Vùng phản hồi cố định chiều cao để button không bị nhảy
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (feedback.value != null) {
                        FeedbackCard(feedback.value.orEmpty())
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                                responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt()
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
                Spacer(modifier = Modifier.height(4.dp))
            }
            else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Chiếc hộp cảm xúc",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = EgDesign.textPrimary
                )
                Text(
                    "Bé hãy nhìn hình và chọn cảm xúc đúng nhất nhé",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EgDesign.textSecondary
                )

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
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
                                fontWeight = FontWeight.Bold,
                                color = EgDesign.textPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        options.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item.emoji, fontSize = 24.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(item.name, color = EgDesign.textPrimary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (feedback.value != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        FeedbackCard(feedback.value.orEmpty())
                    }

                    Spacer(modifier = Modifier.height(20.dp))
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
                                    responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt()
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
                    Spacer(modifier = Modifier.height(24.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
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

@Composable
private fun FeedbackCard(message: String) {
    val isCorrect = message.startsWith("Đúng")
    val isDark = AppSettingsState.activeDarkTheme.value
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isCorrect) {
            if (isDark) Color(0xFF153E2A) else Color(0xFFE8F5E9)
        } else {
            if (isDark) Color(0xFF4A2D12) else Color(0xFFFFF3E0)
        }
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = if (isCorrect) {
                if (isDark) Color(0xFF86EFAC) else Color(0xFF2E7D32)
            } else {
                if (isDark) Color(0xFFFBBF24) else Color(0xFFE65100)
            },
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun fallbackRecognizeQuestions(): List<RecognizeQuestionUi> {
    return listOf(
        RecognizeQuestionUi("fallback-recognize-angry", "Đây là cảm xúc gì?", R.drawable.angry_1, "angry"),
        RecognizeQuestionUi("fallback-recognize-fear", "Đây là cảm xúc gì?", R.drawable.fear_1, "fear"),
        RecognizeQuestionUi("fallback-recognize-disgust", "Đây là cảm xúc gì?", R.drawable.disgust_1, "disgust")
    )
}

