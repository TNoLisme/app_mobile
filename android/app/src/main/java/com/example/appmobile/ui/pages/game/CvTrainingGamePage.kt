package com.example.appmobile.ui.pages.game

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Size
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.webkit.WebViewAssetLoader
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.CvPromptUiItem
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.egEmotionPastelColor
import com.example.appmobile.ui.state.CvEmotionScoreState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

private const val CvRoundSeconds = 30
private const val CvRequestRoundSeconds = 20
private const val CvRequiredConfidence = 75f
private const val CvRequiredHoldMs = 1_200L
private const val CvStoryQuestionsPerLevel = 5

private enum class CvChallengeState {
    Idle,
    RequestingPermission,
    PermissionDenied,
    LoadingCamera,
    LoadingModel,
    CameraReady,
    Countdown,
    Playing,
    Success,
    Failed,
    Ended
}

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
    onFinish: () -> Unit = onBack,
    onOpenAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }
    val isStoryMode = gameId == GameUiCatalog.GAME_CV_STORY
    val roundSeconds = if (isStoryMode) CvRoundSeconds else CvRequestRoundSeconds

    val currentIndex = remember(level, gameId) { mutableIntStateOf(0) }
    val score = remember(level, gameId) { mutableIntStateOf(0) }
    val questions = remember(level, gameId) {
        mutableStateOf(
            if (isStoryMode) {
                selectCvStoryQuestions(level = level, backendQuestions = emptyList(), gameId = gameId)
            } else {
                listOf(CvQuestionUi("fallback-$gameId", defaultPrompt))
            }
        )
    }
    val sessionId = remember(level, gameId) { mutableStateOf<String?>(null) }
    val results = remember(level, gameId) { mutableStateOf<List<AnswerResultDto>>(emptyList()) }
    val summary = remember(level, gameId) { mutableStateOf<String?>(null) }
    val feedback = remember(level, gameId) { mutableStateOf<String?>(null) }
    val lastAttemptSuccess = remember(level, gameId) { mutableStateOf<Boolean?>(null) }
    val isSubmitting = remember(level, gameId) { mutableStateOf(false) }
    val roundLoading = remember(level, gameId, selectedEmotion) { mutableStateOf(true) }
    val questionStartMs = remember(level, gameId) { mutableStateOf(System.currentTimeMillis()) }
    val maxErrors = remember(level, gameId) { mutableIntStateOf(2) }
    val emotionErrors = remember(level, gameId) { mutableStateMapOf<String, Int>() }
    val learnedEmotions = remember(level, gameId) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level, gameId) { mutableStateOf<String?>(null) }
    val challengeState = remember(level, gameId) { mutableStateOf(CvChallengeState.Idle) }
    val startRequested = remember(level, gameId) { mutableStateOf(false) }
    val isRequestingCameraPermission = remember(level, gameId) { mutableStateOf(false) }
    val challengeStarted = remember(level, gameId) { mutableStateOf(false) }
    val cameraPreviewReady = remember(level, gameId) { mutableStateOf(false) }
    val detectorReady = remember(level, gameId) { mutableStateOf(false) }
    val remainingSeconds = remember(level, gameId) { mutableIntStateOf(roundSeconds) }
    val currentConfidence = remember(level, gameId) { mutableStateOf(0f) }
    val detectedEmotion = remember(level, gameId) { mutableStateOf<String?>(null) }
    val faceDetected = remember(level, gameId) { mutableStateOf(false) }
    val holdProgressMs = remember(level, gameId) { mutableStateOf(0L) }
    val lastDetectionAtMs = remember(level, gameId) { mutableStateOf<Long?>(null) }
    val playingStartedAtMs = remember(level, gameId) { mutableStateOf<Long?>(null) }
    val cameraMessage = remember(level, gameId) { mutableStateOf<String?>(null) }
    val correctHoldStartedAt = remember(level, gameId) { mutableStateOf<Long?>(null) }
    val sustainedConfidenceDuringHold = remember(level, gameId) { mutableStateOf(0f) }
    val selectedStoryGuess = remember(level, gameId) { mutableStateOf<String?>(null) }
    val storyReadyToAct = remember(level, gameId) { mutableStateOf(isStoryMode) }
    val storyGuessFeedback = remember(level, gameId) { mutableStateOf<String?>(null) }
    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isRequestingCameraPermission.value = false
        hasCameraPermission.value = granted
        cameraPreviewReady.value = false
        detectorReady.value = false
        cameraMessage.value = if (granted) null else "Bạn có thể bật lại quyền camera trong Cài đặt."
        remainingSeconds.intValue = roundSeconds
        if (granted && startRequested.value) {
            challengeState.value = CvChallengeState.LoadingCamera
            feedback.value = null
            lastAttemptSuccess.value = null
            currentConfidence.value = 0f
            detectedEmotion.value = null
            faceDetected.value = false
            holdProgressMs.value = 0L
            lastDetectionAtMs.value = null
            playingStartedAtMs.value = null
            questionStartMs.value = System.currentTimeMillis()
            challengeStarted.value = false
        } else {
            challengeState.value = CvChallengeState.PermissionDenied
            challengeStarted.value = false
        }
    }

    fun resetCurrentRound(start: Boolean = false) {
        feedback.value = null
        lastAttemptSuccess.value = null
        currentConfidence.value = 0f
        detectedEmotion.value = null
        faceDetected.value = false
        holdProgressMs.value = 0L
        lastDetectionAtMs.value = null
        playingStartedAtMs.value = null
        cameraPreviewReady.value = false
        detectorReady.value = false
        remainingSeconds.intValue = roundSeconds
        questionStartMs.value = System.currentTimeMillis()
        startRequested.value = start
        challengeStarted.value = false
        challengeState.value = if (start) CvChallengeState.LoadingCamera else CvChallengeState.Idle
        correctHoldStartedAt.value = null
        sustainedConfidenceDuringHold.value = 0f
    }

    fun finishLevel(finalResults: List<AnswerResultDto>) {
        if (isSubmitting.value || summary.value != null) return
        scope.launch {
            try {
                isSubmitting.value = true
                val response = sessionId.value?.let {
                    repository.endLevel(it, finalResults, learnedEmotions.distinct())
                }
                if (!isStoryMode) return@launch
                summary.value = if (isStoryMode) {
                    "Hoàn thành cấp độ!\nSố câu hoàn thành: ${finalResults.size}/${questions.value.size}.\nĐiểm: ${response?.score ?: score.intValue}."
                } else if (response != null) {
                    val status = if (response.passed) "Đã qua cấp độ" else "Chưa qua cấp độ"
                    "$status. Điểm: ${response.score}/100."
                } else {
                    "Hoàn thành. Điểm tạm tính: ${score.intValue}."
                }
            } catch (_: Exception) {
                if (!isStoryMode) return@launch
                summary.value = if (isStoryMode) {
                    "Hoàn thành cấp độ!\nSố câu hoàn thành: ${finalResults.size}/${questions.value.size}.\nĐiểm: ${score.intValue}."
                } else {
                    "Hoàn thành. Điểm tạm tính: ${score.intValue}."
                }
            } finally {
                isSubmitting.value = false
            }
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
        startRequested.value = false
        cameraPreviewReady.value = false
        detectorReady.value = false
        correctHoldStartedAt.value = null
        holdProgressMs.value = 0L
        lastDetectionAtMs.value = null
        playingStartedAtMs.value = null
        sustainedConfidenceDuringHold.value = 0f
        challengeState.value = if (success) CvChallengeState.Success else CvChallengeState.Failed
        feedback.value = if (success) {
            if (isStoryMode) {
                "Con đã thể hiện đúng cảm xúc của tình huống."
            } else {
                "Con đã làm mặt ${targetMeta.shortLabel} ${targetMeta.emoji}. +1 sao"
            }
        } else {
            "Mình thử lại nhé. Con đã cố gắng rất tốt."
        }
        if (!isStoryMode) {
            if (success) {
                CvEmotionScoreState.saveBestScore(
                    context = context,
                    userId = userId,
                    emotionId = reviewEmotion,
                    score = confidence
                )
            }
            finishLevel(updatedResults)
        }
    }

    fun goNextOrFinish() {
        if (currentIndex.intValue >= questions.value.lastIndex) {
            finishLevel(results.value)
            return
        }
        currentIndex.intValue += 1
        selectedStoryGuess.value = null
        storyReadyToAct.value = isStoryMode
        storyGuessFeedback.value = null
        resetCurrentRound(start = false)
    }

    fun replayCurrentLevel() {
        questions.value = if (isStoryMode) {
            selectCvStoryQuestions(
                level = level,
                backendQuestions = emptyList(),
                gameId = "$gameId-replay-${System.currentTimeMillis()}"
            )
        } else {
            questions.value
        }
        currentIndex.intValue = 0
        score.intValue = 0
        results.value = emptyList()
        summary.value = null
        emotionErrors.clear()
        learnedEmotions.clear()
        learningEmotionId.value = null
        selectedStoryGuess.value = null
        storyReadyToAct.value = isStoryMode
        storyGuessFeedback.value = null
        resetCurrentRound(start = false)
    }

    LaunchedEffect(gameId, level, userId, selectedEmotion) {
        roundLoading.value = true
        try {
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

            val filteredQuestions = if (isStoryMode) {
                selectCvStoryQuestions(
                    level = level,
                    backendQuestions = backendQuestions,
                    gameId = gameId
                )
            } else {
                selectedEmotionKey?.let { key ->
                    backendQuestions.filter { normalizeCvEmotion(it.prompt.correctAnswer) == key }
                } ?: backendQuestions
            }
            val fallbackPrompt = selectedEmotionKey?.let { key ->
                CvPromptUiItem(questionText = "", correctAnswer = key)
            } ?: defaultPrompt

        questions.value = filteredQuestions.ifEmpty {
            val fallbackQuestionId = if (!isStoryMode && selectedEmotionKey != null) {
                cvRequestFallbackQuestionId(selectedEmotionKey)
            } else {
                "fallback-$gameId-${selectedEmotionKey ?: "default"}"
            }
            listOf(CvQuestionUi(fallbackQuestionId, fallbackPrompt))
        }
            currentIndex.intValue = 0
            score.intValue = 0
            results.value = emptyList()
            summary.value = null
            emotionErrors.clear()
            learnedEmotions.clear()
            learningEmotionId.value = null
            selectedStoryGuess.value = null
            storyReadyToAct.value = isStoryMode
            storyGuessFeedback.value = null
            resetCurrentRound(start = false)
        } finally {
            roundLoading.value = false
        }
    }

    val currentQuestion = questions.value[currentIndex.intValue % questions.value.size]
    val targetEmotion = cvEmotionMeta(currentQuestion.prompt.correctAnswer)
    val promptText = displayCvPrompt(currentQuestion.prompt.questionText, targetEmotion)
    val storyPromptText = if (isStoryMode) {
        hideCvAnswerInStoryPrompt(promptText, targetEmotion)
    } else {
        promptText
    }
    val canShowCameraStep = !isStoryMode || storyReadyToAct.value
    val shouldMountCamera = challengeState.value == CvChallengeState.LoadingCamera ||
        challengeState.value == CvChallengeState.LoadingModel ||
        challengeState.value == CvChallengeState.CameraReady ||
        challengeState.value == CvChallengeState.Countdown ||
        challengeState.value == CvChallengeState.Playing
    val cameraMayStart = !roundLoading.value &&
        canShowCameraStep &&
        shouldMountCamera &&
        startRequested.value &&
        hasCameraPermission.value &&
        cameraMessage.value == null
    val cameraReady = cameraMayStart && cameraPreviewReady.value
    val detectorActive = cameraReady && detectorReady.value
    val timerActive = canShowCameraStep &&
        detectorActive &&
        challengeState.value == CvChallengeState.Playing &&
        feedback.value == null &&
        summary.value == null

    LaunchedEffect(cameraReady, detectorReady.value, challengeState.value, feedback.value, summary.value) {
        if (
            challengeState.value == CvChallengeState.LoadingCamera &&
            cameraReady &&
            feedback.value == null &&
            summary.value == null
        ) {
            challengeState.value = if (detectorReady.value) {
                CvChallengeState.CameraReady
            } else {
                CvChallengeState.LoadingModel
            }
        }
        if (
            challengeState.value == CvChallengeState.LoadingModel &&
            cameraReady &&
            detectorReady.value &&
            feedback.value == null &&
            summary.value == null
        ) {
            challengeState.value = CvChallengeState.CameraReady
        }
    }

    LaunchedEffect(challengeState.value, feedback.value, summary.value) {
        if (challengeState.value == CvChallengeState.CameraReady && feedback.value == null && summary.value == null) {
            challengeState.value = CvChallengeState.Countdown
            delay(450)
            if (challengeState.value == CvChallengeState.Countdown && feedback.value == null && summary.value == null) {
                challengeState.value = CvChallengeState.Playing
                challengeStarted.value = true
                playingStartedAtMs.value = System.currentTimeMillis()
                questionStartMs.value = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(cameraMayStart, cameraPreviewReady.value, challengeState.value, currentIndex.intValue) {
        if (cameraMayStart && challengeState.value == CvChallengeState.LoadingCamera && !cameraPreviewReady.value) {
            delay(8000)
            if (
                cameraMayStart &&
                challengeState.value == CvChallengeState.LoadingCamera &&
                !cameraPreviewReady.value &&
                feedback.value == null &&
                summary.value == null
            ) {
                cameraMessage.value = "Không mở được camera. Hãy kiểm tra quyền camera rồi thử lại."
                startRequested.value = false
                challengeStarted.value = false
                challengeState.value = CvChallengeState.Failed
                cameraPreviewReady.value = false
                detectorReady.value = false
                isRequestingCameraPermission.value = false
            }
        }
    }

    LaunchedEffect(cameraReady, detectorReady.value, challengeState.value, currentIndex.intValue) {
        if (cameraReady && challengeState.value == CvChallengeState.LoadingModel && !detectorReady.value) {
            delay(8000)
            if (
                cameraReady &&
                challengeState.value == CvChallengeState.LoadingModel &&
                !detectorReady.value &&
                feedback.value == null &&
                summary.value == null
            ) {
                cameraMessage.value = "Không tải được bộ nhận diện. Hãy thử lại."
                startRequested.value = false
                challengeStarted.value = false
                challengeState.value = CvChallengeState.Failed
                cameraPreviewReady.value = false
                detectorReady.value = false
            }
        }
    }

    LaunchedEffect(challengeState.value, feedback.value, summary.value, currentIndex.intValue) {
        if (challengeState.value != CvChallengeState.Playing || feedback.value != null || summary.value != null) {
            return@LaunchedEffect
        }
        while (
            challengeState.value == CvChallengeState.Playing &&
            feedback.value == null &&
            summary.value == null
        ) {
            delay(1000L)
            val startedAt = playingStartedAtMs.value ?: System.currentTimeMillis()
            val lastFrameAt = lastDetectionAtMs.value ?: startedAt
            if (System.currentTimeMillis() - lastFrameAt > 5000L) {
                cameraMessage.value = "Bá»™ nháº­n diá»‡n chÆ°a pháº£n há»“i. MÃ¬nh thá»­ láº¡i nhÃ©."
                recordAttempt(success = false, confidence = 0f)
                break
            }
        }
    }

    LaunchedEffect(currentIndex.intValue, challengeState.value, feedback.value, summary.value) {
        if (!timerActive) return@LaunchedEffect
        val startedAt = playingStartedAtMs.value ?: System.currentTimeMillis().also {
            playingStartedAtMs.value = it
        }
        while (
            challengeState.value == CvChallengeState.Playing &&
            feedback.value == null &&
            summary.value == null
        ) {
            val elapsedMs = System.currentTimeMillis() - startedAt
            val remainingMs = (roundSeconds * 1000L - elapsedMs).coerceAtLeast(0L)
            remainingSeconds.intValue = ((remainingMs + 999L) / 1000L).toInt()
            if (remainingMs <= 0L) {
                recordAttempt(success = false, confidence = 0f)
                break
            }
            delay(100L)
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
                progressText = if (isStoryMode) "Câu ${currentIndex.intValue + 1}/${questions.value.size}" else null,
                onBack = onBack
            )

            if (summary.value != null) {
                if (isStoryMode) {
                    CvStoryLevelSummaryCard(
                        summary = summary.value.orEmpty(),
                        onReplay = { replayCurrentLevel() },
                        onBack = onBack
                    )
                } else {
                    GameLevelSummaryCard(summary = summary.value.orEmpty(), onBack = onBack)
                }
                Spacer(modifier = Modifier.height(80.dp))
                return@Column
            }

            if (isStoryMode && !storyReadyToAct.value) {
                CvStoryGuessInChallengeCard(
                    promptLabel = promptLabel,
                    scenarioTitle = "Câu chuyện số ${currentIndex.intValue + 1}",
                    scenarioText = storyPromptText,
                    selectedEmotionId = selectedStoryGuess.value,
                    feedback = storyGuessFeedback.value,
                    onSelectEmotion = { emotionId ->
                        selectedStoryGuess.value = emotionId
                        storyGuessFeedback.value = null
                    },
                    onConfirm = {
                        val guess = selectedStoryGuess.value ?: return@CvStoryGuessInChallengeCard
                        if (guess == targetEmotion.id) {
                            storyReadyToAct.value = true
                            storyGuessFeedback.value = "Đúng rồi! Bây giờ hãy thể hiện cảm xúc này nhé."
                            resetCurrentRound(start = false)
                        } else {
                            selectedStoryGuess.value = null
                            storyGuessFeedback.value = "Chưa đúng, thử lại nhé."
                        }
                    }
                )
            } else {
                CvCameraFeedbackCard(
                hasPermission = cameraMayStart,
                cameraMessage = cameraMessage.value,
                onCameraError = { error ->
                    cameraMessage.value = error.message?.takeIf { it.isNotBlank() }
                        ?: "Không thể mở camera. Vui lòng thử lại."
                    startRequested.value = false
                    challengeStarted.value = false
                    challengeState.value = CvChallengeState.Failed
                    cameraPreviewReady.value = false
                    detectorReady.value = false
                    isRequestingCameraPermission.value = false
                },
                targetEmotion = targetEmotion,
                detectedEmotionId = detectedEmotion.value,
                confidence = currentConfidence.value,
                feedback = feedback.value,
                attemptSuccess = lastAttemptSuccess.value,
                challengeState = challengeState.value,
                faceDetected = faceDetected.value,
                holdProgress = holdProgressMs.value.toFloat() / CvRequiredHoldMs.toFloat(),
                cameraReady = cameraReady,
                detectorReady = detectorReady.value,
                challengeStarted = challengeStarted.value,
                startRequested = startRequested.value,
                isRequestingCameraPermission = isRequestingCameraPermission.value,
                    roundLoading = roundLoading.value,
                    remainingSeconds = remainingSeconds.intValue,
                    timerActive = timerActive,
                    isStoryMode = isStoryMode,
                    storyScenarioTitle = if (isStoryMode) "Tình huống ${currentIndex.intValue + 1}" else null,
                    storyScenarioText = if (isStoryMode) storyPromptText else null,
                    isLastQuestion = currentIndex.intValue >= questions.value.lastIndex,
                    isSubmitting = isSubmitting.value,
                    onCameraReady = {
                        cameraPreviewReady.value = true
                        challengeStarted.value = false
                        cameraMessage.value = null
                    },
                    onDetectorReady = {
                        detectorReady.value = true
                        cameraMessage.value = null
                    },
                    onDetection = { emotionId, confidence ->
                        if (!detectorReady.value) {
                            detectorReady.value = true
                        }
                        val now = System.currentTimeMillis()
                        val normalizedEmotion = emotionId?.takeIf { it.isNotBlank() }?.let(::normalizeCvEmotion)
                        val normalizedConfidence = confidence.coerceIn(0f, 100f)
                        val hasFaceInFrame = normalizedEmotion != null || normalizedConfidence > 0f
                        val targetRawScore = if (normalizedEmotion == targetEmotion.id) normalizedConfidence else 0f
                        val previousScore = currentConfidence.value
                        val smoothedScore = if (lastDetectionAtMs.value == null) {
                            targetRawScore
                        } else {
                            (previousScore * 0.7f) + (targetRawScore * 0.3f)
                        }.coerceIn(0f, 100f)
                        val deltaMs = (now - (lastDetectionAtMs.value ?: now)).coerceIn(120L, 700L)
                        lastDetectionAtMs.value = now
                        faceDetected.value = hasFaceInFrame
                        detectedEmotion.value = normalizedEmotion
                        currentConfidence.value = smoothedScore

                        if (
                            !isStoryMode &&
                            feedback.value == null &&
                            summary.value == null &&
                            challengeState.value == CvChallengeState.Playing
                        ) {
                            val isHoldingCorrectEmotion = hasFaceInFrame &&
                                normalizedEmotion == targetEmotion.id &&
                                smoothedScore >= CvRequiredConfidence
                            if (isHoldingCorrectEmotion) {
                                if (correctHoldStartedAt.value == null) {
                                    correctHoldStartedAt.value = now
                                    sustainedConfidenceDuringHold.value = smoothedScore
                                } else {
                                    sustainedConfidenceDuringHold.value = minOf(
                                        sustainedConfidenceDuringHold.value,
                                        smoothedScore
                                    )
                                }
                                holdProgressMs.value = (holdProgressMs.value + deltaMs).coerceAtMost(CvRequiredHoldMs)
                                if (holdProgressMs.value >= CvRequiredHoldMs) {
                                    recordAttempt(
                                        success = true,
                                        confidence = sustainedConfidenceDuringHold.value.coerceIn(0f, 100f)
                                    )
                                }
                            } else {
                                holdProgressMs.value = (holdProgressMs.value - (deltaMs / 2)).coerceAtLeast(0L)
                                if (holdProgressMs.value == 0L) {
                                    correctHoldStartedAt.value = null
                                    sustainedConfidenceDuringHold.value = 0f
                                }
                            }
                        }
                    },
                    onEmotionMatched = { confidence ->
                        if (isStoryMode) {
                            recordAttempt(success = true, confidence = confidence.coerceIn(0f, 100f))
                        }
                    },
                onStart = {
                    if (roundLoading.value) return@CvCameraFeedbackCard
                    startRequested.value = true
                    cameraPreviewReady.value = false
                    detectorReady.value = false
                    if (hasCameraPermission.value) {
                        cameraMessage.value = null
                        resetCurrentRound(start = true)
                    } else {
                        challengeState.value = CvChallengeState.RequestingPermission
                        isRequestingCameraPermission.value = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onRetry = { resetCurrentRound(start = false) },
                onMarkSuccess = { recordAttempt(success = true, confidence = 100f) },
                onNext = { goNextOrFinish() },
                onExit = {
                    challengeState.value = CvChallengeState.Ended
                    onFinish()
                }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        EmotionLearningDialog(
            emotionId = learningEmotionId.value,
            onDismiss = { learningEmotionId.value = null }
        )

    }
}

@Composable
private fun CvTopBar(title: String, progressText: String? = null, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppBackButton(onClick = onBack)
            progressText?.let { text ->
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, EgDesign.cardBorder),
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = EgDesign.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
private fun CvStoryGuessInChallengeCard(
    promptLabel: String,
    scenarioTitle: String,
    scenarioText: String,
    selectedEmotionId: String?,
    feedback: String?,
    onSelectEmotion: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val isWrongFeedback = feedback?.contains("ChÆ°a Ä‘Ãºng") == true
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(462.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF4FAFF),
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.dp, EgDesign.cardBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("ðŸ“–", fontSize = 19.sp)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = promptLabel,
                                color = EgDesign.blue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = scenarioTitle,
                                color = EgDesign.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = scenarioText,
                        color = EgDesign.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 4
                    )
                }
            }

            Text(
                text = "Chá»n cáº£m xÃºc phÃ¹ há»£p",
                color = EgDesign.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cvEmotionChoices().chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { emotion ->
                            CvStoryEmotionChoice(
                                emotion = emotion,
                                selected = emotion.id == selectedEmotionId,
                                onClick = { onSelectEmotion(emotion.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (!feedback.isNullOrBlank()) {
                    Text(
                        text = feedback,
                        color = if (isWrongFeedback) Color(0xFFBE123C) else Color(0xFF047857),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "Äá»c tÃ¬nh huá»‘ng rá»“i chá»n cáº£m xÃºc con Ä‘oÃ¡n nhÃ©.",
                        color = EgDesign.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onConfirm,
                enabled = selectedEmotionId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EgDesign.primary,
                    disabledContainerColor = Color(0xFFDDEBFF),
                    disabledContentColor = EgDesign.textSecondary
                )
            ) {
                Text("XÃ¡c nháº­n", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CvStoryScenarioCard(
    promptLabel: String,
    scenarioTitle: String,
    scenarioText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = Color(0xFFEAF7FF),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📖", fontSize = 28.sp)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = promptLabel,
                        color = EgDesign.blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = scenarioTitle,
                        color = EgDesign.textPrimary,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Text(
                text = scenarioText,
                color = EgDesign.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun CvStoryGuessCard(
    selectedEmotionId: String?,
    feedback: String?,
    onSelectEmotion: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val isWrongFeedback = feedback?.contains("Chưa đúng") == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Chọn cảm xúc phù hợp",
                color = EgDesign.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cvEmotionChoices().chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { emotion ->
                            CvStoryEmotionChoice(
                                emotion = emotion,
                                selected = emotion.id == selectedEmotionId,
                                onClick = { onSelectEmotion(emotion.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (!feedback.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isWrongFeedback) Color(0xFFFFF1F2) else Color(0xFFECFDF5),
                    border = BorderStroke(
                        1.dp,
                        if (isWrongFeedback) Color(0xFFFECACA) else Color(0xFFBBF7D0)
                    )
                ) {
                    Text(
                        text = feedback,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        color = if (isWrongFeedback) Color(0xFFBE123C) else Color(0xFF047857),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = onConfirm,
                enabled = selectedEmotionId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
            ) {
                Text("Xác nhận", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CvStoryEmotionChoice(
    emotion: CvEmotionMeta,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) EgDesign.primary else egEmotionPastelColor(emotion.id),
        border = BorderStroke(1.dp, if (selected) EgDesign.primaryDark else EgDesign.cardBorder),
        shadowElevation = if (selected) 2.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emotion.emoji, fontSize = 21.sp, lineHeight = 23.sp)
            Text(
                text = emotion.label,
                color = if (selected) Color.White else EgDesign.textPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun CvStoryReadyBanner(targetLabel: String, targetEmotion: CvEmotionMeta) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFECFDF5),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("✅", fontSize = 22.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Đúng rồi! Bây giờ hãy thể hiện cảm xúc này nhé.",
                    color = EgDesign.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 19.sp
                )
                Text(
                    text = "$targetLabel: ${targetEmotion.label}",
                    color = EgDesign.blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CvStoryLevelSummaryCard(
    summary: String,
    onReplay: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", fontSize = 42.sp)
            Text(
                text = "Hoàn thành cấp độ!",
                color = EgDesign.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = summary.lineSequence().drop(1).joinToString("\n").ifBlank { summary },
                color = EgDesign.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onReplay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
            ) {
                Text("Chơi lại", color = Color.White, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Text("Về chọn cấp độ", color = EgDesign.blue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun cvEmotionChoices(): List<CvEmotionMeta> {
    return listOf("happy", "sad", "angry", "fear", "surprise", "disgust").map(::cvEmotionMeta)
}

private fun cvRequestFallbackQuestionId(emotionId: String): String {
    return when (normalizeCvEmotion(emotionId)) {
        "happy" -> "cv-request-happy-1"
        "sad" -> "cv-request-sad-1"
        "angry" -> "cv-request-angry-1"
        "fear" -> "cv-request-fear-1"
        "surprise" -> "cv-request-surprise-1"
        "disgust" -> "cv-request-disgust-1"
        else -> "cv-request-happy-1"
    }
}

private fun selectCvStoryQuestions(
    level: Int,
    backendQuestions: List<CvQuestionUi>,
    gameId: String
): List<CvQuestionUi> {
    val backendPool = backendQuestions.distinctBy(::cvStoryQuestionKey)
    val localPool = cvStoryLocalQuestionPool(level)
    val source = if (backendPool.size >= CvStoryQuestionsPerLevel) {
        backendPool
    } else {
        (backendPool + localPool).distinctBy(::cvStoryQuestionKey)
    }
    val seed = System.currentTimeMillis() xor gameId.hashCode().toLong() xor (level * 31L)
    return source
        .shuffled(Random(seed))
        .take(CvStoryQuestionsPerLevel)
        .ifEmpty { localPool.take(CvStoryQuestionsPerLevel) }
}

private fun cvStoryQuestionKey(question: CvQuestionUi): String {
    return "${normalizeCvEmotion(question.prompt.correctAnswer)}:${question.prompt.questionText.trim().lowercase()}"
}

private fun cvStoryLocalQuestionPool(level: Int): List<CvQuestionUi> {
    val scenarios = listOf(
        "happy" to "Bạn nhỏ được cô khen vì đã giúp bạn dọn đồ chơi. Theo con, khuôn mặt của bạn ấy sẽ như thế nào?",
        "happy" to "Bạn nhỏ nhận được món quà mình thích trong ngày sinh nhật. Hãy chọn cảm xúc phù hợp với tình huống.",
        "sad" to "Bạn nhỏ làm rơi cây kem yêu thích xuống đất. Theo con, bạn ấy đang có cảm xúc nào?",
        "sad" to "Bạn nhỏ phải tạm biệt người bạn thân sau buổi chơi. Hãy chọn cảm xúc phù hợp.",
        "angry" to "Bạn nhỏ đang chơi thì bị bạn khác giật mất món đồ chơi mà không xin phép. Cảm xúc nào phù hợp nhất?",
        "angry" to "Bạn nhỏ bị xô ngã khi đang xếp hàng. Theo con, khuôn mặt của bạn ấy sẽ thể hiện điều gì?",
        "fear" to "Bạn nhỏ nghe thấy tiếng sấm rất lớn khi trời tối. Hãy chọn cảm xúc phù hợp với tình huống.",
        "fear" to "Bạn nhỏ đi lạc bố mẹ trong siêu thị đông người. Theo con, bạn ấy đang cảm thấy thế nào?",
        "surprise" to "Bạn nhỏ mở hộp quà và thấy bên trong là món đồ mình không ngờ tới. Cảm xúc nào phù hợp nhất?",
        "surprise" to "Bạn nhỏ nhìn thấy chiếc bánh sinh nhật được giấu sẵn phía sau cánh cửa. Hãy chọn cảm xúc phù hợp.",
        "disgust" to "Bạn nhỏ ngửi thấy mùi rác rất khó chịu ở gần sân chơi. Theo con, khuôn mặt của bạn ấy sẽ như thế nào?",
        "disgust" to "Bạn nhỏ nếm thử món ăn có mùi vị lạ và muốn quay mặt đi. Hãy chọn cảm xúc phù hợp."
    )
    return scenarios.mapIndexed { index, (emotion, text) ->
        CvQuestionUi(
            questionId = "local-cv-story-l$level-${index + 1}",
            prompt = CvPromptUiItem(questionText = text, correctAnswer = emotion)
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
private fun StoryScenarioInsideChallenge(title: String, text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF4FAFF),
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📖", fontSize = 24.sp)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    color = EgDesign.blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = text,
                    color = EgDesign.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 5
                )
            }
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
    challengeState: CvChallengeState,
    faceDetected: Boolean,
    holdProgress: Float,
    cameraReady: Boolean,
    detectorReady: Boolean,
    challengeStarted: Boolean,
    startRequested: Boolean,
    isRequestingCameraPermission: Boolean,
    roundLoading: Boolean,
    remainingSeconds: Int,
    timerActive: Boolean,
    isStoryMode: Boolean,
    storyScenarioTitle: String?,
    storyScenarioText: String?,
    isLastQuestion: Boolean,
    isSubmitting: Boolean,
    onCameraReady: () -> Unit,
    onDetectorReady: () -> Unit,
    onDetection: (String?, Float) -> Unit,
    onEmotionMatched: (Float) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onMarkSuccess: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val detectedMeta = detectedEmotionId?.let(::cvEmotionMeta)
    val hasCameraError = !cameraMessage.isNullOrBlank()
    val cameraOpening = startRequested && hasPermission && !cameraReady && !hasCameraError
    val detectorPreparing = startRequested && cameraReady && !detectorReady && !hasCameraError
    val title = when {
        hasCameraError -> "Không mở được camera"
        roundLoading -> "Đang chuẩn bị lượt..."
        isRequestingCameraPermission -> "Đang chuẩn bị camera..."
        !startRequested -> "Sẵn sàng thử thách"
        cameraOpening -> "Đang mở camera..."
        detectorPreparing -> "Đang chuẩn bị nhận diện..."
        !cameraReady -> "Chưa bật camera"
        attemptSuccess == true -> "🎉 Đúng rồi!"
        attemptSuccess == false -> "Thử lại nhé"
        !challengeStarted -> "Sẵn sàng thử thách"
        confidence >= CvRequiredConfidence -> "Sắp đúng rồi!"
        else -> "Đang nhận diện..."
    }
    val subtitle = when {
        hasCameraError -> cameraMessage.orEmpty()
        roundLoading -> "Đợi một chút để tải lượt chơi."
        isRequestingCameraPermission -> "Vui lòng cấp quyền camera để bắt đầu."
        !startRequested -> "Nhấn bắt đầu và thể hiện cảm xúc nhé."
        cameraOpening -> "Đợi một chút để camera sẵn sàng."
        detectorPreparing -> "Camera đã mở, đang tải bộ nhận diện."
        !cameraReady -> "Hãy cấp quyền camera để bắt đầu."
        attemptSuccess == true -> feedback ?: "Bạn đã thể hiện cảm xúc ${targetEmotion.label}."
        attemptSuccess == false -> feedback ?: "Hãy làm lại biểu cảm."
        !challengeStarted -> "Nhấn bắt đầu và thể hiện cảm xúc nhé."
        confidence >= CvRequiredConfidence -> "Giữ biểu cảm thêm chút nữa."
        else -> "Hãy giữ khuôn mặt trong khung hình."
    }
    val displayTitle = if (isStoryMode) {
        when {
            hasCameraError -> "Không mở được camera"
            roundLoading -> "Đang chuẩn bị lượt..."
            isRequestingCameraPermission -> "Đang chuẩn bị camera..."
            !startRequested -> "Đọc tình huống"
            cameraOpening -> "Đang mở camera..."
            detectorPreparing -> "Đang chuẩn bị nhận diện..."
            !cameraReady -> "Chưa bật camera"
            attemptSuccess == true -> "🎉 Đúng rồi!"
            attemptSuccess == false -> "Thử lại nhé"
            !challengeStarted -> "Đọc tình huống"
            confidence >= CvRequiredConfidence -> "Sắp đúng rồi!"
            else -> "Đang nhận diện..."
        }
    } else {
        title
    }
    val displaySubtitle = if (isStoryMode) {
        when {
            hasCameraError -> cameraMessage.orEmpty()
            roundLoading -> "Đợi một chút để tải lượt chơi."
            isRequestingCameraPermission -> "Vui lòng cấp quyền camera để bắt đầu."
            !startRequested -> "Bé hãy đoán cảm xúc trong tình huống rồi thể hiện nhé."
            cameraOpening -> "Đợi một chút để camera sẵn sàng."
            detectorPreparing -> "Camera đã mở, đang tải bộ nhận diện."
            !cameraReady -> "Hãy cấp quyền camera để bắt đầu."
            attemptSuccess == true -> feedback ?: "Con đã thể hiện đúng cảm xúc của tình huống."
            attemptSuccess == false -> feedback ?: "Hãy thử lại biểu cảm."
            !challengeStarted -> "Bé hãy đoán cảm xúc trong tình huống rồi thể hiện nhé."
            confidence >= CvRequiredConfidence -> "Giữ biểu cảm thêm chút nữa."
            else -> "Hãy giữ khuôn mặt trong khung hình."
        }
    } else {
        subtitle
    }
    val cameraHeight = if (isStoryMode) 220.dp else 280.dp
    val feedbackIcon = when {
        detectedMeta != null -> detectedMeta.emoji
        isStoryMode -> "📖"
        else -> targetEmotion.emoji
    }

    val activeLamp = cvConfidenceLamp(confidence = confidence, attemptSuccess = attemptSuccess)
    val childFeedbackLevel = when {
        attemptSuccess == true || challengeState == CvChallengeState.Success -> "Đúng rồi!"
        challengeState == CvChallengeState.Failed || challengeState == CvChallengeState.Ended -> "Thử lại"
        challengeState == CvChallengeState.LoadingCamera || challengeState == CvChallengeState.LoadingModel ||
            challengeState == CvChallengeState.CameraReady || challengeState == CvChallengeState.Countdown -> "Đang chuẩn bị"
        challengeState != CvChallengeState.Playing -> "Bắt đầu"
        !faceDetected -> "Đưa mặt vào giữa khung nhé"
        confidence >= CvRequiredConfidence -> "Đúng rồi!"
        confidence >= CvRequiredConfidence * 0.55f -> "Gần đúng rồi"
        else -> "Mình thử lại nhé"
    }
    val childDisplayTitle = when {
        hasCameraError -> if (challengeState == CvChallengeState.PermissionDenied) "Chưa bật camera" else "Không mở được camera"
        roundLoading -> "Đang chuẩn bị lượt..."
        isRequestingCameraPermission || challengeState == CvChallengeState.RequestingPermission -> "Đang xin quyền camera..."
        challengeState == CvChallengeState.PermissionDenied -> "Chưa bật camera"
        challengeState == CvChallengeState.LoadingCamera || cameraOpening -> "Đang mở camera..."
        challengeState == CvChallengeState.LoadingModel || detectorPreparing -> "Đang tải bộ nhận diện..."
        challengeState == CvChallengeState.Success -> "Tuyệt vời!"
        challengeState == CvChallengeState.Failed || challengeState == CvChallengeState.Ended -> "Mình thử lại nhé"
        challengeState == CvChallengeState.Playing -> childFeedbackLevel
        else -> if (isStoryMode) "Đọc tình huống" else "Sẵn sàng"
    }
    val childDisplaySubtitle = when {
        hasCameraError -> cameraMessage.orEmpty()
        roundLoading -> "Đợi một chút để tải lượt chơi."
        isRequestingCameraPermission || challengeState == CvChallengeState.RequestingPermission -> "Con cần mở camera để bắt đầu."
        challengeState == CvChallengeState.PermissionDenied -> "Hãy bấm mở camera để cấp quyền."
        challengeState == CvChallengeState.LoadingCamera || cameraOpening -> "Đang mở camera..."
        challengeState == CvChallengeState.LoadingModel -> "Camera đã sẵn sàng, đang tải bộ nhận diện."
        detectorPreparing -> "Camera đã mở, đang chuẩn bị nhận diện."
        challengeState == CvChallengeState.Success -> feedback ?: "Con làm tốt lắm!"
        challengeState == CvChallengeState.Failed || challengeState == CvChallengeState.Ended -> feedback ?: "Con đã cố gắng rất tốt."
        challengeState == CvChallengeState.Playing && !faceDetected -> "Đưa mặt vào giữa khung nhé."
        challengeState == CvChallengeState.Playing -> cvEmotionGuidance(targetEmotion.id)
        isStoryMode -> "Bé hãy đoán cảm xúc trong tình huống rồi thể hiện nhé."
        else -> "Con hãy làm mặt ${targetEmotion.shortLabel} nhé ${targetEmotion.emoji}"
    }
    val childFeedbackIcon = if (isStoryMode) "📖" else targetEmotion.emoji
    val childActiveLamp = when {
        attemptSuccess == true || holdProgress >= 0.99f -> 2
        childFeedbackLevel == "Gần đúng rồi" -> 1
        else -> 0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isStoryMode) 560.dp else 462.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isStoryMode && !storyScenarioText.isNullOrBlank()) {
                StoryScenarioInsideChallenge(
                    title = storyScenarioTitle ?: "Tình huống",
                    text = storyScenarioText
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cameraHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF102A43)),
                contentAlignment = Alignment.Center
            ) {
                if (hasPermission && startRequested && feedback == null && cameraMessage == null) {
                    CvNativeEmotionCamera(
                        modifier = Modifier.fillMaxSize(),
                        targetEmotionId = targetEmotion.id,
                        onReady = onCameraReady,
                        onDetectorReady = onDetectorReady,
                        onDetection = onDetection,
                        onMatched = onEmotionMatched,
                        onError = { message -> onCameraError(IllegalStateException(message)) }
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
                                hasCameraError -> "Không mở được camera"
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
                                text = cameraMessage ?: if (!startRequested) {
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
                if (!isStoryMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
                            CvStatusLamps(activeLamp = childActiveLamp)
                        }
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp),
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
                        Text(childFeedbackIcon, fontSize = 30.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (startRequested) {
                        Text(
                            childDisplayTitle,
                            color = EgDesign.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = childDisplaySubtitle,
                        color = EgDesign.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2
                    )
                    if (!isStoryMode) {
                    Text(
                        text = "Mục tiêu: ${targetEmotion.label}",
                        color = EgDesign.blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                }
                if (!isStoryMode) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = when (childFeedbackLevel) {
                            "Đúng rồi!" -> Color(0xFFDCFCE7)
                            "Gần đúng rồi" -> Color(0xFFFEF9C3)
                            else -> Color(0xFFEAF7FF)
                        },
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Text(
                            text = childFeedbackLevel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            color = EgDesign.blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (!isStoryMode) {
                LinearProgressIndicator(
                    progress = {
                        when {
                            challengeState == CvChallengeState.Success -> 1f
                            challengeState == CvChallengeState.Playing -> holdProgress.coerceIn(0f, 1f)
                            else -> 0f
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = when {
                        challengeState == CvChallengeState.Success -> Color(0xFF22C55E)
                        holdProgress >= 0.5f -> Color(0xFFFACC15)
                        else -> EgDesign.primary
                    },
                    trackColor = Color(0xFFE2E8F0)
                )
            }

            CvChallengeActionRow(
                cameraReady = cameraReady,
                challengeStarted = challengeStarted,
                startRequested = startRequested,
                isRequestingCameraPermission = isRequestingCameraPermission,
                roundLoading = roundLoading,
                cameraOpening = cameraOpening,
                detectorPreparing = detectorPreparing,
                cameraMessage = cameraMessage,
                challengeState = challengeState,
                feedback = feedback,
                attemptSuccess = attemptSuccess,
                isStoryMode = isStoryMode,
                isLastQuestion = isLastQuestion,
                isSubmitting = isSubmitting,
                onStart = onStart,
                onRetry = onRetry,
                onMarkSuccess = onMarkSuccess,
                onNext = onNext,
                onExit = onExit
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CvNativeEmotionCamera(
    modifier: Modifier = Modifier,
    targetEmotionId: String,
    onReady: () -> Unit,
    onDetectorReady: () -> Unit,
    onDetection: (String?, Float) -> Unit,
    onMatched: (Float) -> Unit,
    onError: (String) -> Unit
) {
    val detectorWebView = remember { mutableStateOf<WebView?>(null) }
    val detectorReadyForFrames = remember(targetEmotionId) { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val frameInFlight = remember { AtomicBoolean(false) }

    Box(modifier = modifier) {
        CameraXFramePreview(
            modifier = Modifier.fillMaxSize(),
            onCameraReady = onReady,
            onCameraError = { error -> onError(error.message ?: "Không thể mở camera.") },
            onFrame = { base64Frame, rotationDegrees ->
                if (!detectorReadyForFrames.value || !frameInFlight.compareAndSet(false, true)) {
                    return@CameraXFramePreview
                }
                mainHandler.post {
                    val webView = detectorWebView.value
                    if (webView == null || !detectorReadyForFrames.value) {
                        frameInFlight.set(false)
                        return@post
                    }
                    runCatching {
                        webView.evaluateJavascript(
                            "window.detectCvFrame && window.detectCvFrame('data:image/jpeg;base64,$base64Frame', $rotationDegrees);"
                        ) {
                            frameInFlight.set(false)
                        }
                    }.onFailure {
                        frameInFlight.set(false)
                    }
                }
            }
        )
        CvFrameDetectorWebView(
            modifier = Modifier.size(1.dp),
            targetEmotionId = targetEmotionId,
            onWebViewReady = { webView -> detectorWebView.value = webView },
            onDetectorReady = {
                detectorReadyForFrames.value = true
                onDetectorReady()
            },
            onDetection = onDetection,
            onMatched = onMatched,
            onError = onError
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CvFrameDetectorWebView(
    modifier: Modifier = Modifier,
    targetEmotionId: String,
    onWebViewReady: (WebView) -> Unit,
    onDetectorReady: () -> Unit,
    onDetection: (String?, Float) -> Unit,
    onMatched: (Float) -> Unit,
    onError: (String) -> Unit
) {
    val detectorReadyCallback = rememberUpdatedState(onDetectorReady)
    val detectionCallback = rememberUpdatedState(onDetection)
    val matchedCallback = rememberUpdatedState(onMatched)
    val errorCallback = rememberUpdatedState(onError)
    val webViewRef = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(targetEmotionId) {
        onDispose {
            webViewRef[0]?.let { webView ->
                runCatching { webView.evaluateJavascript("window.stopCvDetector && window.stopCvDetector();", null) }
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
                webViewRef[0] = null
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewRef[0] = this
                onWebViewReady(this)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                addJavascriptInterface(
                    CvEmotionJsBridge(
                        onReady = {},
                        onDetectorReady = { detectorReadyCallback.value.invoke() },
                        onDetection = { emotion, confidence -> detectionCallback.value.invoke(emotion, confidence) },
                        onMatched = { confidence -> matchedCallback.value.invoke(confidence) },
                        onError = { message -> errorCallback.value.invoke(message) }
                    ),
                    "AndroidCvBridge"
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                        if (consoleMessage.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                            errorCallback.value.invoke(consoleMessage.message())
                        }
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: android.webkit.WebResourceRequest
                    ): android.webkit.WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            """
                            (function() {
                              if (window.startFrameDetector) {
                                window.startFrameDetector('$targetEmotionId');
                              } else if (window.AndroidCvBridge) {
                                window.AndroidCvBridge.onError('Không tải được bộ nhận diện camera.');
                              }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/cv_emotion_detector.html")
            }
        }
    )
}

@Composable
private fun CvEmotionDetectorCamera(
    modifier: Modifier = Modifier,
    targetEmotionId: String,
    onReady: () -> Unit,
    onDetectorReady: () -> Unit,
    onDetection: (String?, Float) -> Unit,
    onMatched: (Float) -> Unit,
    onError: (String) -> Unit
) {
    val readyCallback = rememberUpdatedState(onReady)
    val detectorReadyCallback = rememberUpdatedState(onDetectorReady)
    val detectionCallback = rememberUpdatedState(onDetection)
    val matchedCallback = rememberUpdatedState(onMatched)
    val errorCallback = rememberUpdatedState(onError)
    val webViewRef = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(targetEmotionId) {
        onDispose {
            webViewRef[0]?.let { webView ->
                runCatching { webView.evaluateJavascript("window.stopCvDetector && window.stopCvDetector();", null) }
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
                webViewRef[0] = null
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewRef[0] = this
                setBackgroundColor(android.graphics.Color.rgb(16, 42, 67))
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadsImagesAutomatically = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                addJavascriptInterface(
                    CvEmotionJsBridge(
                        onReady = { readyCallback.value.invoke() },
                        onDetectorReady = { detectorReadyCallback.value.invoke() },
                        onDetection = { emotion, confidence -> detectionCallback.value.invoke(emotion, confidence) },
                        onMatched = { confidence -> matchedCallback.value.invoke(confidence) },
                        onError = { message -> errorCallback.value.invoke(message) }
                    ),
                    "AndroidCvBridge"
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        val videoResources = request.resources.filter {
                            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        }.toTypedArray()
                        if (videoResources.isEmpty()) {
                            request.deny()
                            errorCallback.value.invoke("WebView không được cấp quyền camera.")
                        } else {
                            request.grant(videoResources)
                        }
                    }

                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                        if (consoleMessage.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                            errorCallback.value.invoke(consoleMessage.message())
                        }
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: android.webkit.WebResourceRequest
                    ): android.webkit.WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            """
                            (function() {
                              if (window.startCvDetector) {
                                window.startCvDetector('$targetEmotionId');
                              } else if (window.AndroidCvBridge) {
                                window.AndroidCvBridge.onError('Không tải được bộ nhận diện camera.');
                              }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                        error: android.webkit.WebResourceError
                    ) {
                        if (request.isForMainFrame) {
                            errorCallback.value.invoke(
                                error.description?.toString() ?: "Không tải được camera."
                            )
                        }
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/cv_emotion_detector.html")
            }
        }
    )
}

private class CvEmotionJsBridge(
    private val onReady: () -> Unit,
    private val onDetectorReady: () -> Unit,
    private val onDetection: (String?, Float) -> Unit,
    private val onMatched: (Float) -> Unit,
    private val onError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        mainHandler.post(onReady)
    }

    @JavascriptInterface
    fun onDetectorReady() {
        mainHandler.post(onDetectorReady)
    }

    @JavascriptInterface
    fun onDetection(emotionId: String?, confidence: Int) {
        mainHandler.post {
            onDetection(emotionId?.takeIf { it.isNotBlank() }, confidence.toFloat())
        }
    }

    @JavascriptInterface
    fun onMatched(confidence: Int) {
        mainHandler.post {
            onMatched(confidence.toFloat())
        }
    }

    @JavascriptInterface
    fun onError(message: String?) {
        mainHandler.post {
            onError(message ?: "Không thể mở camera.")
        }
    }
}

@Composable
private fun CameraXFramePreview(
    modifier: Modifier = Modifier,
    onCameraReady: () -> Unit,
    onCameraError: (Throwable) -> Unit,
    onFrame: (String, Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: Executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val readyCallback = rememberUpdatedState(onCameraReady)
    val frameCallback = rememberUpdatedState(onFrame)
    val errorCallback = rememberUpdatedState(onCameraError)
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
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(320, 240))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(
                                analyzerExecutor,
                                CvFrameAnalyzer { base64Frame, rotationDegrees ->
                                    frameCallback.value.invoke(base64Frame, rotationDegrees)
                                }
                            )
                        }
                    val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    previewView.post { readyCallback.value.invoke() }
                }.onFailure { error ->
                    errorCallback.value.invoke(error)
                }
            },
            mainExecutor
        )
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private class CvFrameAnalyzer(
    private val onFrame: (String, Int) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastFrameMs = 0L

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastFrameMs < 900L) {
            image.close()
            return
        }
        lastFrameMs = now
        try {
            runCatching { imageProxyToBase64Jpeg(image) }.getOrNull()?.let { base64 ->
                onFrame(base64, image.imageInfo.rotationDegrees)
            }
        } finally {
            image.close()
        }
    }
}

private fun imageProxyToBase64Jpeg(image: ImageProxy): String? {
    if (image.format != ImageFormat.YUV_420_888) return null
    val nv21 = yuv420ToNv21(image)
    val output = ByteArrayOutputStream()
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 38, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private fun yuv420ToNv21(image: ImageProxy): ByteArray {
    val width = image.width
    val height = image.height
    val output = ByteArray(width * height * 3 / 2)
    val planes = image.planes

    var outputOffset = 0
    val yPlane = planes[0]
    val yBuffer = yPlane.buffer
    for (row in 0 until height) {
        val rowOffset = row * yPlane.rowStride
        for (col in 0 until width) {
            output[outputOffset++] = yBuffer.get(rowOffset + col * yPlane.pixelStride)
        }
    }

    val uPlane = planes[1]
    val vPlane = planes[2]
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val chromaWidth = width / 2
    val chromaHeight = height / 2
    for (row in 0 until chromaHeight) {
        val uRowOffset = row * uPlane.rowStride
        val vRowOffset = row * vPlane.rowStride
        for (col in 0 until chromaWidth) {
            output[outputOffset++] = vBuffer.get(vRowOffset + col * vPlane.pixelStride)
            output[outputOffset++] = uBuffer.get(uRowOffset + col * uPlane.pixelStride)
        }
    }
    return output
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
    val activeLamp = cvConfidenceLamp(confidence = confidence, attemptSuccess = attemptSuccess)

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

private fun cvConfidenceLamp(confidence: Float, attemptSuccess: Boolean?): Int {
    return when {
        attemptSuccess == true -> 2
        confidence >= CvRequiredConfidence -> 2
        confidence >= 30f -> 1
        else -> 0
    }
}

private fun cvEmotionGuidance(emotionId: String): String {
    return when (normalizeCvEmotion(emotionId)) {
        "happy" -> "Con hãy cười, kéo khóe miệng lên nhé."
        "sad" -> "Con thử làm mặt buồn, mắt nhìn xuống một chút."
        "angry" -> "Con hãy nhíu mày lại nhé."
        "fear" -> "Con mở to mắt và miệng hơi mở nhé."
        "surprise" -> "Con mở to mắt và miệng nhé."
        "disgust" -> "Con làm mặt không thích nhé."
        else -> "Con hãy nhìn vào camera và thử biểu cảm nhé."
    }
}

@Composable
private fun CvChallengeActionRow(
    cameraReady: Boolean,
    challengeStarted: Boolean,
    startRequested: Boolean,
    isRequestingCameraPermission: Boolean,
    roundLoading: Boolean,
    cameraOpening: Boolean,
    detectorPreparing: Boolean,
    cameraMessage: String?,
    challengeState: CvChallengeState,
    feedback: String?,
    attemptSuccess: Boolean?,
    isStoryMode: Boolean,
    isLastQuestion: Boolean,
    isSubmitting: Boolean,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onMarkSuccess: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (feedback == null) {
            val preparing = roundLoading ||
                isRequestingCameraPermission ||
                cameraOpening ||
                detectorPreparing ||
                challengeState == CvChallengeState.LoadingCamera ||
                challengeState == CvChallengeState.LoadingModel ||
                challengeState == CvChallengeState.CameraReady ||
                challengeState == CvChallengeState.Countdown
            when {
                preparing -> Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAF7FF))
                ) {
                    Text(
                        when {
                            roundLoading -> "Đang chuẩn bị lượt..."
                            challengeState == CvChallengeState.LoadingModel || detectorPreparing -> "Đang tải bộ nhận diện..."
                            cameraOpening -> "Đang mở camera..."
                            else -> "Đang chuẩn bị nhận diện..."
                        },
                        color = EgDesign.blue,
                        fontWeight = FontWeight.Bold
                    )
                }
                !cameraMessage.isNullOrBlank() || challengeState == CvChallengeState.PermissionDenied -> Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                ) {
                    Text("Mở camera", color = Color.White, fontWeight = FontWeight.Bold)
                }
                !cameraReady || !challengeStarted -> Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                ) {
                    Text("Bắt đầu", color = Color.White, fontWeight = FontWeight.Bold)
                }
                else -> OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Text("Bỏ qua", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                }
            }
        } else if (!isStoryMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Text("Chơi lại", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                ) {
                    Text(if (isSubmitting) "Đang lưu..." else "Tiếp tục", color = Color.White, fontWeight = FontWeight.Bold)
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
                        isLastQuestion -> "Xem kết quả"
                        else -> "Câu tiếp theo"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CvActionRow(
    cameraReady: Boolean,
    challengeStarted: Boolean,
    startRequested: Boolean,
    isRequestingCameraPermission: Boolean,
    roundLoading: Boolean,
    cameraOpening: Boolean,
    detectorPreparing: Boolean,
    cameraMessage: String?,
    challengeState: CvChallengeState,
    feedback: String?,
    attemptSuccess: Boolean?,
    isStoryMode: Boolean,
    isLastQuestion: Boolean,
    isSubmitting: Boolean,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onMarkSuccess: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (feedback == null) {
            if (roundLoading || isRequestingCameraPermission || cameraOpening || detectorPreparing) {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = false
                ) {
                    Text(
                        when {
                            roundLoading -> "Đang chuẩn bị lượt..."
                            detectorPreparing -> "Đang chuẩn bị nhận diện..."
                            cameraOpening -> "Đang mở camera..."
                            else -> "Đang chuẩn bị camera..."
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (!cameraMessage.isNullOrBlank()) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                ) {
                    Text("Thử lại", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else if (!cameraReady) {
                if (startRequested && challengeStarted) {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = false
                    ) {
                        Text("Đang chuẩn bị camera...", fontWeight = FontWeight.Bold)
                    }
                } else {
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
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Text("Thử lại", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = if (isStoryMode) onNext else onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
            ) {
                Text(
                    when {
                        isSubmitting -> "Đang lưu..."
                        !isStoryMode -> "Kết thúc lượt"
                        isLastQuestion -> "Xem kết quả"
                        else -> if (isStoryMode) "Câu tiếp theo" else "Lượt tiếp theo"
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

private fun hideCvAnswerInStoryPrompt(prompt: String, emotion: CvEmotionMeta): String {
    val answerWords = when (emotion.id) {
        "happy" -> listOf("vui vẻ", "vui", "hạnh phúc")
        "sad" -> listOf("buồn bã", "buồn")
        "angry" -> listOf("tức giận", "giận dữ", "giận")
        "fear" -> listOf("sợ hãi", "sợ")
        "surprise" -> listOf("ngạc nhiên")
        "disgust" -> listOf("ghê tởm")
        else -> emptyList()
    }
    return answerWords.fold(prompt) { current, word ->
        current.replace(Regex(Regex.escape(word), RegexOption.IGNORE_CASE), "phù hợp")
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
