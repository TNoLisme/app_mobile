package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import kotlin.math.roundToInt

private data class FaceEmotionUi(
    val id: String,
    val label: String,
    val emoji: String,
    val spriteRes: Int
)

private data class AssemblyQuestionUi(
    val questionId: String,
    val text: String,
    val targetEmotion: String
)

private val faceEmotions = listOf(
    FaceEmotionUi("happy", "Vui vẻ", "😊", R.drawable.face_ensemble_happy),
    FaceEmotionUi("sad", "Buồn bã", "😢", R.drawable.face_ensemble_sad),
    FaceEmotionUi("angry", "Tức giận", "😠", R.drawable.face_ensemble_angry),
    FaceEmotionUi("fear", "Sợ hãi", "😨", R.drawable.face_ensemble_fear),
    FaceEmotionUi("surprise", "Ngạc nhiên", "😲", R.drawable.face_ensemble_surprise),
    FaceEmotionUi("disgust", "Ghê tởm", "🤢", R.drawable.face_ensemble_disgust)
)

@Composable
fun FaceAssemblyPage(level: Int = 1, onBack: () -> Unit, onOpenAssistant: () -> Unit = {}) {
    val selectedEyebrow = remember(level) { mutableIntStateOf(-1) }
    val selectedEyes = remember(level) { mutableIntStateOf(-1) }
    val selectedMouth = remember(level) { mutableIntStateOf(-1) }
    val currentIndex = remember(level) { mutableIntStateOf(0) }
    val score = remember(level) { mutableIntStateOf(0) }
    val feedback = remember(level) { mutableStateOf<String?>(null) }
    val questions = remember(level) { mutableStateOf(fallbackAssemblyQuestions()) }
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
                    repository.invalidateProgressCache(GameUiCatalog.GAME_FACE_ASSEMBLY, userId)
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

    fun resetCurrentQuestion() {
        resetSelections(selectedEyebrow, selectedEyes, selectedMouth)
        feedback.value = null
        questionStartMs.value = System.currentTimeMillis()
    }

    fun recordCurrentAnswer(target: FaceEmotionUi) {
        if (feedback.value != null) return
        val isCorrect = selectedEyebrow.intValue == targetIndex(target.id) &&
            selectedEyes.intValue == targetIndex(target.id) &&
            selectedMouth.intValue == targetIndex(target.id)
        if (isCorrect) score.intValue += 10
        val reviewEmotion = normalizeEmotionForLearning(target.id)
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

        val selectedAnswer = if (
            selectedEyebrow.intValue == selectedEyes.intValue &&
            selectedEyes.intValue == selectedMouth.intValue
        ) {
            faceEmotions.getOrNull(selectedEyebrow.intValue)?.id ?: "unknown"
        } else {
            "mixed"
        }

        val question = questions.value[currentIndex.intValue]
        results.value = results.value + AnswerResultDto(
            questionId = question.questionId,
            answer = selectedAnswer,
            isCorrect = isCorrect,
            responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt()
        )
        feedback.value = if (isCorrect) {
            "Đúng rồi, con đã ghép khuôn mặt ${target.label}."
        } else {
            "Chưa đúng. Đáp án là khuôn mặt ${target.label}."
        }
    }

    fun goNextOrFinish() {
        if (currentIndex.intValue >= questions.value.lastIndex) {
            finishLevel(results.value)
            return
        }
        currentIndex.intValue += 1
        resetCurrentQuestion()
    }

    LaunchedEffect(level, userId, replayCount.intValue) {
        val started = repository.startGame(GameUiCatalog.GAME_FACE_ASSEMBLY, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotion = normalizeEmotionForLearning((content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null })
                AssemblyQuestionUi(
                    questionId = content.contentId,
                    text = content.questionText?.ifBlank { "Hãy ghép khuôn mặt phù hợp" } ?: "Hãy ghép khuôn mặt phù hợp",
                    targetEmotion = emotion
                )
            }
            .orEmpty()

        questions.value = backendQuestions.ifEmpty { fallbackAssemblyQuestions() }
        currentIndex.intValue = 0
        score.intValue = 0
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
        resetCurrentQuestion()
    }

    val question = questions.value[currentIndex.intValue % questions.value.size]
    val target = faceEmotions.firstOrNull { it.id == question.targetEmotion } ?: faceEmotions.get(0)

    GameScreenShell(contentMaxWidth = 900, onOpenAssistant = onOpenAssistant, scrollEnabled = false, bottomSpacerHeight = 0.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Xưởng lắp ghép", style = MaterialTheme.typography.titleLarge, color = EgDesign.textPrimary, fontWeight = FontWeight.Bold)
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
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(20.dp))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isMobile = maxWidth < 750.dp
                        if (isMobile) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                PreviewCard(selectedEyebrow.intValue, selectedEyes.intValue, selectedMouth.intValue)
                                AssemblyControls(
                                    question = question,
                                    target = target,
                                    selectedEyebrow = selectedEyebrow.intValue,
                                    selectedEyes = selectedEyes.intValue,
                                    selectedMouth = selectedMouth.intValue,
                                    hasFeedback = feedback.value != null,
                                    isSubmitting = isSubmitting.value,
                                    onCycleEyebrow = { selectedEyebrow.intValue = nextEmotionIndex(selectedEyebrow.intValue) },
                                    onCycleEyes = { selectedEyes.intValue = nextEmotionIndex(selectedEyes.intValue) },
                                    onCycleMouth = { selectedMouth.intValue = nextEmotionIndex(selectedMouth.intValue) },
                                    onReset = { resetCurrentQuestion() },
                                    onCheck = { recordCurrentAnswer(target) }
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    PreviewCard(selectedEyebrow.intValue, selectedEyes.intValue, selectedMouth.intValue)
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    AssemblyControls(
                                        question = question,
                                        target = target,
                                        selectedEyebrow = selectedEyebrow.intValue,
                                        selectedEyes = selectedEyes.intValue,
                                        selectedMouth = selectedMouth.intValue,
                                        hasFeedback = feedback.value != null,
                                        isSubmitting = isSubmitting.value,
                                        onCycleEyebrow = { selectedEyebrow.intValue = nextEmotionIndex(selectedEyebrow.intValue) },
                                        onCycleEyes = { selectedEyes.intValue = nextEmotionIndex(selectedEyes.intValue) },
                                        onCycleMouth = { selectedMouth.intValue = nextEmotionIndex(selectedMouth.intValue) },
                                        onReset = { resetCurrentQuestion() },
                                        onCheck = { recordCurrentAnswer(target) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (feedback.value != null) {
                        GameFeedbackCard(feedback.value.orEmpty())
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { goNextOrFinish() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = feedback.value != null && !isSubmitting.value && learningEmotionId.value == null
                ) {
                    Text(
                        when {
                            isSubmitting.value -> "Đang lưu..."
                            currentIndex.intValue >= questions.value.lastIndex -> "Hoàn thành"
                            else -> "Câu tiếp theo"
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
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
                                repository.resetReviewEmotions(GameUiCatalog.GAME_FACE_ASSEMBLY, userId, listOf(emotion))
                            } catch (_: Exception) {}
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PreviewCard(selectedEyebrow: Int, selectedEyes: Int, selectedMouth: Int) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Khuôn mặt đang ghép", fontWeight = FontWeight.Bold, color = EgDesign.textPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = EgDesign.cardSoft),
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth().height(270.dp)) {
                    FacePartBand(selectedEyebrow, partIndex = 0, label = "Lông mày")
                    FacePartBand(selectedEyes, partIndex = 1, label = "Mắt")
                    FacePartBand(selectedMouth, partIndex = 2, label = "Miệng")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Chọn cùng một cảm xúc cho cả 3 phần để tạo khuôn mặt đúng.", color = EgDesign.textSecondary)
        }
    }
}

@Composable
private fun FacePartBand(emotionIndex: Int, partIndex: Int, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(EgDesign.card),
        contentAlignment = Alignment.Center
    ) {
        if (emotionIndex < 0 || emotionIndex >= faceEmotions.size) {
            Text(label, color = EgDesign.textSecondary, fontWeight = FontWeight.SemiBold)
            return@Box
        }

        val bitmap = ImageBitmap.imageResource(id = faceEmotions.get(emotionIndex).spriteRes)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val partHeight = bitmap.height / 3
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(0, partIndex * partHeight),
                srcSize = IntSize(bitmap.width, partHeight),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )
            if (partIndex < 2) {
                drawLine(
                    color = EgDesign.cardBorder,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun AssemblyControls(
    question: AssemblyQuestionUi,
    target: FaceEmotionUi,
    selectedEyebrow: Int,
    selectedEyes: Int,
    selectedMouth: Int,
    hasFeedback: Boolean,
    isSubmitting: Boolean,
    onCycleEyebrow: () -> Unit,
    onCycleEyes: () -> Unit,
    onCycleMouth: () -> Unit,
    onReset: () -> Unit,
    onCheck: () -> Unit
) {
    val canCheck = selectedEyebrow >= 0 && selectedEyes >= 0 && selectedMouth >= 0 && !hasFeedback

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Yêu cầu", fontWeight = FontWeight.Bold, color = EgDesign.textPrimary)
            Text(question.text, style = MaterialTheme.typography.bodyLarge, color = EgDesign.textSecondary)
            Surface(shape = MaterialTheme.shapes.large, color = EgDesign.cardSoft) {
                Text(
                    "${target.emoji} Ghép khuôn mặt: ${target.label}",
                    modifier = Modifier.padding(12.dp),
                    color = EgDesign.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            ControlItem("Lông mày", selectedEyebrow, onCycleEyebrow, enabled = !hasFeedback && !isSubmitting)
            ControlItem("Mắt", selectedEyes, onCycleEyes, enabled = !hasFeedback && !isSubmitting)
            ControlItem("Miệng", selectedMouth, onCycleMouth, enabled = !hasFeedback && !isSubmitting)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f), enabled = !hasFeedback && !isSubmitting) {
                    Text("Chọn lại")
                }
                Button(onClick = onCheck, modifier = Modifier.weight(1f), enabled = canCheck && !isSubmitting) {
                    Text("Kiểm tra")
                }
            }
        }
    }
}

@Composable
private fun ControlItem(title: String, selectedIndex: Int, onClick: () -> Unit, enabled: Boolean) {
    val selected = faceEmotions.getOrNull(selectedIndex)
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = EgDesign.textPrimary)
            Text(
                selected?.let { "${it.emoji} ${it.label}" } ?: "Chưa chọn",
                color = EgDesign.blue,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun fallbackAssemblyQuestions(): List<AssemblyQuestionUi> {
    return listOf(
        AssemblyQuestionUi("fallback-assembly-happy", "Hãy ghép khuôn mặt Vui vẻ.", "happy"),
        AssemblyQuestionUi("fallback-assembly-sad", "Hãy ghép khuôn mặt Buồn bã.", "sad"),
        AssemblyQuestionUi("fallback-assembly-angry", "Hãy ghép khuôn mặt Tức giận.", "angry"),
        AssemblyQuestionUi("fallback-assembly-fear", "Hãy ghép khuôn mặt Sợ hãi.", "fear"),
        AssemblyQuestionUi("fallback-assembly-surprise", "Hãy ghép khuôn mặt Ngạc nhiên.", "surprise")
    )
}

private fun targetIndex(emotionId: String): Int = faceEmotions.indexOfFirst { it.id == emotionId }

private fun nextEmotionIndex(current: Int): Int = if (current < 0) 0 else (current + 1) % faceEmotions.size

private fun resetSelections(
    selectedEyebrow: MutableIntState,
    selectedEyes: MutableIntState,
    selectedMouth: MutableIntState
) {
    selectedEyebrow.intValue = -1
    selectedEyes.intValue = -1
    selectedMouth.intValue = -1
}
