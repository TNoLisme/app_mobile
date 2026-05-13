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
fun GameClick2Page(level: Int = 1, onBack: () -> Unit, onOpenAssistant: () -> Unit = {}) {
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

    fun finishLevel(finalResults: List<AnswerResultDto>) {
        if (isSubmitting.value || summary.value != null) return
        scope.launch {
            isSubmitting.value = true
            val response = sessionId.value?.let {
                repository.endLevel(it, finalResults, learnedEmotions.distinct())
            }
            summary.value = if (response != null) {
                val status = if (response.passed) "Đã qua level" else "Chưa qua level"
                "$status. Điểm: ${response.score}/100."
            } else {
                "Hoàn thành. Điểm tạm tính: ${score.intValue}."
            }
            isSubmitting.value = false
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
            val nextErrorCount = (emotionErrors[reviewEmotion] ?: 0) + 1
            emotionErrors[reviewEmotion] = nextErrorCount
            if (nextErrorCount >= maxErrors.intValue && reviewEmotion !in learnedEmotions) {
                learnedEmotions.add(reviewEmotion)
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

    LaunchedEffect(level, userId) {
        val started = repository.startGame(GameUiCatalog.GAME_FACE_ASSEMBLY, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotion = (content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null }
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
        emotionErrors.clear()
        learnedEmotions.clear()
        learningEmotionId.value = null
        resetCurrentQuestion()
    }

    val question = questions.value[currentIndex.intValue % questions.value.size]
    val target = faceEmotions.firstOrNull { it.id == question.targetEmotion } ?: faceEmotions.first()

    GameScreenShell(contentMaxWidth = 900, onOpenAssistant = onOpenAssistant) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Xưởng lắp ghép", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameStatChip("Câu ${currentIndex.intValue + 1}/${questions.value.size}")
                GameStatChip("Điểm ${score.intValue}")
                GameStatChip("Level $level")
            }

            if (summary.value != null) {
                Spacer(modifier = Modifier.height(20.dp))
                GameLevelSummaryCard(summary = summary.value.orEmpty(), onBack = onBack)
                return@Column
            }

            Spacer(modifier = Modifier.height(20.dp))
            BoxWithConstraints {
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
                            feedback = feedback.value,
                            isSubmitting = isSubmitting.value,
                            isLastQuestion = currentIndex.intValue >= questions.value.lastIndex,
                            onCycleEyebrow = { selectedEyebrow.intValue = nextEmotionIndex(selectedEyebrow.intValue) },
                            onCycleEyes = { selectedEyes.intValue = nextEmotionIndex(selectedEyes.intValue) },
                            onCycleMouth = { selectedMouth.intValue = nextEmotionIndex(selectedMouth.intValue) },
                            onReset = { resetCurrentQuestion() },
                            onCheck = { recordCurrentAnswer(target) },
                            onNext = { goNextOrFinish() }
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
                                feedback = feedback.value,
                                isSubmitting = isSubmitting.value,
                                isLastQuestion = currentIndex.intValue >= questions.value.lastIndex,
                                onCycleEyebrow = { selectedEyebrow.intValue = nextEmotionIndex(selectedEyebrow.intValue) },
                                onCycleEyes = { selectedEyes.intValue = nextEmotionIndex(selectedEyes.intValue) },
                                onCycleMouth = { selectedMouth.intValue = nextEmotionIndex(selectedMouth.intValue) },
                                onReset = { resetCurrentQuestion() },
                                onCheck = { recordCurrentAnswer(target) },
                                onNext = { goNextOrFinish() }
                            )
                        }
                    }
                }
            }
            EmotionLearningDialog(
                emotionId = learningEmotionId.value,
                onDismiss = { learningEmotionId.value = null }
            )
        }
    }
}

@Composable
private fun PreviewCard(selectedEyebrow: Int, selectedEyes: Int, selectedMouth: Int) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Khuôn mặt đang ghép", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.fillMaxWidth().height(270.dp)) {
                    FacePartBand(selectedEyebrow, partIndex = 0, label = "Lông mày")
                    FacePartBand(selectedEyes, partIndex = 1, label = "Mắt")
                    FacePartBand(selectedMouth, partIndex = 2, label = "Miệng")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Chọn cùng một cảm xúc cho cả 3 phần để tạo khuôn mặt đúng.", color = Color.Gray)
        }
    }
}

@Composable
private fun FacePartBand(emotionIndex: Int, partIndex: Int, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (emotionIndex < 0 || emotionIndex >= faceEmotions.size) {
            Text(label, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
            return@Box
        }

        val bitmap = ImageBitmap.imageResource(id = faceEmotions[emotionIndex].spriteRes)
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
                    color = Color(0xFFE2E8F0),
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
    feedback: String?,
    isSubmitting: Boolean,
    isLastQuestion: Boolean,
    onCycleEyebrow: () -> Unit,
    onCycleEyes: () -> Unit,
    onCycleMouth: () -> Unit,
    onReset: () -> Unit,
    onCheck: () -> Unit,
    onNext: () -> Unit
) {
    val canCheck = selectedEyebrow >= 0 && selectedEyes >= 0 && selectedMouth >= 0 && feedback == null

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Yêu cầu", fontWeight = FontWeight.Bold, color = Color(0xFF1E4E8C))
            Text(question.text, style = MaterialTheme.typography.bodyLarge)
            Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFF0F7FF)) {
                Text(
                    "${target.emoji} Ghép khuôn mặt: ${target.label}",
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            ControlItem("Lông mày", selectedEyebrow, onCycleEyebrow, enabled = feedback == null)
            ControlItem("Mắt", selectedEyes, onCycleEyes, enabled = feedback == null)
            ControlItem("Miệng", selectedMouth, onCycleMouth, enabled = feedback == null)

            if (feedback != null) {
                GameFeedbackCard(feedback)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f), enabled = feedback == null) {
                    Text("Chọn lại")
                }
                Button(onClick = onCheck, modifier = Modifier.weight(1f), enabled = canCheck) {
                    Text("Kiểm tra")
                }
            }

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                enabled = feedback != null && !isSubmitting
            ) {
                Text(
                    when {
                        isSubmitting -> "Đang lưu..."
                        isLastQuestion -> "Hoàn thành"
                        else -> "Câu tiếp theo"
                    }
                )
            }
        }
    }
}

@Composable
private fun ControlItem(title: String, selectedIndex: Int, onClick: () -> Unit, enabled: Boolean) {
    val selected = faceEmotions.getOrNull(selectedIndex)
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(selected?.let { "${it.emoji} ${it.label}" } ?: "Chưa chọn", fontWeight = FontWeight.Bold)
        }
    }
}

private fun fallbackAssemblyQuestions(): List<AssemblyQuestionUi> {
    return listOf(
        AssemblyQuestionUi("fallback-assembly-happy", "Hãy ghép khuôn mặt vui vẻ.", "happy"),
        AssemblyQuestionUi("fallback-assembly-sad", "Hãy ghép khuôn mặt buồn bã.", "sad"),
        AssemblyQuestionUi("fallback-assembly-angry", "Hãy ghép khuôn mặt tức giận.", "angry"),
        AssemblyQuestionUi("fallback-assembly-fear", "Hãy ghép khuôn mặt sợ hãi.", "fear"),
        AssemblyQuestionUi("fallback-assembly-surprise", "Hãy ghép khuôn mặt ngạc nhiên.", "surprise")
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
