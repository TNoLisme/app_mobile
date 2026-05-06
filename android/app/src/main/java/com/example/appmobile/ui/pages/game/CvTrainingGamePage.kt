package com.example.appmobile.ui.pages.game

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.CvPromptUiItem
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private data class CvQuestionUi(
    val questionId: String,
    val prompt: CvPromptUiItem
)

@Composable
fun CvTrainingGamePage(
    gameId: String,
    level: Int,
    title: String,
    subtitle: String,
    imageRes: Int,
    defaultPrompt: CvPromptUiItem,
    promptLabel: String,
    targetLabel: String,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "local-player" }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }

    val currentIndex = remember(level, gameId) { mutableIntStateOf(0) }
    val score = remember(level, gameId) { mutableIntStateOf(0) }
    val questions = remember(level, gameId) {
        mutableStateOf(listOf(CvQuestionUi("fallback-$gameId", defaultPrompt)))
    }
    val sessionId = remember(level, gameId) { mutableStateOf<String?>(null) }
    val results = remember(level, gameId) { mutableStateOf<List<AnswerResultDto>>(emptyList()) }
    val summary = remember(level, gameId) { mutableStateOf<String?>(null) }
    val feedback = remember(level, gameId) { mutableStateOf<String?>(null) }
    val capturedBitmap = remember(level, gameId) { mutableStateOf<Bitmap?>(null) }
    val isSubmitting = remember(level, gameId) { mutableStateOf(false) }
    val questionStartMs = remember(level, gameId) { mutableStateOf(System.currentTimeMillis()) }
    val maxErrors = remember(level, gameId) { mutableIntStateOf(2) }
    val emotionErrors = remember(level, gameId) { mutableStateMapOf<String, Int>() }
    val learnedEmotions = remember(level, gameId) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level, gameId) { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        capturedBitmap.value = bitmap
        feedback.value = null
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

    fun recordAttempt(success: Boolean) {
        if (feedback.value != null) return
        val question = questions.value[currentIndex.intValue]
        if (success) score.intValue += 10
        val reviewEmotion = normalizeEmotionForLearning(question.prompt.correctAnswer)
        if (!success) {
            val nextErrorCount = (emotionErrors[reviewEmotion] ?: 0) + 1
            emotionErrors[reviewEmotion] = nextErrorCount
            if (nextErrorCount >= maxErrors.intValue && reviewEmotion !in learnedEmotions) {
                learnedEmotions.add(reviewEmotion)
                learningEmotionId.value = reviewEmotion
            }
        }
        val updatedResults = results.value + AnswerResultDto(
            questionId = question.questionId,
            answer = if (success) question.prompt.correctAnswer else "not_matched",
            isCorrect = success,
            responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt()
        )
        results.value = updatedResults
        feedback.value = if (success) {
            "Đã ghi nhận đạt yêu cầu."
        } else {
            "Đã ghi nhận chưa đạt, con có thể thử lại ở lần sau."
        }
    }

    fun goNextOrFinish() {
        if (currentIndex.intValue >= questions.value.lastIndex) {
            finishLevel(results.value)
            return
        }
        currentIndex.intValue += 1
        capturedBitmap.value = null
        feedback.value = null
        questionStartMs.value = System.currentTimeMillis()
    }

    LaunchedEffect(gameId, level, userId) {
        val started = repository.startGame(gameId, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 2
        val backendQuestions = started?.questions
            ?.mapNotNull { content ->
                val emotion = (content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null }
                val text = content.questionText?.ifBlank { defaultPrompt.questionText } ?: defaultPrompt.questionText
                CvQuestionUi(
                    questionId = content.contentId,
                    prompt = CvPromptUiItem(questionText = text, correctAnswer = emotion)
                )
            }
            .orEmpty()

        questions.value = backendQuestions.ifEmpty { listOf(CvQuestionUi("fallback-$gameId", defaultPrompt)) }
        currentIndex.intValue = 0
        score.intValue = 0
        results.value = emptyList()
        summary.value = null
        feedback.value = null
        capturedBitmap.value = null
        emotionErrors.clear()
        learnedEmotions.clear()
        learningEmotionId.value = null
        questionStartMs.value = System.currentTimeMillis()
    }

    val currentQuestion = questions.value[currentIndex.intValue % questions.value.size]

    GameScreenShell(contentMaxWidth = 1000, onOpenAssistant = onOpenAssistant) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color(0xFFE7F1FF),
                    border = BorderStroke(1.dp, Color(0xFFBFD7FF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (currentIndex.intValue + 1).toFloat() / questions.value.size },
                            modifier = Modifier.width(80.dp).height(8.dp),
                            color = Color(0xFF3B82F6),
                            trackColor = Color(0xFFD8E9FF)
                        )
                        Text("Màn ${currentIndex.intValue + 1}/${questions.value.size}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E4E8C)
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

            if (summary.value != null) {
                Spacer(modifier = Modifier.height(20.dp))
                GameLevelSummaryCard(summary = summary.value.orEmpty(), onBack = onBack)
                return@Column
            }

            Spacer(modifier = Modifier.height(20.dp))
            BoxWithConstraints {
                val isMobile = maxWidth < 850.dp
                if (isMobile) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CvPromptCard(currentQuestion.prompt, imageRes, promptLabel, targetLabel)
                        CvCaptureCard(
                            bitmap = capturedBitmap.value,
                            feedback = feedback.value,
                            isSubmitting = isSubmitting.value,
                            isLastQuestion = currentIndex.intValue >= questions.value.lastIndex,
                            onCapture = { cameraLauncher.launch(null) },
                            onMarkSuccess = { recordAttempt(true) },
                            onMarkFailed = { recordAttempt(false) },
                            onNext = { goNextOrFinish() }
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CvPromptCard(currentQuestion.prompt, imageRes, promptLabel, targetLabel)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CvCaptureCard(
                                bitmap = capturedBitmap.value,
                                feedback = feedback.value,
                                isSubmitting = isSubmitting.value,
                                isLastQuestion = currentIndex.intValue >= questions.value.lastIndex,
                                onCapture = { cameraLauncher.launch(null) },
                                onMarkSuccess = { recordAttempt(true) },
                                onMarkFailed = { recordAttempt(false) },
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
private fun CvPromptCard(
    challenge: CvPromptUiItem,
    imageRes: Int,
    promptLabel: String,
    targetLabel: String
) {
    val targetEmotion = GameUiCatalog.emotionById(challenge.correctAnswer)?.name ?: challenge.correctAnswer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(promptLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Text(challenge.questionText, style = MaterialTheme.typography.bodyMedium)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color(0xFFE7F1FF),
                border = BorderStroke(1.dp, Color(0xFFBFD7FF))
            ) {
                Text(
                    "$targetLabel: $targetEmotion",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E4E8C)
                )
            }
        }
    }
}

@Composable
private fun CvCaptureCard(
    bitmap: Bitmap?,
    feedback: String?,
    isSubmitting: Boolean,
    isLastQuestion: Boolean,
    onCapture: () -> Unit,
    onMarkSuccess: () -> Unit,
    onMarkFailed: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color.Black, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Chưa có ảnh biểu cảm", color = Color.LightGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Bấm chụp để mở camera", color = Color.LightGray)
                    }
                }
            }

            if (feedback != null) {
                GameFeedbackCard(feedback)
            }

            Button(onClick = onCapture, modifier = Modifier.fillMaxWidth(), enabled = feedback == null) {
                Text(if (bitmap == null) "Chụp biểu cảm" else "Chụp lại")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onMarkFailed,
                    modifier = Modifier.weight(1f),
                    enabled = bitmap != null && feedback == null
                ) {
                    Text("Chưa đạt")
                }
                Button(
                    onClick = onMarkSuccess,
                    modifier = Modifier.weight(1f),
                    enabled = bitmap != null && feedback == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("Đạt")
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
                        else -> "Màn tiếp theo"
                    }
                )
            }

            Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFF1F5F9)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Đánh giá")
                    Text("Tạm thời thủ công", fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
                }
            }
        }
    }
}
