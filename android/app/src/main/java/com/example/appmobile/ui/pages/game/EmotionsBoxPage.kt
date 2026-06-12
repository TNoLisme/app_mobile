package com.example.appmobile.ui.pages.game

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
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
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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
fun EmotionsBoxPage(
    level: Int = 1,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit = {},
    onGameCompleted: (Int) -> Unit = {}
) {
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
    val showExitConfirm = remember(level) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember(context) {
        FirebaseAuth.getInstance().currentUser?.uid
            ?: AppSession.getBackendUserId(context)
            ?: AppSession.currentBackendUserId()
            ?: "local-player"
    }
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
                        totalScore = 100,
                        accuracy = response.accuracy,
                        correctCount = finalResults.count { it.isCorrect },
                        totalQuestions = finalResults.size
                    )
                    repository.invalidateProgressCache(GameUiCatalog.GAME_RECOGNIZE_EMOTION, userId)
                    val status = if (response.passed) "Đã qua level" else "Chưa qua level"
                    summary.value = "$status. Điểm: ${response.score}/100."
                } else {
                    val totalQ = finalResults.size
                    val correctC = finalResults.count { it.isCorrect }
                    val acc = if (totalQ > 0) (correctC.toFloat() / totalQ) * 100f else 0f
                    summaryData.value = LevelSummaryData(
                        passed = acc >= 80f,
                        score = score.intValue,
                        totalScore = 100,
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
                    passed = acc >= 80f,
                    score = score.intValue,
                    totalScore = 100,
                    accuracy = acc,
                    correctCount = correctC,
                    totalQuestions = totalQ
                )
                summary.value = "Hoàn thành."
            } finally {
                clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_RECOGNIZE_EMOTION, level)
                val completedScore = summaryData.value?.score ?: score.intValue
                saveLocalUnlockedLevel(
                    context = context,
                    userId = userId,
                    gameId = GameUiCatalog.GAME_RECOGNIZE_EMOTION,
                    completedLevel = level,
                    score = completedScore
                )
                onGameCompleted(completedScore)
                isSubmitting.value = false
            }
        }
    }

    fun checkpointIndex(): Int {
        val answeredCurrent = feedback.value != null &&
            results.value.any { it.questionId == questions.value.getOrNull(currentIndex.intValue)?.questionId }
        return if (answeredCurrent) {
            (currentIndex.intValue + 1).coerceAtMost((questions.value.size - 1).coerceAtLeast(0))
        } else {
            currentIndex.intValue
        }
    }

    fun saveAndExit() {
        saveClickGameCheckpoint(
            context = context,
            userId = userId,
            gameId = GameUiCatalog.GAME_RECOGNIZE_EMOTION,
            level = level,
            sessionId = sessionId.value,
            score = score.intValue,
            currentIndex = checkpointIndex(),
            maxErrors = maxErrors.intValue,
            results = results.value,
            questions = recognizeQuestionsToJson(questions.value)
        )
        showExitConfirm.value = false
        onBack()
    }

    fun exitWithoutSaving() {
        clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_RECOGNIZE_EMOTION, level)
        showExitConfirm.value = false
        onBack()
    }

    fun requestExit() {
        val hasProgress = summary.value == null && (results.value.isNotEmpty() || currentIndex.intValue > 0)
        if (hasProgress) showExitConfirm.value = true else onBack()
    }

    LaunchedEffect(level, userId, replayCount.intValue) {
        val checkpoint = loadClickGameCheckpointJson(context, userId, GameUiCatalog.GAME_RECOGNIZE_EMOTION, level)
        if (checkpoint != null) {
            val restoredQuestions = recognizeQuestionsFromJson(checkpoint.optJSONArray("questions"))
            if (restoredQuestions.isNotEmpty()) {
                questions.value = restoredQuestions
                sessionId.value = checkpoint.optString("session_id").takeIf { it.isNotBlank() && it != "null" }
                maxErrors.intValue = checkpoint.optInt("max_errors", 3).coerceAtLeast(1)
                currentIndex.intValue = checkpoint.optInt("current_index", 0).coerceIn(0, restoredQuestions.lastIndex)
                score.intValue = checkpoint.optInt("score", 0).coerceIn(0, 100)
                selectedEmotionId.value = null
                feedback.value = null
                results.value = answerResultsFromCheckpoint(checkpoint)
                summary.value = null
                summaryData.value = null
                emotionErrors.clear()
                accumulatedErrors.clear()
                learnedEmotions.clear()
                learningEmotionId.value = null
                pendingLearnEmotion.value = null
                questionStartMs.value = System.currentTimeMillis()
                return@LaunchedEffect
            }
        }
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
    val hintShown = remember(currentIndex.intValue) { mutableStateOf(false) }

    BackHandler(enabled = summary.value == null) {
        requestExit()
    }

    GameScreenShell(
        contentMaxWidth = 800,
        onOpenAssistant = onOpenAssistant,
        scrollEnabled = false,
        bottomSpacerHeight = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameHeader(
                title = "Chiếc hộp cảm xúc",
                level = level,
                currentQuestion = currentIndex.intValue + 1,
                totalQuestions = questions.value.size,
                score = score.intValue,
                onBack = { requestExit() }
            )

            if (summary.value != null) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GameLevelSummaryCard(
                        summaryData = summaryData.value,
                        summary = summary.value.orEmpty(),
                        onBack = {
                            clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_RECOGNIZE_EMOTION, level)
                            onBack()
                        },
                        onReplay = {
                            clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_RECOGNIZE_EMOTION, level)
                            replayCount.intValue++
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ClickGameInstructionCard(
                        title = "Nhìn hình và chọn cảm xúc",
                        description = currentQuestion.questionText,
                        iconKey = "eye"
                    ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(108.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GameQuestionMedia(
                                    mediaPath = currentQuestion.mediaPath,
                                    fallbackRes = currentQuestion.imageRes,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
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
                                        correctEmotion = currentQuestion.correctEmotion,
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
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (feedback.value != null) {
                            GameFeedbackCard(feedback.value.orEmpty())
                        } else if (!currentQuestion.explanation.isNullOrBlank()) {
                            if (hintShown.value) {
                                GameHintCard(text = currentQuestion.explanation)
                            } else {
                                GameHintCard(onClick = { hintShown.value = true })
                            }
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
                                        usedHint = false
                                    )
                                    results.value = updatedResults
                                    score.intValue = scoreFromCorrectAnswers(
                                        updatedResults.count { it.isCorrect },
                                        questions.value.size
                                    )
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
                            enabled = selectedEmotionId.value != null && !isSubmitting.value && learningEmotionId.value == null,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(EgDesign.pillRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary, contentColor = Color.White)
                        ) {
                            val learnTarget = pendingLearnEmotion.value?.let {
                                GameUiCatalog.emotionById(it)?.name ?: it
                            }
                            Text(
                                text = when {
                                    isSubmitting.value -> "Đang lưu..."
                                    feedback.value == null -> "Trả lời"
                                    pendingLearnEmotion.value != null -> "Học về $learnTarget"
                                    currentIndex.intValue >= questions.value.lastIndex -> "Hoàn thành"
                                    else -> "Câu tiếp theo"
                                },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
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

    if (showExitConfirm.value) {
        ClickGameExitConfirmDialog(
            onDismiss = { showExitConfirm.value = false },
            onSaveAndExit = { saveAndExit() },
            onExitWithoutSaving = { exitWithoutSaving() }
        )
    }
}

private fun recognizeQuestionsToJson(questions: List<RecognizeQuestionUi>): JSONArray {
    return JSONArray().apply {
        questions.forEach { question ->
            put(JSONObject().apply {
                put("question_id", question.questionId)
                put("question_text", question.questionText)
                put("media_path", question.mediaPath ?: JSONObject.NULL)
                put("correct_emotion", question.correctEmotion)
                put("explanation", question.explanation ?: JSONObject.NULL)
                put("options", JSONArray().apply {
                    question.optionEmotionIds.forEach(::put)
                })
            })
        }
    }
}

private fun recognizeQuestionsFromJson(array: JSONArray?): List<RecognizeQuestionUi> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val emotion = normalizeEmotionForLearning(item.optString("correct_emotion"))
            val options = item.optJSONArray("options")?.let { optionArray ->
                buildList {
                    for (optionIndex in 0 until optionArray.length()) {
                        optionArray.optString(optionIndex).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.orEmpty()
            add(
                RecognizeQuestionUi(
                    questionId = item.optString("question_id"),
                    questionText = item.optString("question_text", "Đây là cảm xúc gì?"),
                    imageRes = emotionDrawableResource(emotion),
                    mediaPath = item.optString("media_path").takeIf { it.isNotBlank() && it != "null" },
                    correctEmotion = emotion,
                    optionEmotionIds = options.ifEmpty { GameUiCatalog.emotions.map { it.id } },
                    explanation = item.optString("explanation").takeIf { it.isNotBlank() && it != "null" }
                )
            )
        }
    }
}

private fun fallbackRecognizeQuestions(): List<RecognizeQuestionUi> {
    return listOf(
        RecognizeQuestionUi("fallback-recognize-happy", "Đây là cảm xúc gì?", R.drawable.happy_1, null, "happy"),
        RecognizeQuestionUi("fallback-recognize-sad", "Đây là cảm xúc gì?", R.drawable.sad_1, null, "sad"),
        RecognizeQuestionUi("fallback-recognize-angry", "Đây là cảm xúc gì?", R.drawable.angry_1, null, "angry"),
        RecognizeQuestionUi("fallback-recognize-fear", "Đây là cảm xúc gì?", R.drawable.fear_1, null, "fear"),
        RecognizeQuestionUi("fallback-recognize-surprise", "Đây là cảm xúc gì?", R.drawable.surprise_1, null, "surprise")
    )
}
