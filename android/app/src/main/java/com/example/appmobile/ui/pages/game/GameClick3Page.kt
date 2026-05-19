package com.example.appmobile.ui.pages.game

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

private const val QUESTIONS_PER_LEVEL = 5
private const val TOTAL_ROUNDS = 5

private data class MatchQuestionUi(
    val questionId: String,
    val displayName: String,
    val imageUrl: String?,
    val prompt: String,
    val correctEmotion: String
)

private fun getNumQuestionsPerRound(level: Int): Int {
    return when {
        level <= 2 -> 2
        level <= 4 -> 3
        level <= 6 -> 4
        else -> 5
    }
}

@Composable
fun GameClick3Page(level: Int = 1, onBack: () -> Unit, onOpenAssistant: () -> Unit = {}) {
    val currentIndex = remember(level) { mutableIntStateOf(0) }
    val score = remember(level) { mutableIntStateOf(0) }
    val questions = remember(level) { mutableStateOf<List<MatchQuestionUi>>(emptyList()) }
    val roundQuestions = remember(level) { mutableStateOf<List<MatchQuestionUi>>(emptyList()) }
    val remainingQuestions = remember(level) { mutableStateListOf<MatchQuestionUi>() }
    val sessionId = remember(level) { mutableStateOf<String?>(null) }
    val results = remember(level) { mutableStateOf<List<AnswerResultDto>>(emptyList()) }
    val summary = remember(level) { mutableStateOf<String?>(null) }
    val isSubmitting = remember(level) { mutableStateOf(false) }
    val questionStartMs = remember(level) { mutableLongStateOf(System.currentTimeMillis()) }
    val maxErrors = remember(level) { mutableIntStateOf(3) }
    val emotionErrors = remember(level) { mutableStateMapOf<String, Int>() }
    val learnedEmotions = remember(level) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val feedback = remember(level) { mutableStateOf<String?>(null) }
    val assignments = remember(level) { mutableStateMapOf<String, String>() }
    val dropZones = remember(level) { mutableStateMapOf<String, Rect>() }
    val nameChipBounds = remember(level) { mutableStateMapOf<String, Rect>() }
    val draggingName = remember(level) { mutableStateOf<String?>(null) }
    val draggingOffset = remember(level) { mutableStateOf(Offset.Zero) }
    val selectedName = remember(level) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }

    fun assignedNames(): Set<String> = assignments.values.toSet()

    fun pickQuestionsForCurrentRound(): List<MatchQuestionUi> {
        if (remainingQuestions.isEmpty()) return emptyList()

        val maxPerRound = getNumQuestionsPerRound(level)
        val num = minOf(maxPerRound, remainingQuestions.size)
        val pool = remainingQuestions.shuffled(Random(level * 31L + currentIndex.intValue))

        val usedNames = mutableSetOf<String>()
        val usedEmotions = mutableSetOf<String>()
        val selected = mutableListOf<MatchQuestionUi>()

        for (question in pool) {
            if (selected.size >= num) break

            val name = question.displayName.trim().lowercase()
            val emotion = question.correctEmotion.trim().lowercase()

            if (name.isNotBlank() && name in usedNames) continue
            if (emotion.isNotBlank() && emotion in usedEmotions) continue

            selected.add(question)
            if (name.isNotBlank()) usedNames.add(name)
            if (emotion.isNotBlank()) usedEmotions.add(emotion)
        }

        if (selected.size < num) {
            for (question in pool) {
                if (selected.size >= num) break
                if (selected.any { it.questionId == question.questionId }) continue
                selected.add(question)
            }
        }

        val selectedIds = selected.map { it.questionId }.toSet()
        remainingQuestions.removeAll { it.questionId in selectedIds }
        return selected
    }

    fun questionImage(question: MatchQuestionUi): String? {
        val raw = question.imageUrl?.trim().orEmpty()
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("/fe/assets/") -> "file:///android_asset/${raw.removePrefix("/")}"
            raw.startsWith("fe/assets/") -> "file:///android_asset/$raw"
            else -> NetworkClient.backendUrl(raw)
        }
    }

    @Composable
    fun questionAssetBitmap(question: MatchQuestionUi): ImageBitmap? {
        val raw = question.imageUrl?.trim().orEmpty()
        val assetPath = when {
            raw.startsWith("/fe/assets/") -> raw.removePrefix("/")
            raw.startsWith("fe/assets/") -> raw
            else -> null
        } ?: return null

        return remember(assetPath) {
            runCatching {
                context.assets.open(assetPath).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    fun finishLevel(finalResults: List<AnswerResultDto>) {
        if (isSubmitting.value || summary.value != null) return
        scope.launch {
            isSubmitting.value = true
            val response = sessionId.value?.let {
                repository.endLevel(it, finalResults, learnedEmotions.distinct())
            }
            summary.value = if (response != null) {
                val status = if (response.passed) "Đã qua cấp độ" else "Chưa qua cấp độ"
                "$status. Điểm: ${response.score}/50."
            } else {
                "Không thể lưu kết quả lên server. Kiểm tra kết nối và thử lại."
            }
            response?.reviewEmotionsToLearn
                ?.firstOrNull()
                ?.let { learningEmotionId.value = normalizeEmotionForLearning(it) }
            isSubmitting.value = false
        }
    }

    LaunchedEffect(level, userId) {
        val started = runCatching {
            repository.startGame(GameUiCatalog.GAME_EMOTION_MATCH, userId, level)
        }.getOrNull()

        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3

        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val displayName = (content.correctAnswer ?: content.emotion ?: "").trim()
                if (displayName.isBlank()) return@mapNotNull null

                MatchQuestionUi(
                    questionId = content.contentId,
                    displayName = displayName,
                    imageUrl = content.mediaPath,
                    prompt = content.questionText?.ifBlank { "Kéo thẻ tên vào đúng ảnh" }
                        ?: "Kéo thẻ tên vào đúng ảnh",
                    correctEmotion = normalizeEmotionForLearning(
                        content.emotion ?: content.correctAnswer ?: displayName
                    )
                )
            }
            .orEmpty()

        questions.value = backendQuestions
        remainingQuestions.clear()
        remainingQuestions.addAll(backendQuestions.shuffled(Random(level * 41L)))
        roundQuestions.value = pickQuestionsForCurrentRound()
        currentIndex.intValue = 0
        score.intValue = 0
        results.value = emptyList()
        summary.value = null
        emotionErrors.clear()
        learnedEmotions.clear()
        learningEmotionId.value = null
        feedback.value = null
        assignments.clear()
        dropZones.clear()
        nameChipBounds.clear()
        draggingName.value = null
        draggingOffset.value = Offset.Zero
        selectedName.value = null
        questionStartMs.longValue = System.currentTimeMillis()
    }

    if (roundQuestions.value.isEmpty()) return

    val currentRoundQuestions = roundQuestions.value
    val availableNames = currentRoundQuestions
        .map { it.displayName }
        .filter { it !in assignedNames() }
        .shuffled(Random(level * 37L + currentIndex.intValue))

    fun placeDraggedName(questionId: String) {
        val name = draggingName.value ?: return
        if (assignments.containsValue(name)) return
        assignments[questionId] = name
        draggingName.value = null
        draggingOffset.value = Offset.Zero
        selectedName.value = null
    }

    fun placeSelectedName(questionId: String) {
        val name = selectedName.value ?: return
        if (assignments.containsValue(name)) return
        assignments[questionId] = name
        selectedName.value = null
    }

    fun handleSubmitRound() {
        val now = System.currentTimeMillis()
        if (feedback.value == null) {
            if (currentRoundQuestions.any { assignments[it.questionId].isNullOrBlank() }) return

            var correctCount = 0
            currentRoundQuestions.forEach { question ->
                val chosen = assignments[question.questionId].orEmpty()
                val isCorrect = chosen == question.displayName
                if (isCorrect) {
                    score.intValue += 10
                    correctCount += 1
                } else {
                    val emoKey = normalizeEmotionForLearning(question.correctEmotion)
                    val nextErrorCount = (emotionErrors[emoKey] ?: 0) + 1
                    emotionErrors[emoKey] = nextErrorCount
                    if (nextErrorCount >= maxErrors.intValue && emoKey !in learnedEmotions) {
                        learnedEmotions.add(emoKey)
                    }
                }
                results.value = results.value + AnswerResultDto(
                    questionId = question.questionId,
                    answer = chosen,
                    isCorrect = isCorrect,
                    responseTimeMs = (now - questionStartMs.longValue).toInt().coerceAtLeast(0)
                )
            }
            feedback.value = if (correctCount == currentRoundQuestions.size) {
                "Đúng rồi."
            } else {
                "Chưa đúng. Hãy bấm Tiếp tục để sang round mới."
            }
            return
        }

        val isLastRound = currentIndex.intValue >= TOTAL_ROUNDS - 1
        val noMoreQuestions = remainingQuestions.isEmpty()

        if (isLastRound || noMoreQuestions) {
            finishLevel(results.value)
        } else {
            currentIndex.intValue += 1
            roundQuestions.value = pickQuestionsForCurrentRound()

            assignments.clear()
            dropZones.clear()
            nameChipBounds.clear()
            draggingName.value = null
            draggingOffset.value = Offset.Zero
            selectedName.value = null
            feedback.value = null
            questionStartMs.longValue = System.currentTimeMillis()
        }
    }

    GameScreenShell(contentMaxWidth = 920, onOpenAssistant = onOpenAssistant) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("← Quay lại") }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Cảm xúc đúng chỗ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameStatChip("Câu ${currentIndex.intValue + 1}/${TOTAL_ROUNDS}")
                    GameStatChip("Điểm ${score.intValue}")
                    GameStatChip("Cấp độ $level")
                }

                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kéo tên vào đúng ảnh", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
                        Text(
                            "Kéo hoặc chạm tên vào đúng ảnh. Mỗi round có ${getNumQuestionsPerRound(level)} card.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (summary.value != null) {
                            GameLevelSummaryCard(summary = summary.value.orEmpty(), onBack = onBack)
                        }
                        if (summary.value == null) {
                            Text(
                                if (assignments.size == currentRoundQuestions.size && feedback.value == null)
                                    "Đã ghép đủ, bấm Trả lời để kiểm tra."
                                else if (feedback.value == null)
                                    "Kéo hoặc chạm tên để ghép với ảnh này."
                                else
                                    "Xem kết quả rồi bấm Tiếp tục.",
                                color = Color(0xFF64748B)
                            )

                            if (feedback.value != null) {
                                GameFeedbackCard(feedback.value.orEmpty())
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    currentRoundQuestions.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { question ->
                                val assigned = assignments[question.questionId]
                                val hasFeedback = feedback.value != null
                                val isCorrect = hasFeedback && assigned == question.displayName
                                val isWrong = hasFeedback && assigned != null && assigned != question.displayName
                                val borderColor = when {
                                    isCorrect -> Color(0xFF2E7D32)
                                    isWrong -> Color(0xFFD32F2F)
                                    else -> Color(0xFFE2E8F0)
                                }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coordinates ->
                                            dropZones[question.questionId] = coordinates.boundsInRoot()
                                        }
                                        .clickable(
                                            enabled = summary.value == null &&
                                                    feedback.value == null &&
                                                    selectedName.value != null &&
                                                    !isSubmitting.value
                                        ) {
                                            placeSelectedName(question.questionId)
                                        },
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(2.dp, borderColor),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        val bitmap = questionAssetBitmap(question)
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = question.displayName,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(136.dp)
                                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                                            )
                                        } else {
                                            Image(
                                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.game_click_4),
                                                contentDescription = question.displayName,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(136.dp)
                                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                                            )
                                        }
                                        Text(question.prompt, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(66.dp)
                                                .border(1.dp, Color(0xFFD8E0EA), RoundedCornerShape(14.dp))
                                                .background(
                                                    if (assigned != null) Color(0xFFE7F1FF) else Color(0xFFF8FAFC),
                                                    RoundedCornerShape(14.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (assigned != null) {
                                                Text(assigned, fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
                                            } else {
                                                Text("Thả tên vào đây", color = Color(0xFF94A3B8))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Thẻ tên", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
                Text("Chạm tên rồi chạm ảnh nếu kéo thả khó trên màn hình này.", fontSize = 12.sp, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    availableNames.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { name ->
                                val dragging = draggingName.value == name
                                val selected = selectedName.value == name
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coordinates ->
                                            nameChipBounds[name] = coordinates.boundsInRoot()
                                        }
                                        .clickable(
                                            enabled = summary.value == null &&
                                                    feedback.value == null &&
                                                    !isSubmitting.value &&
                                                    name !in assignedNames()
                                        ) {
                                            selectedName.value = if (selected) null else name
                                            draggingName.value = null
                                            draggingOffset.value = Offset.Zero
                                        }
                                        .pointerInput(name) {
                                            detectDragGestures(
                                                onDragStart = { touch ->
                                                    if (summary.value != null || feedback.value != null || isSubmitting.value) return@detectDragGestures
                                                    val bounds = nameChipBounds[name] ?: return@detectDragGestures
                                                    draggingName.value = name
                                                    draggingOffset.value = Offset(bounds.left + touch.x, bounds.top + touch.y)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    if (summary.value != null || feedback.value != null || isSubmitting.value) return@detectDragGestures
                                                    change.consume()
                                                    if (draggingName.value == name) {
                                                        draggingOffset.value = draggingOffset.value + dragAmount
                                                    }
                                                },
                                                onDragEnd = {
                                                    val dropPoint = draggingOffset.value
                                                    val target = dropZones.entries.firstOrNull { (_, rect) -> rect.contains(dropPoint) }?.key
                                                    if (target != null) {
                                                        placeDraggedName(target)
                                                    }
                                                    draggingName.value = null
                                                    draggingOffset.value = Offset.Zero
                                                },
                                                onDragCancel = {
                                                    draggingName.value = null
                                                    draggingOffset.value = Offset.Zero
                                                }
                                            )
                                        },
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(2.dp, if (dragging || selected) Color(0xFF3B82F6) else Color(0xFFE2E8F0)),
                                    colors = CardDefaults.cardColors(containerColor = if (dragging || selected) Color(0xFFE7F1FF) else Color.White)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text("🎴", fontSize = 24.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(name, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { handleSubmitRound() },
                    enabled = assignments.size == currentRoundQuestions.size && !isSubmitting.value && summary.value == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            isSubmitting.value -> "Đang lưu..."
                            feedback.value == null -> "Trả lời"
                            currentIndex.intValue >= TOTAL_ROUNDS - 1 -> "Hoàn thành"
                            else -> "Tiếp tục"
                        }
                    )
                }
            }

            if (draggingName.value != null) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (draggingOffset.value.x - with(density) { 72.dp.toPx() } / 2f).roundToInt(),
                                (draggingOffset.value.y - with(density) { 44.dp.toPx() } / 2f).roundToInt()
                            )
                        }
                        .size(width = 144.dp, height = 44.dp)
                        .background(Color(0xFFE7F1FF), RoundedCornerShape(14.dp))
                        .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(draggingName.value.orEmpty(), fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
                }
            }
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

private fun selectMatchQuestions(level: Int, backendQuestions: List<MatchQuestionUi>): List<MatchQuestionUi> {
    val seed = level * 31L
    return backendQuestions
        .shuffled(Random(seed))
        .take(QUESTIONS_PER_LEVEL)
}

private fun fallbackMatchQuestions(): List<MatchQuestionUi> {
    return listOf(
        MatchQuestionUi("fallback-match-happy", "Happy", null, "Bé vừa nhận quà yêu thích", "happy"),
        MatchQuestionUi("fallback-match-angry", "Angry", null, "Bé bị giật mất đồ chơi", "angry"),
        MatchQuestionUi("fallback-match-sad", "Sad", null, "Bé làm mất món đồ yêu quý", "sad"),
        MatchQuestionUi("fallback-match-surprise", "Surprise", null, "Bố mẹ tạo bất ngờ cho bé", "surprise"),
        MatchQuestionUi("fallback-match-fear", "Fear", null, "Bé nghe tiếng động lạ trong phòng tối", "fear"),
        MatchQuestionUi("fallback-match-disgust", "Disgust", null, "Bé ngửi thấy mùi rất khó chịu", "disgust")
    )
}
