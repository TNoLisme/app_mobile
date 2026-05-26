package com.example.appmobile.ui.pages.game

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
fun EmotionMatchPage(level: Int = 1, onBack: () -> Unit, onOpenAssistant: () -> Unit = {}) {
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }

    var summaryData by remember(level) { mutableStateOf<LevelSummaryData?>(null) }

    val rounds = remember(questions.value) { questions.value.chunked(2) }
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
                    val correctRounds = rounds.count { round ->
                        round.all { q -> finalResults.find { it.questionId == q.questionId }?.isCorrect == true }
                    }
                    summaryData = LevelSummaryData(
                        passed = response.passed,
                        score = response.score / 2,
                        totalScore = 50,
                        accuracy = if (rounds.isNotEmpty()) (correctRounds.toFloat() / rounds.size * 100) else 0f,
                        correctCount = correctRounds,
                        totalQuestions = rounds.size
                    )
                    repository.invalidateProgressCache(GameUiCatalog.GAME_EMOTION_MATCH, userId)
                    val status = if (response.passed) "Đã qua level" else "Chưa qua level"
                    summary.value = "$status. Điểm: ${response.score / 2}/50."
                } else {
                    summary.value = "Hoàn thành. Điểm tạm tính: ${score.intValue}."
                }
            } catch (_: Exception) {
                summary.value = "Hoàn thành. Điểm tạm tính: ${score.intValue}."
            } finally {
                isSubmitting.value = false
            }
        }
    }

    LaunchedEffect(level, userId, replayCount.intValue) {
        val started = repository.startGame(GameUiCatalog.GAME_EMOTION_MATCH, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotionKey = normalizeEmotionForLearning(content.emotion ?: "vui vẻ")
                val emotionName = GameUiCatalog.emotionById(emotionKey)?.name ?: content.emotion ?: "vui vẻ"
                MatchQuestionUi(
                    questionId = content.contentId,
                    text = content.questionText ?: "",
                    correctName = content.correctAnswer ?: "Bé",
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

    GameScreenShell(
        contentMaxWidth = 800, onOpenAssistant = onOpenAssistant,
        scrollEnabled = false, bottomSpacerHeight = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            // TOP BAR
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                GameStatChip("Câu ${currentRoundIndex.intValue + 1}/${rounds.size}")
                Spacer(modifier = Modifier.width(8.dp))
                GameStatChip("Điểm ${score.intValue}")
            }

            Spacer(modifier = Modifier.height(4.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Cảm xúc đúng chỗ 🎭", style = MaterialTheme.typography.titleLarge, color = EgDesign.textPrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (summary.value != null) {
                GameLevelSummaryCard(
                    summaryData = summaryData,
                    summary = summary.value.orEmpty(),
                    onBack = onBack,
                    onReplay = { replayCount.intValue++ }
                )
                return@GameScreenShell
            }

            // HINT CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(2.dp, EgDesign.primary),
                colors = CardDefaults.cardColors(containerColor = EgDesign.card)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(28.dp).background(EgDesign.primary, shape = RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔊", fontSize = 14.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("💡 Tình huống:", fontWeight = FontWeight.Bold, color = EgDesign.textPrimary, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    currentRound.forEachIndexed { index, question ->
                        val annotatedText = buildAnnotatedString {
                            append("${index + 1}. ")
                            withStyle(SpanStyle(color = EgDesign.primary, fontWeight = FontWeight.Bold)) {
                                append(question.correctName)
                            }
                            append(" đang cảm thấy \"")
                            withStyle(SpanStyle(color = EgDesign.primary, fontWeight = FontWeight.Bold)) {
                                append(question.emotionName.lowercase())
                            }
                            append("\". Kéo tên ")
                            withStyle(SpanStyle(color = EgDesign.primary, fontWeight = FontWeight.Bold)) {
                                append(question.correctName)
                            }
                            append(" vào đúng ảnh.")
                        }
                        Text(annotatedText, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // FACES GRID (Drop Zones)
            // Đọc snapshot answers 1 lần cho toàn bộ grid
            val currentAnswers = answers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                                shape = MaterialTheme.shapes.medium,
                                elevation = CardDefaults.cardElevation(2.dp),
                                modifier = Modifier.fillMaxWidth().height(140.dp)
                            ) {
                                val cleanPath = question.imagePath.replace(Regex("^/fe/"), "/")
                                val imgUrl = "http://10.0.2.2:8000$cleanPath"
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = R.drawable.game_click_3),
                                    fallback = painterResource(id = R.drawable.game_click_3)
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
                                feedback.value == null -> if (droppedName != null) Color.White else Color.Gray.copy(alpha = 0.05f)
                                isCorrect == true -> Color(0xFFE8F5E9)
                                else -> Color(0xFFFFEBEE)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .onGloballyPositioned { coordinates ->
                                        dragDropState.registerDropZone(question.questionId, coordinates.boundsInRoot())
                                    }
                                    .background(color = bgColor, shape = RoundedCornerShape(28.dp))
                                    .border(
                                        width = if (droppedName != null) 2.dp else 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(28.dp)
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
                                            fontSize = 16.sp
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
                                    Text("Thả tên vào đây", color = Color.Gray.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CHIPS ROW HOẶC FEEDBACK
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (feedback.value != null) {
                    GameFeedbackCard(feedback.value.orEmpty())
                } else {
                    val allNamesInRound = remember(currentRound) { currentRound.map { it.correctName }.shuffled() }
                    // Lọc ra các tên chưa được đặt vào ô nào
                    val availableNames = allNamesInRound.filter { it !in currentAnswers.values }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        score.intValue += correctCount * 5
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = (allFilled || feedback.value != null) && !isSubmitting.value && learningEmotionId.value == null
            ) {
                Text(
                    when {
                        isSubmitting.value -> "Đang lưu..."
                        feedback.value == null -> "Kiểm tra kết quả"
                        currentRoundIndex.intValue >= rounds.lastIndex -> "Hoàn thành"
                        else -> "Câu tiếp theo"
                    },
                    fontSize = 18.sp,
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
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(if (isDragging) 8.dp else 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.primary.copy(alpha = 0.5f)),
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
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(name, fontWeight = FontWeight.Bold, color = EgDesign.textPrimary, fontSize = 16.sp)
        }
    }
}

private fun fallbackMatchQuestions(): List<MatchQuestionUi> {
    return listOf(
        MatchQuestionUi("1", "", "Bình", "happy", "Vui vẻ", ""),
        MatchQuestionUi("2", "", "Lan", "angry", "Tức giận", "")
    )
}
