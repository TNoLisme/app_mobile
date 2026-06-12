package com.example.appmobile.ui.pages.game

import androidx.activity.compose.BackHandler
import androidx.compose.ui.layout.ContentScale
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

private data class DetectiveQuestionUi(
    val questionId: String,
    val story: String,
    val correctEmotion: String,
    val optionEmotionIds: List<String> = GameUiCatalog.emotions.map { it.id },
    val explanation: String? = null,
    val mediaPath: String? = null
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
    val showExitConfirm = remember(level) { mutableStateOf(false) }
    val pendingResumeCheckpoint = remember(level) { mutableStateOf<JSONObject?>(null) }
    val resumeFromCheckpoint = remember(level) { mutableStateOf<Boolean?>(null) }
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
                    repository.invalidateProgressCache(GameUiCatalog.GAME_DETECTIVE, userId)
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
                clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_DETECTIVE, level)
                val completedScore = summaryData.value?.score ?: score.intValue
                saveLocalUnlockedLevel(
                    context = context,
                    userId = userId,
                    gameId = GameUiCatalog.GAME_DETECTIVE,
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
            gameId = GameUiCatalog.GAME_DETECTIVE,
            level = level,
            sessionId = sessionId.value,
            score = score.intValue,
            currentIndex = checkpointIndex(),
            maxErrors = maxErrors.intValue,
            results = results.value,
            questions = detectiveQuestionsToJson(questions.value)
        )
        showExitConfirm.value = false
        onBack()
    }

    fun exitWithoutSaving() {
        clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_DETECTIVE, level)
        showExitConfirm.value = false
        onBack()
    }

    fun requestExit() {
        val hasProgress = summary.value == null && (results.value.isNotEmpty() || currentIndex.intValue > 0)
        if (hasProgress) showExitConfirm.value = true else onBack()
    }

    LaunchedEffect(level, userId, replayCount.intValue, resumeFromCheckpoint.value) {
        val checkpoint = loadClickGameCheckpointJson(context, userId, GameUiCatalog.GAME_DETECTIVE, level)
        if (checkpoint != null && resumeFromCheckpoint.value == null) {
            pendingResumeCheckpoint.value = checkpoint
            return@LaunchedEffect
        }
        if (checkpoint != null) {
            if (resumeFromCheckpoint.value == false) {
                clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_DETECTIVE, level)
            } else {
            val restoredQuestions = detectiveQuestionsFromJson(checkpoint.optJSONArray("questions"))
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
        }
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
                    explanation = content.explanation,
                    mediaPath = content.mediaPath
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

    BackHandler(enabled = summary.value == null) {
        requestExit()
    }

    val question = questions.value[currentIndex.intValue % questions.value.size]
    val hintShown = remember(currentIndex.intValue) { mutableStateOf(false) }
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
                            clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_DETECTIVE, level)
                            onBack()
                        },
                        onReplay = {
                            clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_DETECTIVE, level)
                            replayCount.intValue++
                        },
                        modifier = Modifier.fillMaxWidth()
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
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GameQuestionMedia(
                                    mediaPath = question.mediaPath,
                                    fallbackRes = R.drawable.game_click_4,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
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
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (feedback.value != null) {
                            GameFeedbackCard(feedback.value.orEmpty())
                        } else if (!question.explanation.isNullOrBlank()) {
                            if (hintShown.value) {
                                GameHintCard(text = question.explanation)
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
                                    val isCorrect = selected == question.correctEmotion
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
                                        usedHint = hintShown.value
                                    )
                                    results.value = updatedResults
                                    score.intValue = scoreFromCorrectAnswers(
                                        updatedResults.count { it.isCorrect },
                                        questions.value.size
                                    )
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
                        repository.resetReviewEmotions(GameUiCatalog.GAME_DETECTIVE, userId, listOf(emotion))
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
    pendingResumeCheckpoint.value?.let { checkpoint ->
        val totalCount = checkpoint.optJSONArray("questions")?.length() ?: 1
        val answeredCount = checkpoint.optJSONArray("results")?.length() ?: 0
        ClickGameResumeDialog(
            answeredCount = answeredCount,
            totalCount = totalCount,
            onContinue = {
                pendingResumeCheckpoint.value = null
                resumeFromCheckpoint.value = true
            },
            onRestart = {
                clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_DETECTIVE, level)
                pendingResumeCheckpoint.value = null
                resumeFromCheckpoint.value = false
            }
        )
    }
}

private fun detectiveQuestionsToJson(questions: List<DetectiveQuestionUi>): JSONArray {
    return JSONArray().apply {
        questions.forEach { question ->
            put(JSONObject().apply {
                put("question_id", question.questionId)
                put("story", question.story)
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

private fun detectiveQuestionsFromJson(array: JSONArray?): List<DetectiveQuestionUi> {
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
                DetectiveQuestionUi(
                    questionId = item.optString("question_id"),
                    story = item.optString("story", "Cảm xúc nào đang ẩn giấu?"),
                    correctEmotion = emotion,
                    optionEmotionIds = options.ifEmpty { GameUiCatalog.emotions.map { it.id } },
                    explanation = item.optString("explanation").takeIf { it.isNotBlank() && it != "null" },
                    mediaPath = item.optString("media_path").takeIf { it.isNotBlank() && it != "null" }
                )
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
