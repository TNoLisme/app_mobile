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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgEmotionVectorIcon
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private data class DetectiveQuestionUi(
    val questionId: String,
    val story: String,
    val correctEmotion: String,
    val optionEmotionIds: List<String> = GameUiCatalog.emotions.map { it.id },
    val explanation: String? = null
)

@Composable
fun DetectiveGamePage(
    level: Int = 1,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit = {},
    onGameCompleted: (Int) -> Unit = {}
) {
    val currentIndex = remember(level) { mutableIntStateOf(0) }
    val score = remember(level) { mutableIntStateOf(0) }
    val selectedEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val feedback = remember(level) { mutableStateOf<String?>(null) }
    val questions = remember(level) { mutableStateOf(fallbackDetectiveQuestions()) }
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
                    repository.invalidateProgressCache(GameUiCatalog.GAME_DETECTIVE, userId)
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
                onGameCompleted(summaryData.value?.score ?: score.intValue)
                isSubmitting.value = false
            }
        }
    }

    LaunchedEffect(level, userId, replayCount.intValue) {
        val started = repository.startGame(GameUiCatalog.GAME_DETECTIVE, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotion = normalizeEmotionForLearning((content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null })
                DetectiveQuestionUi(
                    questionId = content.contentId,
                    story = content.questionText?.ifBlank { "Cảm xúc nào đang ẩn giấu?" } ?: "Cảm xúc nào đang ẩn giấu?",
                    correctEmotion = emotion,
                    optionEmotionIds = optionEmotionIdsFromBackend(content.options, emotion),
                    explanation = content.explanation
                )
            }
            .orEmpty()

        questions.value = backendQuestions.ifEmpty { fallbackDetectiveQuestions() }
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

    val question = questions.value[currentIndex.intValue % questions.value.size]
    val options = remember(question.questionId, question.optionEmotionIds) {
        question.optionEmotionIds
            .mapNotNull { GameUiCatalog.emotionById(it) }
            .ifEmpty { GameUiCatalog.emotions }
            .shuffled()
    }

    GameScreenShell(
        contentMaxWidth = 800,
        onOpenAssistant = onOpenAssistant,
        scrollEnabled = false,
        bottomSpacerHeight = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameHeader(
                title = "Thám tử cảm xúc",
                level = level,
                currentQuestion = currentIndex.intValue + 1,
                totalQuestions = questions.value.size,
                score = score.intValue,
                onBack = onBack
            )

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
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ClickGameInstructionCard(
                        title = "Đọc tình huống và tìm cảm xúc",
                        description = question.story,
                        iconKey = "puzzle"
                    ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.game_click_4),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.chunked(2).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { item ->
                                    val visualState = answerVisualState(
                                        optionId = item.id,
                                        correctEmotion = question.correctEmotion,
                                        selectedEmotionId = selectedEmotionId.value,
                                        hasFeedback = feedback.value != null
                                    )
                                    ClickEmotionOptionCard(
                                        emotionId = item.id,
                                        emotionName = item.name,
                                        visualState = visualState,
                                        enabled = feedback.value == null,
                                        compact = true,
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectedEmotionId.value = item.id }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

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
                        if (feedback.value != null) {
                            GameFeedbackCard(feedback.value.orEmpty())
                        }
                    }

                    // Dòng 3: Nút Trả lời / Câu tiếp theo (chiều cao cố định)
                    Box(
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                if (feedback.value == null) {
                                    val selected = selectedEmotionId.value ?: return@Button
                                    val isCorrect = selected == question.correctEmotion
                                    if (isCorrect) score.intValue += 10
                                    val reviewEmotion = normalizeEmotionForLearning(question.correctEmotion)
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
                                        questionId = question.questionId,
                                        answer = selected,
                                        isCorrect = isCorrect,
                                        responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt(),
                                        usedHint = false
                                    )
                                    results.value = updatedResults
                                    val targetName = GameUiCatalog.emotionById(question.correctEmotion)?.name
                                        ?: question.correctEmotion
                                    feedback.value = if (isCorrect) "Phá án đúng rồi." else "Chưa đúng. Đáp án là $targetName."
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
                            enabled = selectedEmotionId.value != null && !isSubmitting.value && learningEmotionId.value == null,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(EgDesign.pillRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary, contentColor = Color.White)
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
                                    else -> "Manh mối tiếp theo"
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
                                repository.resetReviewEmotions(GameUiCatalog.GAME_DETECTIVE, userId, listOf(emotion))
                            } catch (_: Exception) {}
                        }
                    }
                }
            )
        }
    }
}

private fun fallbackDetectiveQuestions(): List<DetectiveQuestionUi> {
    return listOf(
        DetectiveQuestionUi(
            "fallback-detective-fear",
            "Minh bám chặt tay mẹ khi thấy chó lớn. Cảm xúc nào đang ẩn giấu?",
            "fear",
            explanation = "Gợi ý: Khi sợ hãi, trẻ thường tìm đến người thân để được bảo vệ."
        ),
        DetectiveQuestionUi(
            "fallback-detective-happy",
            "Lan được cô giáo khen vì biết chia sẻ đồ chơi. Cảm xúc nào đang ẩn giấu?",
            "happy",
            explanation = "Gợi ý: Khi được khen hoặc làm điều tốt, bé thường vui và mỉm cười."
        ),
        DetectiveQuestionUi(
            "fallback-detective-angry",
            "Bình đang xếp tháp thì bạn khác chạy tới làm đổ. Cảm xúc nào đang ẩn giấu?",
            "angry",
            explanation = "Gợi ý: Khi công sức bị phá hỏng, bé có thể tức giận."
        ),
        DetectiveQuestionUi(
            "fallback-detective-sad",
            "Mai làm rơi cây kem yêu thích xuống đất và cúi mặt im lặng. Cảm xúc nào đang ẩn giấu?",
            "sad",
            explanation = "Gợi ý: Khi mất món đồ yêu thích, bé có thể buồn bã."
        ),
        DetectiveQuestionUi(
            "fallback-detective-surprise",
            "Nam mở hộp quà và thấy món đồ chơi mình mong muốn từ lâu. Cảm xúc nào đang ẩn giấu?",
            "surprise",
            explanation = "Gợi ý: Khi gặp điều bất ngờ, mắt bé thường mở to và miệng hơi há."
        )
    )
}
