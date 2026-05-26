package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

    var summaryData = remember(level) { mutableStateOf<LevelSummaryData?>(null) }

    val rounds = remember(questions.value) { questions.value.chunked(2) }
    val currentRoundIndex = remember(level, replayCount.intValue) { mutableIntStateOf(0) }
    val currentRound = rounds.getOrNull(currentRoundIndex.intValue) ?: emptyList()

    val answers = remember(currentRoundIndex.intValue) { mutableStateMapOf<String, String>() }
    val feedback = remember(currentRoundIndex.intValue) { mutableStateOf<String?>(null) }
    val roundResults = remember(currentRoundIndex.intValue) { mutableStateMapOf<String, Boolean>() }
    val dragDropState = remember(currentRoundIndex.intValue) { DragDropState() }

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
                    repository.invalidateProgressCache(GameUiCatalog.GAME_EMOTION_MATCH, userId)
                    val status = if (response.passed) "Đã qua level" else "Chưa qua level"
                    summary.value = "$status. Điểm: ${response.score}/50."
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
        summaryData.value = null
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
            // TOP BAR (Row 1: Back + Stats, Row 2: Title)
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
                    summaryData = summaryData.value,
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
                            append("\", hãy kéo thẻ tên để biết đâu là ")
                            withStyle(SpanStyle(color = EgDesign.primary, fontWeight = FontWeight.Bold)) {
                                append(question.correctName)
                            }
                            append(".")
                        }
                        Text(annotatedText, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // FACES GRID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                currentRound.forEach { question ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth().height(120.dp) // Fixed height to prevent shifting
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
                        Spacer(modifier = Modifier.height(8.dp))

                        val droppedName = answers[question.questionId]

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .onGloballyPositioned { coordinates ->
                                    dragDropState.registerDropZone(question.questionId, coordinates.boundsInRoot())
                                }
                                .background(
                                    color = if (droppedName != null) EgDesign.primary.copy(alpha = 0.1f) else Color.Transparent,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (droppedName != null) EgDesign.primary else Color.Gray.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.medium
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (droppedName != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(droppedName, fontWeight = FontWeight.Bold, color = EgDesign.primary)
                                    // Removed the correctness check from here so it doesn't shift layout
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Xóa",
                                        modifier = Modifier.size(20.dp).clickable { answers.remove(question.questionId) },
                                        tint = EgDesign.textSecondary
                                    )
                                }
                            } else {
                                Text("Thả tên vào đây", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CHIPS ROW OR FEEDBACK ROW
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (feedback.value != null) {
                    GameFeedbackCard(feedback.value.orEmpty())
                } else {
                    val answersMap = answers.toMap()
                    val allNamesInRound = remember(currentRound) { currentRound.map { it.correctName }.shuffled() }
                    val availableNames = allNamesInRound.filter { it !in answersMap.values }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        availableNames.forEach { name ->
                            DraggableNameChip(
                                name = name,
                                dragDropState = dragDropState,
                                enabled = true,
                                onDropped = { zoneId ->
                                    if (answers[zoneId] == null) {
                                        answers[zoneId] = name
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f)) // Push everything below to bottom

            // INSTRUCTION
            Text(
                "🎴 Kéo thẻ cảm xúc vào ô phía dưới mỗi khuôn mặt",
                color = EgDesign.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // ACTION BUTTON
            Button(
                onClick = {
                    if (feedback.value == null) {
                        var correctCount = 0
                        currentRound.forEach { q ->
                            val isCorrect = answers[q.questionId] == q.correctName
                            roundResults[q.questionId] = isCorrect
                            if (isCorrect) correctCount++

                            if (!isCorrect) {
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
                                isCorrect = isCorrect,
                                responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt()
                            )
                        }
                        if (correctCount == currentRound.size) score.intValue += 10
                        feedback.value = if (correctCount == currentRound.size) "Chính xác tuyệt đối!" else "Chưa đúng hoàn toàn. Hãy xem lại đáp án."
                        return@Button
                    }

                    if (currentRoundIndex.intValue >= rounds.lastIndex) {
                        finishLevel(results.value)
                    } else {
                        currentRoundIndex.intValue++
                        answers.clear()
                        roundResults.clear()
                        feedback.value = null
                        questionStartMs.value = System.currentTimeMillis()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = answers.size == currentRound.size && !isSubmitting.value && learningEmotionId.value == null
            ) {
                Text(
                    when {
                        isSubmitting.value -> "Đang lưu..."
                        feedback.value == null -> "Trả lời"
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
    enabled: Boolean,
    onDropped: (String) -> Unit
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var globalPosition by remember { mutableStateOf(Offset.Zero) }
    var chipSize by remember { mutableStateOf(IntSize.Zero) }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(if (isDragging) 8.dp else 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .onGloballyPositioned {
                globalPosition = it.positionInRoot()
                chipSize = it.size
            }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
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
                                    size = androidx.compose.ui.geometry.Size(chipSize.width.toFloat(), chipSize.height.toFloat())
                                )
                                val dropZoneId = dragDropState.findDropZone(chipRect)
                                if (dropZoneId != null) {
                                    onDropped(dropZoneId)
                                }
                                dragOffset = Offset.Zero
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffset = Offset.Zero
                            }
                        )
                    }
                } else Modifier
            )
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
