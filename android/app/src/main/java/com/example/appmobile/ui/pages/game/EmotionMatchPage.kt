package com.example.appmobile.ui.pages.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.audio.EgSoundEffect
import com.example.appmobile.ui.audio.EgSoundEffects
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class MatchQuestionUi(
    val questionId: String,
    val text: String,
    val correctName: String,
    val emotionKey: String,
    val emotionName: String,
    val imagePath: String
)

class DragDropState {
    val dropZones = mutableMapOf<String, Rect>()
    fun registerDropZone(id: String, rect: Rect) {
        dropZones[id] = rect
    }
    fun findDropZone(chipRect: Rect): String? {
        var bestZone: String? = null
        var maxArea = 0f
        for ((id, zoneRect) in dropZones) {
            if (zoneRect.overlaps(chipRect)) {
                val intersection = zoneRect.intersect(chipRect)
                val area = intersection.width * intersection.height
                if (area > maxArea) {
                    maxArea = area
                    bestZone = id
                }
            }
        }
        return bestZone
    }
}

@Composable
fun EmotionMatchPage(
    level: Int = 1,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit = {},
    onGameCompleted: (Int) -> Unit = {}
) {
    val score = remember(level) { mutableIntStateOf(0) }
    val questions = remember(level) { mutableStateOf(fallbackMatchQuestions()) }
    val sessionId = remember(level) { mutableStateOf<String?>(null) }
    val results = remember(level) { mutableStateOf<List<AnswerResultDto>>(emptyList()) }
    val summary = remember(level) { mutableStateOf<String?>(null) }
    val replayCount = remember { mutableIntStateOf(0) }
    val isSubmitting = remember(level) { mutableStateOf(false) }
    val questionStartMs = remember(level) { mutableStateOf(System.currentTimeMillis()) }
    val maxErrors = remember(level) { mutableIntStateOf(3) }
    val emotionErrors = remember(level) { mutableStateMapOf<String, Int>() }
    val learnedEmotions = remember(level) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level) { mutableStateOf<String?>(null) }
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

    var summaryData by remember(level) { mutableStateOf<LevelSummaryData?>(null) }

    val chunkSize = if (level <= 4) 2 else 3
    val rounds = remember(questions.value, chunkSize) { questions.value.chunked(chunkSize) }
    val currentRoundIndex = remember(level, replayCount.intValue) { mutableIntStateOf(0) }
    val currentRound = rounds.getOrNull(currentRoundIndex.intValue) ?: emptyList()

    // State cho round hiện tại
    // Key trên cả questions.value để khi backend questions đến (thay thế fallback),
    // toàn bộ state được tạo lại sạch, tránh lẫn ID cũ/mới
    var answers by remember(currentRoundIndex.intValue, questions.value) { mutableStateOf(mapOf<String, String>()) }
    val feedback = remember(currentRoundIndex.intValue, questions.value) { mutableStateOf<String?>(null) }
    val roundResults = remember(currentRoundIndex.intValue, questions.value) { mutableStateMapOf<String, Boolean>() }
    val dragDropState = remember(currentRoundIndex.intValue, questions.value) { DragDropState() }

    // Không dùng remember cho callbacks — tránh stale closure
    fun addAnswer(zoneId: String, name: String) {
        val cur = answers
        if (name !in cur.values && cur[zoneId] == null) {
            answers = cur + (zoneId to name)
        }
    }

    fun removeAnswer(zoneId: String) {
        answers = answers - zoneId
    }

    fun finishLevel(finalResults: List<AnswerResultDto>) {
        if (isSubmitting.value || summary.value != null) return
        scope.launch {
            try {
                isSubmitting.value = true
                val response = sessionId.value?.let {
                    repository.endLevel(it, finalResults, learnedEmotions.distinct())
                }
                if (response != null) {
                    summaryData = LevelSummaryData(
                        passed = response.passed,
                        score = response.score,
                        totalScore = 100,
                        accuracy = response.accuracy,
                        correctCount = finalResults.count { it.isCorrect },
                        totalQuestions = finalResults.size
                    )
                    repository.invalidateProgressCache(GameUiCatalog.GAME_EMOTION_MATCH, userId)
                    val status = if (response.passed) "Đã qua level" else "Chưa qua level"
                    summary.value = "$status. Điểm: ${response.score}/100."
                } else {
                    val correctCount = finalResults.count { it.isCorrect }
                    val localScore = scoreFromCorrectAnswers(correctCount, finalResults.size)
                    summaryData = LevelSummaryData(
                        passed = localScore >= 80,
                        score = localScore,
                        totalScore = 100,
                        accuracy = localScore.toFloat(),
                        correctCount = correctCount,
                        totalQuestions = finalResults.size
                    )
                    summary.value = "Hoàn thành. Điểm tạm tính: $localScore/100."
                }
            } catch (_: Exception) {
                val correctCount = finalResults.count { it.isCorrect }
                val localScore = scoreFromCorrectAnswers(correctCount, finalResults.size)
                summaryData = LevelSummaryData(
                    passed = localScore >= 80,
                    score = localScore,
                    totalScore = 100,
                    accuracy = localScore.toFloat(),
                    correctCount = correctCount,
                    totalQuestions = finalResults.size
                )
                summary.value = "Hoàn thành. Điểm tạm tính: $localScore/100."
            } finally {
                clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_EMOTION_MATCH, level)
                val completedScore = summaryData?.score ?: score.intValue
                saveLocalUnlockedLevel(
                    context = context,
                    userId = userId,
                    gameId = GameUiCatalog.GAME_EMOTION_MATCH,
                    completedLevel = level,
                    score = completedScore
                )
                onGameCompleted(completedScore)
                isSubmitting.value = false
            }
        }
    }

    LaunchedEffect(level, userId, replayCount.intValue, resumeFromCheckpoint.value) {
        val checkpoint = loadClickGameCheckpointJson(context, userId, GameUiCatalog.GAME_EMOTION_MATCH, level)
        if (checkpoint != null && resumeFromCheckpoint.value == null) {
            pendingResumeCheckpoint.value = checkpoint
            return@LaunchedEffect
        }
        if (checkpoint != null) {
            if (resumeFromCheckpoint.value == false) {
                clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_EMOTION_MATCH, level)
            } else {
            val restoredQuestions = matchQuestionsFromJson(checkpoint.optJSONArray("questions"))
            if (restoredQuestions.isNotEmpty()) {
                questions.value = restoredQuestions
                sessionId.value = checkpoint.optString("session_id").takeIf { it.isNotBlank() && it != "null" }
                maxErrors.intValue = checkpoint.optInt("max_errors", 3).coerceAtLeast(1)
                score.intValue = checkpoint.optInt("score", 0).coerceIn(0, 100)
                results.value = answerResultsFromCheckpoint(checkpoint)
                summary.value = null
                summaryData = null
                emotionErrors.clear()
                learnedEmotions.clear()
                learningEmotionId.value = null
                val restoredRoundCount = restoredQuestions.chunked(chunkSize).size.coerceAtLeast(1)
                currentRoundIndex.intValue = checkpoint.optInt("current_index", 0).coerceIn(0, restoredRoundCount - 1)
                questionStartMs.value = System.currentTimeMillis()
                return@LaunchedEffect
            }
            }
        }
        val started = repository.startGame(GameUiCatalog.GAME_EMOTION_MATCH, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotionKey = normalizeEmotionForLearning(content.emotion ?: content.correctAnswer ?: "happy")
                val emotionName = GameUiCatalog.emotionById(emotionKey)?.name ?: content.emotion ?: "Vui vẻ"
                MatchQuestionUi(
                    questionId = content.contentId,
                    text = content.questionText ?: "",
                    correctName = matchTokenLabel(content.correctAnswer, emotionKey),
                    emotionKey = emotionKey,
                    emotionName = emotionName,
                    imagePath = content.mediaPath ?: ""
                )
            }
            .orEmpty()

        questions.value = backendQuestions.ifEmpty { fallbackMatchQuestions() }
        currentRoundIndex.intValue = 0
        score.intValue = 0
        results.value = emptyList()
        summary.value = null
        summaryData = null
        emotionErrors.clear()
        learnedEmotions.clear()
        learningEmotionId.value = null
        questionStartMs.value = System.currentTimeMillis()
    }

    fun checkpointIndex(): Int {
        return if (feedback.value != null) {
            (currentRoundIndex.intValue + 1).coerceAtMost((rounds.size - 1).coerceAtLeast(0))
        } else {
            currentRoundIndex.intValue
        }
    }

    fun saveAndExit() {
        saveClickGameCheckpoint(
            context = context,
            userId = userId,
            gameId = GameUiCatalog.GAME_EMOTION_MATCH,
            level = level,
            sessionId = sessionId.value,
            score = score.intValue,
            currentIndex = checkpointIndex(),
            maxErrors = maxErrors.intValue,
            results = results.value,
            questions = matchQuestionsToJson(questions.value)
        )
        showExitConfirm.value = false
        onBack()
    }

    fun exitWithoutSaving() {
        clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_EMOTION_MATCH, level)
        showExitConfirm.value = false
        onBack()
    }

    fun requestExit() {
        val hasProgress = summary.value == null && (results.value.isNotEmpty() || currentRoundIndex.intValue > 0)
        if (hasProgress) showExitConfirm.value = true else onBack()
    }

    BackHandler(enabled = summary.value == null) {
        requestExit()
    }

    GameScreenShell(
        contentMaxWidth = 800, onOpenAssistant = onOpenAssistant,
        scrollEnabled = false, bottomSpacerHeight = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameHeader(
                title = "Cảm xúc đúng chỗ",
                level = level,
                currentQuestion = currentRoundIndex.intValue + 1,
                totalQuestions = rounds.size,
                score = score.intValue,
                onBack = { requestExit() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (summary.value != null) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GameLevelSummaryCard(
                        summaryData = summaryData,
                        summary = summary.value.orEmpty(),
                        onBack = {
                            clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_EMOTION_MATCH, level)
                            onBack()
                        },
                        onReplay = {
                            clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_EMOTION_MATCH, level)
                            replayCount.intValue++
                        }
                    )
                }
                return@GameScreenShell
            }

            ClickGameInstructionCard(
                title = "Kéo thẻ vào đúng ảnh",
                description = "Đọc tình huống rồi đặt thẻ phù hợp vào khung.",
                iconKey = "puzzle"
            ) {
                    currentRound.forEachIndexed { index, question ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${index + 1}.",
                                color = EgDesign.blue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                            Text(
                                text = question.text,
                                color = EgDesign.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                lineHeight = 20.sp
                            )
                        }
                    }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // FACES GRID (Drop Zones)
            // Đọc snapshot answers 1 lần cho toàn bộ grid
            val currentAnswers = answers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentRound.forEach { question ->
                    key(question.questionId) {
                        val droppedName = currentAnswers[question.questionId]
                        val isCorrect = roundResults[question.questionId]

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Card(
                                shape = RoundedCornerShape(EgDesign.radiusLarge),
                                elevation = CardDefaults.cardElevation(1.dp),
                                colors = CardDefaults.cardColors(containerColor = EgDesign.card),
                                border = BorderStroke(1.dp, EgDesign.cardBorder),
                                modifier = Modifier.fillMaxWidth().height(142.dp)
                            ) {
                                GameQuestionMedia(
                                    mediaPath = question.imagePath,
                                    fallbackRes = matchSceneResource(question.emotionKey),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // Màu sắc dựa trên trạng thái
                            val borderColor = when {
                                feedback.value == null -> if (droppedName != null) EgDesign.primary else Color.Gray.copy(alpha = 0.3f)
                                isCorrect == true -> Color(0xFF4CAF50)
                                else -> Color(0xFFF44336)
                            }
                            val bgColor = when {
                                feedback.value == null -> if (droppedName != null) EgDesign.card else EgDesign.cardSoft
                                isCorrect == true -> EgDesign.success.copy(alpha = 0.12f)
                                else -> EgDesign.danger.copy(alpha = 0.12f)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .onGloballyPositioned { coordinates ->
                                        dragDropState.registerDropZone(question.questionId, coordinates.boundsInRoot())
                                    }
                                    .background(color = bgColor, shape = RoundedCornerShape(EgDesign.radiusMedium))
                                    .border(
                                        width = if (droppedName != null) 2.dp else 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(EgDesign.radiusMedium)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (droppedName != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    ) {
                                        Text(
                                            droppedName,
                                            fontWeight = FontWeight.Bold,
                                            color = if (feedback.value == null) EgDesign.textPrimary else borderColor,
                                            fontSize = 15.sp
                                        )
                                        if (feedback.value == null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Xóa",
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
                                                    .clickable { removeAnswer(question.questionId) },
                                                tint = EgDesign.textSecondary
                                            )
                                        }
                                    }
                                } else {
                                    Text("Thả thẻ vào đây", color = EgDesign.textSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CHIPS ROW HOẶC FEEDBACK
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (feedback.value != null) {
                    GameFeedbackCard(feedback.value.orEmpty())
                } else {
                    val allNamesInRound = remember(currentRound) { currentRound.map { it.correctName }.shuffled() }
                    // Lọc ra các tên chưa được đặt vào ô nào
                    val availableNames = allNamesInRound.filter { it !in currentAnswers.values }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        availableNames.forEach { name ->
                            key(name) {
                                DraggableNameChip(
                                    name = name,
                                    dragDropState = dragDropState,
                                    onDropped = { zoneId -> addAnswer(zoneId, name) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ACTION BUTTON
            val allFilled = currentAnswers.size >= currentRound.size && currentRound.isNotEmpty()
            Button(
                onClick = {
                    if (feedback.value == null) {
                        var correctCount = 0
                        currentRound.forEach { q ->
                            val correct = answers[q.questionId] == q.correctName
                            roundResults[q.questionId] = correct
                            if (correct) correctCount++

                            if (!correct) {
                                val emotionKey = q.emotionKey
                                emotionErrors[emotionKey] = (emotionErrors[emotionKey] ?: 0) + 1
                                if ((emotionErrors[emotionKey] ?: 0) >= maxErrors.intValue && emotionKey !in learnedEmotions) {
                                    learnedEmotions.add(emotionKey)
                                    learningEmotionId.value = emotionKey
                                }
                            }

                            results.value = results.value + AnswerResultDto(
                                questionId = q.questionId,
                                answer = answers[q.questionId] ?: "",
                                isCorrect = correct,
                                responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt()
                            )
                        }
                        EgSoundEffects.play(
                            if (correctCount == currentRound.size) EgSoundEffect.Correct else EgSoundEffect.Wrong
                        )
                        score.intValue = scoreFromCorrectAnswers(
                            results.value.count { it.isCorrect },
                            questions.value.size
                        )
                        feedback.value = if (correctCount == currentRound.size) "Chính xác tuyệt đối!" else "Chưa đúng hoàn toàn. Hãy xem lại đáp án."
                        return@Button
                    }

                    if (currentRoundIndex.intValue >= rounds.lastIndex) {
                        finishLevel(results.value)
                    } else {
                        currentRoundIndex.intValue++
                        answers = emptyMap()
                        roundResults.clear()
                        feedback.value = null
                        questionStartMs.value = System.currentTimeMillis()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = (allFilled || feedback.value != null) && !isSubmitting.value && learningEmotionId.value == null,
                shape = RoundedCornerShape(EgDesign.pillRadius),
                colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary, contentColor = Color.White)
            ) {
                Text(
                    when {
                        isSubmitting.value -> "Đang lưu..."
                        feedback.value == null -> "Kiểm tra kết quả"
                        currentRoundIndex.intValue >= rounds.lastIndex -> "Hoàn thành"
                        else -> "Câu tiếp theo"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        EmotionLearningDialog(
            emotionId = learningEmotionId.value,
            onDismiss = {
                val emotion = learningEmotionId.value
                learningEmotionId.value = null
                if (emotion != null) {
                    emotionErrors[emotion] = 0
                    scope.launch {
                        try {
                            repository.resetReviewEmotions(GameUiCatalog.GAME_EMOTION_MATCH, userId, listOf(emotion))
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
                    clearClickGameCheckpoint(context, userId, GameUiCatalog.GAME_EMOTION_MATCH, level)
                    pendingResumeCheckpoint.value = null
                    resumeFromCheckpoint.value = false
                }
            )
        }
    }
}

private fun matchQuestionsToJson(questions: List<MatchQuestionUi>): JSONArray {
    return JSONArray().apply {
        questions.forEach { question ->
            put(JSONObject().apply {
                put("question_id", question.questionId)
                put("text", question.text)
                put("correct_name", question.correctName)
                put("emotion_key", question.emotionKey)
                put("emotion_name", question.emotionName)
                put("image_path", question.imagePath)
            })
        }
    }
}

private fun matchQuestionsFromJson(array: JSONArray?): List<MatchQuestionUi> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                MatchQuestionUi(
                    questionId = item.optString("question_id"),
                    text = item.optString("text"),
                    correctName = item.optString("correct_name"),
                    emotionKey = normalizeEmotionForLearning(item.optString("emotion_key")),
                    emotionName = item.optString("emotion_name"),
                    imagePath = item.optString("image_path")
                )
            )
        }
    }
}

@Composable
fun DraggableNameChip(
    name: String,
    dragDropState: DragDropState,
    onDropped: (String) -> Unit
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var globalPosition by remember { mutableStateOf(Offset.Zero) }
    var chipSize by remember { mutableStateOf(IntSize.Zero) }

    // rememberUpdatedState giữ reference luôn mới nhất
    // ngay cả khi pointerInput không re-execute
    val currentOnDropped by rememberUpdatedState(onDropped)
    val currentDragDropState by rememberUpdatedState(dragDropState)

    Card(
        shape = RoundedCornerShape(EgDesign.pillRadius),
        elevation = CardDefaults.cardElevation(if (isDragging) 6.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        border = BorderStroke(1.dp, if (isDragging) EgDesign.primary else EgDesign.cardBorder),
        modifier = Modifier
            .onGloballyPositioned {
                globalPosition = it.positionInRoot()
                chipSize = it.size
            }
            .zIndex(if (isDragging) 10f else 1f)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                scaleX = if (isDragging) 1.1f else 1.0f
                scaleY = if (isDragging) 1.1f else 1.0f
            }
            // Key trên name: khi Compose tái sử dụng node cho tên khác,
            // pointerInput sẽ được tạo lại với closure mới
            .pointerInput(name) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    },
                    onDragEnd = {
                        isDragging = false
                        val chipRect = Rect(
                            offset = globalPosition + dragOffset,
                            size = androidx.compose.ui.geometry.Size(
                                chipSize.width.toFloat(),
                                chipSize.height.toFloat()
                            )
                        )
                        val dropZoneId = currentDragDropState.findDropZone(chipRect)
                        if (dropZoneId != null) {
                            currentOnDropped(dropZoneId)
                        }
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    }
                )
            }
    ) {
        Box(modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp)) {
            Text(name, fontWeight = FontWeight.Bold, color = EgDesign.textPrimary, fontSize = 15.sp)
        }
    }
}

private fun fallbackMatchQuestions(): List<MatchQuestionUi> {
    return listOf(
        MatchQuestionUi("fallback-match-happy", "An được cô giáo khen vì biết chia sẻ đồ chơi. Bạn nào đang vui?", "An", "happy", "Vui vẻ", ""),
        MatchQuestionUi("fallback-match-sad", "Mai làm rơi cây kem yêu thích xuống đất. Bạn nào đang buồn?", "Mai", "sad", "Buồn bã", ""),
        MatchQuestionUi("fallback-match-angry", "Bình đang xếp tháp thì bạn khác chạy tới làm đổ. Bạn nào đang tức giận?", "Bình", "angry", "Tức giận", ""),
        MatchQuestionUi("fallback-match-fear", "Minh nghe tiếng sấm rất to và ôm chặt mẹ. Bạn nào đang sợ hãi?", "Minh", "fear", "Sợ hãi", ""),
        MatchQuestionUi("fallback-match-surprise", "Nam mở hộp quà và thấy món đồ chơi bất ngờ. Bạn nào đang ngạc nhiên?", "Nam", "surprise", "Ngạc nhiên", ""),
        MatchQuestionUi("fallback-match-disgust", "Lan ngửi thấy mùi rác trong sân trường và nhăn mũi. Bạn nào đang ghê tởm?", "Lan", "disgust", "Ghê tởm", "")
    )
}

private fun matchTokenLabel(rawAnswer: String?, emotionKey: String): String {
    val raw = rawAnswer?.trim().orEmpty()
    val lower = raw.lowercase()
    return when {
        raw.isBlank() -> GameUiCatalog.emotionById(emotionKey)?.name ?: emotionKey
        lower in setOf("happy", "sad", "angry", "fear", "surprise", "disgust") ->
            GameUiCatalog.emotionById(normalizeEmotionForLearning(raw))?.name ?: raw
        else -> raw
    }
}

private fun matchSceneResource(emotionKey: String): Int {
    return when (normalizeEmotionForLearning(emotionKey)) {
        "happy" -> R.drawable.learn_scene_happy
        "sad" -> R.drawable.learn_scene_sad
        "angry" -> R.drawable.learn_scene_angry
        "fear" -> R.drawable.learn_scene_fear
        "surprise" -> R.drawable.learn_scene_surprise
        "disgust" -> R.drawable.learn_scene_disgust
        else -> R.drawable.game_click_3
    }
}
