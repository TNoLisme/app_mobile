package com.example.appmobile.ui.pages.game

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.CvPromptUiItem
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor

private const val CvRoundSeconds = 30
private const val CvRequiredConfidence = 60f

private data class CvQuestionUi(
    val questionId: String,
    val prompt: CvPromptUiItem
)

private data class CvEmotionMeta(
    val id: String,
    val label: String,
    val shortLabel: String,
    val emoji: String,
    val scenarioTitle: String,
    val hint: String
)

@Composable
fun CvTrainingGamePage(
    gameId: String,
    level: Int,
    selectedEmotion: String? = null,
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
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
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
    val lastAttemptSuccess = remember(level, gameId) { mutableStateOf<Boolean?>(null) }
    val isSubmitting = remember(level, gameId) { mutableStateOf(false) }
    val questionStartMs = remember(level, gameId) { mutableStateOf(System.currentTimeMillis()) }
    val maxErrors = remember(level, gameId) { mutableIntStateOf(2) }
    val emotionErrors = remember(level, gameId) { mutableStateMapOf<String, Int>() }
    val learnedEmotions = remember(level, gameId) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level, gameId) { mutableStateOf<String?>(null) }
    val startRequested = remember(level, gameId) { mutableStateOf(false) }
    val isRequestingCameraPermission = remember(level, gameId) { mutableStateOf(false) }
    val challengeStarted = remember(level, gameId) { mutableStateOf(false) }
    val remainingSeconds = remember(level, gameId) { mutableIntStateOf(CvRoundSeconds) }
    val currentConfidence = remember(level, gameId) { mutableStateOf(0f) }
    val detectedEmotion = remember(level, gameId) { mutableStateOf<String?>(null) }
    val cameraMessage = remember(level, gameId) { mutableStateOf<String?>(null) }
    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isRequestingCameraPermission.value = false
        hasCameraPermission.value = granted
        cameraMessage.value = if (granted) null else "Bạn có thể bật lại quyền camera trong Cài đặt."
        remainingSeconds.intValue = CvRoundSeconds
        if (granted && startRequested.value) {
            feedback.value = null
            lastAttemptSuccess.value = null
            currentConfidence.value = 0f
            detectedEmotion.value = null
            questionStartMs.value = System.currentTimeMillis()
            challengeStarted.value = true
        } else {
            challengeStarted.value = false
        }
    }

    fun resetCurrentRound(start: Boolean = false) {
        feedback.value = null
        lastAttemptSuccess.value = null
        currentConfidence.value = 0f
        detectedEmotion.value = null
        remainingSeconds.intValue = CvRoundSeconds
        questionStartMs.value = System.currentTimeMillis()
        startRequested.value = start
        challengeStarted.value = start
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
                "$status. Điểm: ${response.score}/100."
            } else {
                "Hoàn thành. Điểm tạm tính: ${score.intValue}."
            }
            isSubmitting.value = false
        }
    }

    fun recordAttempt(success: Boolean, confidence: Float) {
        if (feedback.value != null || summary.value != null) return
        val question = questions.value[currentIndex.intValue]
        val reviewEmotion = normalizeCvEmotion(question.prompt.correctAnswer)
        val targetMeta = cvEmotionMeta(reviewEmotion)
        if (success) score.intValue += 10
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
            responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt(),
            cvConfidence = confidence
        )
        results.value = updatedResults
        currentConfidence.value = confidence
        detectedEmotion.value = if (success) reviewEmotion else null
        lastAttemptSuccess.value = success
        challengeStarted.value = false
        feedback.value = if (success) {
            "Bạn đã thể hiện cảm xúc ${targetMeta.label}."
        } else {
            "Hãy xem gợi ý và làm lại biểu cảm."
        }
    }

    fun goNextOrFinish() {
        if (currentIndex.intValue >= questions.value.lastIndex) {
            finishLevel(results.value)
            return
        }
        currentIndex.intValue += 1
        resetCurrentRound(start = false)
    }

    LaunchedEffect(gameId, level, userId, selectedEmotion) {
        val selectedEmotionKey = selectedEmotion?.takeIf { it.isNotBlank() }?.let(::normalizeCvEmotion)
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

        val filteredQuestions = selectedEmotionKey?.let { key ->
            backendQuestions.filter { normalizeCvEmotion(it.prompt.correctAnswer) == key }
        } ?: backendQuestions
        val fallbackPrompt = selectedEmotionKey?.let { key ->
            CvPromptUiItem(questionText = "", correctAnswer = key)
        } ?: defaultPrompt

        questions.value = filteredQuestions.ifEmpty {
            listOf(CvQuestionUi("fallback-$gameId-${selectedEmotionKey ?: "default"}", fallbackPrompt))
        }
        currentIndex.intValue = 0
        score.intValue = 0
        results.value = emptyList()
        summary.value = null
        emotionErrors.clear()
        learnedEmotions.clear()
        learningEmotionId.value = null
        resetCurrentRound(start = false)
    }

    val currentQuestion = questions.value[currentIndex.intValue % questions.value.size]
    val targetEmotion = cvEmotionMeta(currentQuestion.prompt.correctAnswer)
    val cameraReady = startRequested.value && hasCameraPermission.value && cameraMessage.value == null
    val timerActive = cameraReady && challengeStarted.value && feedback.value == null && summary.value == null

    LaunchedEffect(currentIndex.intValue, timerActive, feedback.value, summary.value) {
        if (!timerActive) {
            if (!challengeStarted.value || !cameraReady) {
                remainingSeconds.intValue = CvRoundSeconds
            }
            return@LaunchedEffect
        }
        remainingSeconds.intValue = CvRoundSeconds
        questionStartMs.value = System.currentTimeMillis()
        while (remainingSeconds.intValue > 0 && challengeStarted.value && feedback.value == null && summary.value == null) {
            delay(1000)
            remainingSeconds.intValue -= 1
        }
        if (remainingSeconds.intValue <= 0 && feedback.value == null && summary.value == null) {
            recordAttempt(success = false, confidence = 0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EgDesign.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CvTopBar(
                title = displayCvTitle(title, gameId),
                roundText = "Lượt ${currentIndex.intValue + 1}/${questions.value.size}",
                onBack = onBack
            )

            if (summary.value != null) {
                GameLevelSummaryCard(summary = summary.value.orEmpty(), onBack = onBack)
                Spacer(modifier = Modifier.height(80.dp))
                return@Column
            }

            CvCameraFeedbackCard(
                hasPermission = cameraReady,
                cameraMessage = cameraMessage.value,
                onCameraError = {
                    cameraMessage.value = "Không thể mở camera. Vui lòng thử lại."
                    challengeStarted.value = false
                },
                targetEmotion = targetEmotion,
                detectedEmotionId = detectedEmotion.value,
                confidence = currentConfidence.value,
                feedback = feedback.value,
                attemptSuccess = lastAttemptSuccess.value,
                cameraReady = cameraReady,
                challengeStarted = challengeStarted.value,
                startRequested = startRequested.value,
                isRequestingCameraPermission = isRequestingCameraPermission.value,
                remainingSeconds = remainingSeconds.intValue,
                timerActive = timerActive,
                isLastQuestion = currentIndex.intValue >= questions.value.lastIndex,
                isSubmitting = isSubmitting.value,
                onStart = {
                    startRequested.value = true
                    if (hasCameraPermission.value) {
                        cameraMessage.value = null
                        resetCurrentRound(start = true)
                    } else {
                        isRequestingCameraPermission.value = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onRetry = { resetCurrentRound(start = false) },
                onMarkSuccess = { recordAttempt(success = true, confidence = 100f) },
                onNext = { goNextOrFinish() }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        EmotionLearningDialog(
            emotionId = learningEmotionId.value,
            onDismiss = { learningEmotionId.value = null }
        )

    }
}

@Composable
private fun CvTopBar(title: String, roundText: String, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppBackButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White,
                border = BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 1.dp
            ) {
                Text(
                    text = roundText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = EgDesign.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = title,
            color = EgDesign.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 28.sp
        )
    }
}

@Composable
private fun CvProgressTimer(
    current: Int,
    total: Int,
    remainingSeconds: Int,
    timerActive: Boolean
) {
    val progress = if (total <= 0) 0f else current.toFloat() / total.toFloat()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Tiến trình thử thách",
                        color = EgDesign.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (timerActive) "Đang tính thời gian" else "Chờ bắt đầu",
                        color = EgDesign.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (timerActive && remainingSeconds <= 5) Color(0xFFFFF1F2) else Color(0xFFEAF7FF),
                    border = BorderStroke(
                        1.dp,
                        if (timerActive && remainingSeconds <= 5) Color(0xFFFDA4AF) else EgDesign.cardBorder
                    )
                ) {
                    Text(
                        text = formatCvTime(remainingSeconds),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (timerActive && remainingSeconds <= 5) Color(0xFFBE123C) else EgDesign.blue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = EgDesign.primary,
                trackColor = Color(0xFFDDEBFF)
            )
        }
    }
}

@Composable
private fun CvCameraFeedbackCard(
    hasPermission: Boolean,
    cameraMessage: String?,
    onCameraError: (Throwable) -> Unit,
    targetEmotion: CvEmotionMeta,
    detectedEmotionId: String?,
    confidence: Float,
    feedback: String?,
    attemptSuccess: Boolean?,
    cameraReady: Boolean,
    challengeStarted: Boolean,
    startRequested: Boolean,
    isRequestingCameraPermission: Boolean,
    remainingSeconds: Int,
    timerActive: Boolean,
    isLastQuestion: Boolean,
    isSubmitting: Boolean,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onMarkSuccess: () -> Unit,
    onNext: () -> Unit
) {
    val detectedMeta = detectedEmotionId?.let(::cvEmotionMeta)
    val title = when {
        isRequestingCameraPermission -> "Đang chuẩn bị camera..."
        !startRequested -> "Sẵn sàng thử thách"
        !cameraReady -> "Chưa bật camera"
        attemptSuccess == true -> "🎉 Đúng rồi!"
        attemptSuccess == false -> "Thử lại nhé"
        !challengeStarted -> "Sẵn sàng thử thách"
        confidence >= CvRequiredConfidence -> "Sắp đúng rồi!"
        else -> "Đang nhận diện..."
    }
    val subtitle = when {
        isRequestingCameraPermission -> "Vui lòng cấp quyền camera để bắt đầu."
        !startRequested -> "Nhấn bắt đầu và thể hiện cảm xúc nhé."
        !cameraReady -> "Hãy cấp quyền camera để bắt đầu."
        attemptSuccess == true -> feedback ?: "Bạn đã thể hiện cảm xúc ${targetEmotion.label}."
        attemptSuccess == false -> feedback ?: "Hãy làm lại biểu cảm."
        !challengeStarted -> "Nhấn bắt đầu và thể hiện cảm xúc nhé."
        confidence >= CvRequiredConfidence -> "Giữ biểu cảm thêm chút nữa."
        else -> "Hãy giữ khuôn mặt trong khung hình."
    }
    val activeLamp = when {
        attemptSuccess == true -> 2
        challengeStarted || confidence >= CvRequiredConfidence -> 1
        else -> 0
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF102A43)),
                contentAlignment = Alignment.Center
            ) {
                if (hasPermission) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onCameraError = onCameraError
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📷", fontSize = 42.sp)
                        Text(
                            text = when {
                                !startRequested -> "Camera sẽ bật khi bắt đầu"
                                isRequestingCameraPermission -> "Đang chuẩn bị camera..."
                                else -> "Cần quyền camera để nhận diện biểu cảm"
                            },
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp
                        )
                        if (isRequestingCameraPermission) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Text(
                                text = if (!startRequested) {
                                    "Hãy chuẩn bị khuôn mặt trong khung hình nhé."
                                } else {
                                    "Bấm nút bên dưới để cấp quyền camera."
                                },
                                color = Color.White.copy(alpha = 0.82f),
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
                        CvStatusLamps(activeLamp = activeLamp)
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = if (timerActive && remainingSeconds <= 5) {
                        Color(0xFFFFF1F2).copy(alpha = 0.96f)
                    } else {
                        Color.White.copy(alpha = 0.94f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (timerActive && remainingSeconds <= 5) Color(0xFFFDA4AF) else EgDesign.cardBorder
                    )
                ) {
                    Text(
                        text = formatCvTime(remainingSeconds),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (timerActive && remainingSeconds <= 5) Color(0xFFBE123C) else EgDesign.blue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            if (!hasPermission && !cameraMessage.isNullOrBlank()) {
                Text(
                    text = "Bạn có thể bật lại quyền camera trong Cài đặt.",
                    color = Color(0xFF92400E),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            } else if (hasPermission && !cameraMessage.isNullOrBlank()) {
                Text(cameraMessage, color = Color(0xFFBE123C), fontSize = 13.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    color = Color(0xFFEAF7FF),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(detectedMeta?.emoji ?: targetEmotion.emoji, fontSize = 30.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = EgDesign.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = subtitle,
                        color = EgDesign.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Mục tiêu: ${targetEmotion.label}",
                        color = EgDesign.blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${confidence.toInt()}%",
                    color = EgDesign.blue,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            LinearProgressIndicator(
                progress = { (confidence / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = if (confidence >= CvRequiredConfidence) Color(0xFF22C55E) else EgDesign.primary,
                trackColor = Color(0xFFE2E8F0)
            )

            CvActionRow(
                cameraReady = cameraReady,
                challengeStarted = challengeStarted,
                startRequested = startRequested,
                isRequestingCameraPermission = isRequestingCameraPermission,
                feedback = feedback,
                attemptSuccess = attemptSuccess,
                isLastQuestion = isLastQuestion,
                isSubmitting = isSubmitting,
                onStart = onStart,
                onRetry = onRetry,
                onMarkSuccess = onMarkSuccess,
                onNext = onNext
            )
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier = Modifier, onCameraError: (Throwable) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: Executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview
                    )
                }.onFailure(onCameraError)
            },
            executor
        )
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun UnusedCvDetectionCard(
    targetEmotion: CvEmotionMeta,
    detectedEmotionId: String?,
    confidence: Float,
    feedback: String?,
    attemptSuccess: Boolean?,
    cameraReady: Boolean,
    challengeStarted: Boolean
) {
    val detectedMeta = detectedEmotionId?.let(::cvEmotionMeta)
    val title = when {
        !cameraReady -> "Chưa bật camera"
        attemptSuccess == true -> "🎉 Đúng rồi!"
        attemptSuccess == false -> "Thử lại nhé"
        !challengeStarted -> "Sẵn sàng thử thách"
        confidence >= CvRequiredConfidence -> "Sắp đúng rồi!"
        else -> "Đang nhận diện..."
    }
    val subtitle = when {
        !cameraReady -> "Hãy cấp quyền camera để bắt đầu."
        attemptSuccess == true -> feedback ?: "Bạn đã thể hiện cảm xúc ${targetEmotion.label}."
        attemptSuccess == false -> feedback ?: "Hãy xem gợi ý và làm lại biểu cảm."
        !challengeStarted -> "Nhấn bắt đầu và thể hiện cảm xúc nhé."
        confidence >= CvRequiredConfidence -> "Giữ biểu cảm thêm chút nữa."
        else -> "Hãy giữ khuôn mặt trong khung hình."
    }
    val activeLamp = when {
        attemptSuccess == true -> 2
        challengeStarted || confidence >= CvRequiredConfidence -> 1
        else -> 0
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(62.dp),
                    shape = CircleShape,
                    color = Color(0xFFEAF7FF),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(detectedMeta?.emoji ?: targetEmotion.emoji, fontSize = 32.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = EgDesign.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = subtitle,
                        color = EgDesign.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Mục tiêu: ${targetEmotion.label}",
                        color = EgDesign.blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${confidence.toInt()}%",
                    color = EgDesign.blue,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            LinearProgressIndicator(
                progress = { (confidence / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = if (confidence >= CvRequiredConfidence) Color(0xFF22C55E) else EgDesign.primary,
                trackColor = Color(0xFFE2E8F0)
            )

            CvStatusLamps(activeLamp = activeLamp)
        }
    }
}

@Composable
private fun CvStatusLamps(activeLamp: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Color(0xFFEF4444), Color(0xFFFACC15), Color(0xFF22C55E)).forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .size(if (index == activeLamp) 14.dp else 10.dp)
                    .background(color.copy(alpha = if (index == activeLamp) 1f else 0.25f), CircleShape)
            )
        }
    }
}

@Composable
private fun CvActionRow(
    cameraReady: Boolean,
    challengeStarted: Boolean,
    startRequested: Boolean,
    isRequestingCameraPermission: Boolean,
    feedback: String?,
    attemptSuccess: Boolean?,
    isLastQuestion: Boolean,
    isSubmitting: Boolean,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onMarkSuccess: () -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (feedback == null) {
            if (isRequestingCameraPermission) {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = false
                ) {
                    Text("Đang chuẩn bị camera...", fontWeight = FontWeight.Bold)
                }
            } else if (!cameraReady) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                ) {
                    Text(
                        if (startRequested) "Cấp quyền camera" else "Bắt đầu thử thách",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (!challengeStarted) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                ) {
                    Text("Bắt đầu thử thách", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Text("Làm lại", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onMarkSuccess,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                    ) {
                        Text("Xác nhận đúng", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
            ) {
                Text(
                    when {
                        isSubmitting -> "Đang lưu..."
                        isLastQuestion -> if (attemptSuccess == true) "Hoàn thành" else "Kết thúc lượt"
                        else -> "Lượt tiếp theo"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun cvEmotionMeta(rawEmotion: String): CvEmotionMeta {
    return when (normalizeCvEmotion(rawEmotion)) {
        "happy" -> CvEmotionMeta(
            id = "happy",
            label = "Vui vẻ",
            shortLabel = "vui vẻ",
            emoji = "😊",
            scenarioTitle = "Nụ cười thật tươi",
            hint = "Hãy mỉm cười, mắt hơi híp lại và giữ khuôn mặt trong vài giây."
        )
        "sad" -> CvEmotionMeta(
            id = "sad",
            label = "Buồn bã",
            shortLabel = "buồn bã",
            emoji = "😢",
            scenarioTitle = "Mình hơi buồn",
            hint = "Hạ khóe miệng, ánh mắt buồn và giữ biểu cảm."
        )
        "angry" -> CvEmotionMeta(
            id = "angry",
            label = "Tức giận",
            shortLabel = "tức giận",
            emoji = "😡",
            scenarioTitle = "Không vui chút nào",
            hint = "Nhíu mày, mím môi và nhìn nghiêm."
        )
        "fear" -> CvEmotionMeta(
            id = "fear",
            label = "Sợ hãi",
            shortLabel = "sợ hãi",
            emoji = "😨",
            scenarioTitle = "Ôi, mình hơi sợ",
            hint = "Mở to mắt, hơi lùi mặt lại và giữ biểu cảm."
        )
        "surprise" -> CvEmotionMeta(
            id = "surprise",
            label = "Ngạc nhiên",
            shortLabel = "ngạc nhiên",
            emoji = "😮",
            scenarioTitle = "Ôi! Bất ngờ quá",
            hint = "Mở to mắt, há miệng nhẹ và nâng lông mày."
        )
        "disgust" -> CvEmotionMeta(
            id = "disgust",
            label = "Ghê tởm",
            shortLabel = "ghê tởm",
            emoji = "🤢",
            scenarioTitle = "Mùi này khó chịu quá",
            hint = "Nhăn mũi, hơi cau mày và giữ biểu cảm."
        )
        else -> cvEmotionMeta("happy")
    }
}

private fun normalizeCvEmotion(value: String): String {
    val decoded = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
    val lower = decoded.trim().lowercase()
    return when {
        lower.contains("happy") || lower.contains("vui") -> "happy"
        lower.contains("sad") || lower.contains("buồn") || lower.contains("buon") -> "sad"
        lower.contains("angry") || lower.contains("tức") || lower.contains("tuc") -> "angry"
        lower.contains("fear") || lower.contains("sợ") || lower.contains("so hai") || lower.contains("so_hai") -> "fear"
        lower.contains("surprise") || lower.contains("ngạc") || lower.contains("ngac") -> "surprise"
        lower.contains("disgust") || lower.contains("ghê") || lower.contains("ghe") -> "disgust"
        else -> lower
    }
}

private fun formatCvTime(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun displayCvPrompt(rawPrompt: String, emotion: CvEmotionMeta): String {
    val cleanPrompt = rawPrompt.trim()
    if (cleanPrompt.isNotBlank() && !hasEncodingIssue(cleanPrompt)) {
        return cleanPrompt
    }
    return when (emotion.id) {
        "happy" -> "Hãy mỉm cười thật tươi như vừa được khen."
        "sad" -> "Hạ khóe miệng và thể hiện nét mặt buồn như khi làm rơi món đồ yêu thích."
        "angry" -> "Nhíu mày và thể hiện nét mặt tức giận như khi bị lấy đồ chơi."
        "fear" -> "Mở to mắt và thể hiện nét mặt sợ hãi như vừa nghe tiếng động lớn."
        "surprise" -> "Thể hiện nét mặt thật bất ngờ như vừa được tặng quà."
        "disgust" -> "Nhăn mũi và thể hiện nét mặt ghê tởm như vừa ngửi thấy mùi khó chịu."
        else -> "Hãy thể hiện đúng cảm xúc được yêu cầu."
    }
}

private fun displayCvTitle(rawTitle: String, gameId: String): String {
    if (rawTitle.isNotBlank() && !hasEncodingIssue(rawTitle)) return rawTitle
    return if (gameId == GameUiCatalog.GAME_CV_STORY) {
        "Câu chuyện khuôn mặt"
    } else {
        "Thử thách cảm xúc"
    }
}

private fun hasEncodingIssue(value: String): Boolean {
    return value.contains("Ã") || value.contains("Â") || value.contains("Æ") || value.contains("áº") || value.contains("ðŸ")
}
