package com.example.appmobile.ui.pages.game

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.util.Size
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewAssetLoader
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.remote.dto.GameContentDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.CvPromptUiItem
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.egEmotionPastelColor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

private const val CvRoundSeconds = 30
private const val CvRequestRoundSeconds = 20
private const val CvRequiredConfidence = 75f
private const val CvRequiredHoldMs = 5_000L
private const val CvStoryQuestionsPerLevel = 5
private const val CvLogTag = "CvChallenge"
private const val CvStoryCheckpointPref = "cv_story_checkpoint"
private const val CvStoryCheckpointTtlMs = 24L * 60L * 60L * 1000L

private enum class CvChallengeState {
    Idle,
    RequestingPermission,
    PermissionDenied,
    CameraStarting,
    SearchingFace,
    FaceDetected,
    LoadingCamera,
    LoadingModel,
    CameraReady,
    Countdown,
    Detecting,
    Playing,
    Success,
    Timeout,
    Error,
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

private data class CvStoryCheckpoint(
    val sessionId: String?,
    val score: Int,
    val maxErrors: Int,
    val currentIndex: Int,
    val questions: List<CvQuestionUi>,
    val results: List<AnswerResultDto>,
    val savedAtMs: Long
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val userId = remember {
        FirebaseAuth.getInstance().currentUser?.uid
            ?: AppSession.getBackendUserId(context)
            ?: AppSession.currentBackendUserId()
            ?: "local-player"
    }
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
                listOf(
                    CvQuestionUi(
                        questionId = "loading-$gameId",
                        prompt = CvPromptUiItem(
                            questionText = "Đang tải tình huống...",
                            correctAnswer = defaultPrompt.correctAnswer
                        )
                    )
                )
            } else {
                listOf(CvQuestionUi("fallback-$gameId", defaultPrompt))
            }
        )
    }
    val sessionId = remember(level, gameId) { mutableStateOf<String?>(null) }
    val backendSessionClosed = remember(level, gameId) { mutableStateOf(false) }
    val abandoningSession = remember(level, gameId) { mutableStateOf(false) }
    val results = remember(level, gameId) { mutableStateOf<List<AnswerResultDto>>(emptyList()) }
    val summary = remember(level, gameId) { mutableStateOf<String?>(null) }
    val requestResultSummary = remember(level, gameId) { mutableStateOf<String?>(null) }
    val feedback = remember(level, gameId) { mutableStateOf<String?>(null) }
    val lastAttemptSuccess = remember(level, gameId) { mutableStateOf<Boolean?>(null) }
    val isSubmitting = remember(level, gameId) { mutableStateOf(false) }
    val roundLoading = remember(level, gameId, selectedEmotion) { mutableStateOf(false) }
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
    val countdownValue = remember(level, gameId) { mutableIntStateOf(3) }
    val currentConfidence = remember(level, gameId) { mutableStateOf(0f) }
    val detectedConfidence = remember(level, gameId) { mutableStateOf(0f) }
    val highestConfidence = remember(level, gameId) { mutableStateOf(0f) }
    val completedTimeSeconds = remember(level, gameId) { mutableIntStateOf(0) }
    val detectedEmotion = remember(level, gameId) { mutableStateOf<String?>(null) }
    val faceDetected = remember(level, gameId) { mutableStateOf(false) }
    val holdProgressMs = remember(level, gameId) { mutableStateOf(0L) }
    val holdConfidenceWeightedSum = remember(level, gameId) { mutableStateOf(0f) }
    val roundConfidenceWeightedSum = remember(level, gameId) { mutableStateOf(0f) }
    val roundConfidenceDurationMs = remember(level, gameId) { mutableStateOf(0L) }
    val lastDetectionAtMs = remember(level, gameId) { mutableStateOf<Long?>(null) }
    val lastStrongTargetAtMs = remember(level, gameId) { mutableStateOf<Long?>(null) }
    val playingStartedAtMs = remember(level, gameId) { mutableStateOf<Long?>(null) }
    val cameraMessage = remember(level, gameId) { mutableStateOf<String?>(null) }
    val correctHoldStartedAt = remember(level, gameId) { mutableStateOf<Long?>(null) }
    val sustainedConfidenceDuringHold = remember(level, gameId) { mutableStateOf(0f) }
    val countdownRunId = remember(level, gameId) { mutableIntStateOf(0) }
    val showExitConfirm = remember(level, gameId) { mutableStateOf(false) }
    val showStoryResumeDialog = remember(level, gameId, selectedEmotion) { mutableStateOf(false) }
    val pendingStoryCheckpoint = remember(level, gameId, selectedEmotion) { mutableStateOf<CvStoryCheckpoint?>(null) }
    val storyEntryDecisionResolved = remember(level, gameId, selectedEmotion) { mutableStateOf(!isStoryMode) }
    val shouldLoadSessionFromBackend = remember(level, gameId, selectedEmotion) { mutableStateOf(!isStoryMode) }
    val searchingFaceLong = remember(level, gameId) { mutableStateOf(false) }
    val challengeSessionId = remember(level, gameId) { mutableStateOf(0L) }
    val challengeCompleted = remember(level, gameId) { mutableStateOf(false) }
    val detectionActive = remember(level, gameId) { mutableStateOf(false) }
    val screenDisposed = remember(level, gameId) { mutableStateOf(false) }
    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun persistStoryCheckpoint(force: Boolean = false) {
        if (!isStoryMode || !storyEntryDecisionResolved.value) return
        if (summary.value != null || backendSessionClosed.value) {
            clearCvStoryCheckpoint(
                context = context,
                userId = userId,
                gameId = gameId,
                level = level
            )
            return
        }
        if (questions.value.isEmpty()) return
        val hasProgress = results.value.isNotEmpty() ||
            currentIndex.intValue > 0 ||
            startRequested.value ||
            challengeStarted.value ||
            challengeState.value != CvChallengeState.Idle ||
            force
        if (!hasProgress) return
        val checkpoint = CvStoryCheckpoint(
            sessionId = sessionId.value?.takeIf { it.isNotBlank() },
            score = score.intValue.coerceAtLeast(0),
            maxErrors = maxErrors.intValue.coerceAtLeast(1),
            currentIndex = currentIndex.intValue.coerceIn(
                0,
                (questions.value.size - 1).coerceAtLeast(0)
            ),
            questions = questions.value,
            results = results.value,
            savedAtMs = System.currentTimeMillis()
        )
        saveCvStoryCheckpoint(
            context = context,
            userId = userId,
            gameId = gameId,
            level = level,
            checkpoint = checkpoint
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isRequestingCameraPermission.value = false
        hasCameraPermission.value = granted
        cameraPreviewReady.value = false
        detectorReady.value = false
        cameraMessage.value = if (granted) null else "App cần quyền camera để chơi thử thách này."
        remainingSeconds.intValue = roundSeconds
        if (granted && startRequested.value) {
            challengeSessionId.value = System.currentTimeMillis()
            challengeCompleted.value = false
            detectionActive.value = !isStoryMode
            Log.d(CvLogTag, "permissionGranted cameraStarting sessionId=${challengeSessionId.value}")
            challengeState.value = if (isStoryMode) CvChallengeState.LoadingCamera else CvChallengeState.CameraStarting
            feedback.value = null
            lastAttemptSuccess.value = null
            currentConfidence.value = 0f
            detectedConfidence.value = 0f
            highestConfidence.value = 0f
            completedTimeSeconds.intValue = 0
            detectedEmotion.value = null
            faceDetected.value = false
            holdProgressMs.value = 0L
            holdConfidenceWeightedSum.value = 0f
            roundConfidenceWeightedSum.value = 0f
            roundConfidenceDurationMs.value = 0L
            lastDetectionAtMs.value = null
            lastStrongTargetAtMs.value = null
            playingStartedAtMs.value = null
            questionStartMs.value = System.currentTimeMillis()
            challengeStarted.value = false
        } else {
            detectionActive.value = false
            challengeState.value = CvChallengeState.PermissionDenied
            challengeStarted.value = false
            startRequested.value = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission.value = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (
                    !isStoryMode &&
                    startRequested.value &&
                    !challengeCompleted.value &&
                    challengeState.value in setOf(
                        CvChallengeState.SearchingFace,
                        CvChallengeState.FaceDetected,
                        CvChallengeState.Countdown,
                        CvChallengeState.Detecting
                    )
                ) {
                    detectionActive.value = true
                    challengeStarted.value = false
                    countdownValue.intValue = 3
                    if (challengeState.value != CvChallengeState.SearchingFace) {
                        challengeState.value = CvChallengeState.SearchingFace
                    }
                    Log.d(CvLogTag, "lifecycleResume searchingFace sessionId=${challengeSessionId.value}")
                }
            } else if (event == Lifecycle.Event.ON_STOP && !isStoryMode) {
                detectionActive.value = false
                Log.d(CvLogTag, "lifecycleStop pauseDetection sessionId=${challengeSessionId.value}")
            } else if (event == Lifecycle.Event.ON_STOP && isStoryMode) {
                persistStoryCheckpoint(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(level, gameId) {
        screenDisposed.value = false
        onDispose {
            persistStoryCheckpoint(force = true)
            screenDisposed.value = true
            detectionActive.value = false
            challengeCompleted.value = true
            challengeSessionId.value += 1L
            Log.d(CvLogTag, "onDispose cleanupChallenge sessionId=${challengeSessionId.value}")
        }
    }

    fun resetCurrentRound(start: Boolean = false) {
        val newSessionId = System.currentTimeMillis()
        challengeSessionId.value = newSessionId
        challengeCompleted.value = false
        detectionActive.value = start && !isStoryMode
        Log.d(CvLogTag, "startChallenge sessionId=$newSessionId active=${detectionActive.value}")
        feedback.value = null
        lastAttemptSuccess.value = null
        currentConfidence.value = 0f
        detectedConfidence.value = 0f
        highestConfidence.value = 0f
        completedTimeSeconds.intValue = 0
        detectedEmotion.value = null
        faceDetected.value = false
        holdProgressMs.value = 0L
        holdConfidenceWeightedSum.value = 0f
        roundConfidenceWeightedSum.value = 0f
        roundConfidenceDurationMs.value = 0L
        lastDetectionAtMs.value = null
        lastStrongTargetAtMs.value = null
        playingStartedAtMs.value = null
        cameraPreviewReady.value = false
        detectorReady.value = false
        remainingSeconds.intValue = roundSeconds
        countdownValue.intValue = 3
        questionStartMs.value = System.currentTimeMillis()
        startRequested.value = start
        challengeStarted.value = false
        challengeState.value = if (start) {
            if (isStoryMode) CvChallengeState.LoadingCamera else CvChallengeState.CameraStarting
        } else {
            CvChallengeState.Idle
        }
        correctHoldStartedAt.value = null
        sustainedConfidenceDuringHold.value = 0f
        cameraMessage.value = null
        searchingFaceLong.value = false
    }

    fun startCameraChallenge() {
        if (roundLoading.value) return
        requestResultSummary.value = null
        feedback.value = null
        lastAttemptSuccess.value = null
        cameraMessage.value = null
        startRequested.value = true
        cameraPreviewReady.value = false
        detectorReady.value = false
        if (hasCameraPermission.value) {
            resetCurrentRound(start = true)
        } else {
            challengeState.value = CvChallengeState.RequestingPermission
            isRequestingCameraPermission.value = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun currentRequestRoundAverageConfidence(): Float {
        val durationMs = roundConfidenceDurationMs.value
        return if (durationMs > 0L) {
            (roundConfidenceWeightedSum.value / durationMs.toFloat()).coerceIn(0f, 100f)
        } else {
            0f
        }
    }

    fun applyStoryCheckpoint(checkpoint: CvStoryCheckpoint) {
        val restoredQuestions = checkpoint.questions.ifEmpty { questions.value }
        if (restoredQuestions.isEmpty()) return
        questions.value = restoredQuestions
        sessionId.value = checkpoint.sessionId
        backendSessionClosed.value = false
        abandoningSession.value = false
        maxErrors.intValue = checkpoint.maxErrors.coerceAtLeast(1)
        score.intValue = checkpoint.score.coerceAtLeast(0)
        results.value = checkpoint.results
        val answeredCount = checkpoint.results.size.coerceAtMost(restoredQuestions.size)
        val restoredIndex = maxOf(answeredCount, checkpoint.currentIndex)
            .coerceIn(0, (restoredQuestions.size - 1).coerceAtLeast(0))
        currentIndex.intValue = restoredIndex
        summary.value = null
        requestResultSummary.value = null
        feedback.value = null
        lastAttemptSuccess.value = null
        learningEmotionId.value = null
        challengeCompleted.value = false
        detectionActive.value = false
        emotionErrors.clear()
        learnedEmotions.clear()
        val questionById = restoredQuestions.associateBy { it.questionId }
        checkpoint.results
            .filter { !it.isCorrect }
            .forEach { result ->
                val emotionId = questionById[result.questionId]
                    ?.prompt
                    ?.correctAnswer
                    ?.let(::normalizeCvEmotion)
                if (!emotionId.isNullOrBlank()) {
                    emotionErrors[emotionId] = (emotionErrors[emotionId] ?: 0) + 1
                }
            }
        emotionErrors.forEach { (emotionId, count) ->
            if (count >= maxErrors.intValue && emotionId !in learnedEmotions) {
                learnedEmotions.add(emotionId)
            }
        }
        resetCurrentRound(start = false)
        questionStartMs.value = System.currentTimeMillis()
    }

    fun abandonSessionIfNeeded(after: (() -> Unit)? = null) {
        val activeSessionId = sessionId.value
        if (
            activeSessionId.isNullOrBlank() ||
            backendSessionClosed.value ||
            abandoningSession.value
        ) {
            after?.invoke()
            return
        }
        abandoningSession.value = true
        scope.launch {
            runCatching { repository.abandonSession(activeSessionId) }
                .onSuccess { closed ->
                    if (closed) {
                        backendSessionClosed.value = true
                    }
                }
                .onFailure {
                    Log.w(CvLogTag, "abandonSession failed sessionId=$activeSessionId", it)
                }
            abandoningSession.value = false
            after?.invoke()
        }
    }

    fun startNewBackendSession(after: (() -> Unit)? = null) {
        if (isStoryMode) {
            clearCvStoryCheckpoint(
                context = context,
                userId = userId,
                gameId = gameId,
                level = level
            )
        }
        sessionId.value = null
        backendSessionClosed.value = false
        abandoningSession.value = false
        scope.launch {
            runCatching { repository.startGame(gameId, userId, level) }
                .onSuccess { started ->
                    started?.sessionId?.let { newSessionId ->
                        sessionId.value = newSessionId
                        backendSessionClosed.value = false
                        abandoningSession.value = false
                    }
                    val backendQuestions = started
                        ?.questions
                        .orEmpty()
                        .toCvQuestionUiList(defaultPrompt = defaultPrompt)
                    if (backendQuestions.isNotEmpty()) {
                        questions.value = if (isStoryMode) {
                            selectCvStoryQuestions(
                                level = level,
                                backendQuestions = backendQuestions,
                                gameId = "${gameId}-session-${started?.sessionId.orEmpty()}"
                            )
                        } else {
                            val selectedEmotionKey = selectedEmotion
                                ?.takeIf { it.isNotBlank() }
                                ?.let(::normalizeCvEmotion)
                            selectedEmotionKey?.let { key ->
                                backendQuestions.filter { normalizeCvEmotion(it.prompt.correctAnswer) == key }
                            }?.ifEmpty {
                                listOf(backendQuestions.first())
                            } ?: listOf(backendQuestions.first())
                        }
                    }
                }
                .onFailure {
                    Log.w(CvLogTag, "startNewBackendSession failed gameId=$gameId level=$level", it)
                }
            after?.invoke()
        }
    }

    fun finishLevel(finalResults: List<AnswerResultDto>) {
        if (isSubmitting.value || summary.value != null) return
        if (isStoryMode) {
            clearCvStoryCheckpoint(
                context = context,
                userId = userId,
                gameId = gameId,
                level = level
            )
        }
        isSubmitting.value = true
        scope.launch {
            try {
                var resultsToSubmit = finalResults
                val activeSessionId = sessionId.value ?: run {
                    repository.startGame(gameId, userId, level)?.also { started ->
                        sessionId.value = started.sessionId
                        backendSessionClosed.value = false
                        abandoningSession.value = false
                        val startedQuestionIds = started.questions.map { it.contentId }
                        if (startedQuestionIds.isNotEmpty()) {
                            resultsToSubmit = finalResults.mapIndexed { index, result ->
                                startedQuestionIds.getOrNull(index)?.let { questionId ->
                                    result.copy(questionId = questionId)
                                } ?: result
                            }
                        }
                    }?.sessionId
                }
                val response = activeSessionId?.let {
                    repository.endLevel(it, resultsToSubmit, learnedEmotions.distinct())
                }
                if (response != null) {
                    backendSessionClosed.value = true
                }
                repository.getGameProgress(gameId = gameId, userId = userId, forceRefresh = true)
                if (!isStoryMode) {
                    repository.getCvEmotionScores(userId = userId, forceRefresh = true)
                    val roundScore = score.intValue.coerceIn(0, 100)
                    val roundTimeSeconds = completedTimeSeconds.intValue.coerceAtLeast(0)
                    requestResultSummary.value = if (response != null) {
                        val status = if (response.passed) "Đã lưu tiến trình" else "Đã lưu lượt chơi"
                        "$status. Điểm trung bình lượt này: ${roundScore}/100. Thời gian chơi: ${roundTimeSeconds} giây."
                    } else {
                        "Chưa lưu được tiến trình. Điểm trung bình lượt này: ${roundScore}/100. Thời gian chơi: ${roundTimeSeconds} giây."
                    }
                    return@launch
                }
                summary.value = if (isStoryMode) {
                    "Hoàn thành cấp độ!\nSố màn hoàn thành: ${finalResults.size}/${questions.value.size}.\nĐiểm: ${response?.score ?: score.intValue}."
                } else if (response != null) {
                    val status = if (response.passed) "Đã qua cấp độ" else "Chưa qua cấp độ"
                    "$status. Điểm: ${response.score}/100."
                } else {
                    "Hoàn thành. Điểm tạm tính: ${score.intValue}."
                }
            } catch (_: Exception) {
                if (!isStoryMode) {
                    val roundScore = score.intValue.coerceIn(0, 100)
                    val roundTimeSeconds = completedTimeSeconds.intValue.coerceAtLeast(0)
                    requestResultSummary.value = "Chưa lưu được tiến trình. Điểm trung bình lượt này: ${roundScore}/100. Thời gian chơi: ${roundTimeSeconds} giây."
                    return@launch
                }
                summary.value = if (isStoryMode) {
                    "Hoàn thành cấp độ!\nSố màn hoàn thành: ${finalResults.size}/${questions.value.size}.\nĐiểm: ${score.intValue}."
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
        if (challengeCompleted.value) {
            Log.d(CvLogTag, "completeChallenge ignored because already completed sessionId=${challengeSessionId.value}")
            return
        }
        challengeCompleted.value = true
        detectionActive.value = false
        Log.d(CvLogTag, "completeChallenge called success=$success confidence=$confidence sessionId=${challengeSessionId.value}")
        val question = questions.value[currentIndex.intValue]
        val reviewEmotion = normalizeCvEmotion(question.prompt.correctAnswer)
        val targetMeta = cvEmotionMeta(reviewEmotion)
        val normalizedScore = confidence.coerceIn(0f, 100f)
        if (isStoryMode) {
            if (success) {
                score.intValue += 10
            }
        } else {
            score.intValue = normalizedScore.roundToInt()
        }
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
            cvConfidence = normalizedScore
        )
        results.value = updatedResults
        currentConfidence.value = normalizedScore
        detectedConfidence.value = normalizedScore
        highestConfidence.value = maxOf(highestConfidence.value, normalizedScore)
        completedTimeSeconds.intValue = if (success) {
            ((System.currentTimeMillis() - questionStartMs.value) / 1000L).toInt().coerceAtLeast(1)
        } else {
            (roundSeconds - remainingSeconds.intValue).coerceAtLeast(0)
        }
        detectedEmotion.value = if (success) reviewEmotion else null
        lastAttemptSuccess.value = success
        challengeStarted.value = false
        startRequested.value = false
        cameraPreviewReady.value = false
        detectorReady.value = false
        correctHoldStartedAt.value = null
        holdProgressMs.value = 0L
        holdConfidenceWeightedSum.value = 0f
        roundConfidenceWeightedSum.value = 0f
        roundConfidenceDurationMs.value = 0L
        lastDetectionAtMs.value = null
        lastStrongTargetAtMs.value = null
        playingStartedAtMs.value = null
        sustainedConfidenceDuringHold.value = 0f
        val timeoutReached = !success && challengeState.value == CvChallengeState.Timeout
        challengeState.value = when {
            success -> CvChallengeState.Success
            timeoutReached -> CvChallengeState.Timeout
            else -> CvChallengeState.Failed
        }
        feedback.value = if (success) {
            if (isStoryMode) {
                "Con đã thể hiện đúng cảm xúc của tình huống."
            } else {
                "Bé đã thể hiện cảm xúc ${targetMeta.label} rất tốt."
            }
        } else if (timeoutReached && !isStoryMode) {
            "Bé thử làm cảm xúc ${targetMeta.label} rõ hơn một chút nhé."
        } else {
            "Con làm tốt rồi, mình thử thêm lần nữa nhé."
        }
        if (!isStoryMode) {
            finishLevel(updatedResults)
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

    fun replayCurrentLevel() {
        startNewBackendSession()
        currentIndex.intValue = 0
        score.intValue = 0
        results.value = emptyList()
        summary.value = null
        requestResultSummary.value = null
        emotionErrors.clear()
        learnedEmotions.clear()
        learningEmotionId.value = null
        resetCurrentRound(start = false)
    }

    fun skipStoryQuestion() {
        if (!isStoryMode || summary.value != null) return
        val question = questions.value[currentIndex.intValue]
        val updatedResults = results.value + AnswerResultDto(
            questionId = question.questionId,
            answer = "skipped_expression",
            isCorrect = false,
            responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt(),
            cvConfidence = 0f
        )
        results.value = updatedResults
        if (currentIndex.intValue >= questions.value.lastIndex) {
            finishLevel(updatedResults)
            return
        }
        currentIndex.intValue += 1
        resetCurrentRound(start = false)
    }

    LaunchedEffect(gameId, level, userId, selectedEmotion, isStoryMode) {
        if (!isStoryMode) {
            showStoryResumeDialog.value = false
            pendingStoryCheckpoint.value = null
            storyEntryDecisionResolved.value = true
            shouldLoadSessionFromBackend.value = true
            return@LaunchedEffect
        }
        val checkpoint = loadCvStoryCheckpoint(
            context = context,
            userId = userId,
            gameId = gameId,
            level = level
        )
        if (checkpoint != null) {
            pendingStoryCheckpoint.value = checkpoint
            showStoryResumeDialog.value = true
            storyEntryDecisionResolved.value = false
            shouldLoadSessionFromBackend.value = false
            roundLoading.value = false
        } else {
            showStoryResumeDialog.value = false
            pendingStoryCheckpoint.value = null
            storyEntryDecisionResolved.value = true
            shouldLoadSessionFromBackend.value = true
        }
    }

    LaunchedEffect(
        gameId,
        level,
        userId,
        selectedEmotion,
        shouldLoadSessionFromBackend.value,
        storyEntryDecisionResolved.value
    ) {
        if (!shouldLoadSessionFromBackend.value) return@LaunchedEffect
        if (isStoryMode && !storyEntryDecisionResolved.value) return@LaunchedEffect
        roundLoading.value = true
        try {
            val selectedEmotionKey = selectedEmotion?.takeIf { it.isNotBlank() }?.let(::normalizeCvEmotion)
            val started = repository.startGame(gameId, userId, level)
            sessionId.value = started?.sessionId
            backendSessionClosed.value = false
            abandoningSession.value = false
            maxErrors.intValue = started?.maxErrors ?: 2
            val backendQuestions = started
                ?.questions
                .orEmpty()
                .toCvQuestionUiList(defaultPrompt = defaultPrompt)

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

            val loadedQuestions = filteredQuestions.ifEmpty {
                if (backendQuestions.isNotEmpty()) {
                    if (isStoryMode) backendQuestions else listOf(backendQuestions.first())
                } else {
                    val fallbackQuestionId = if (!isStoryMode && selectedEmotionKey != null) {
                        cvRequestFallbackQuestionId(selectedEmotionKey)
                    } else {
                        "fallback-$gameId-${selectedEmotionKey ?: "default"}"
                    }
                    listOf(CvQuestionUi(fallbackQuestionId, fallbackPrompt))
                }
            }
            val canApplyLoadedQuestions =
                !startRequested.value &&
                    !challengeStarted.value &&
                    feedback.value == null &&
                    summary.value == null &&
                    currentIndex.intValue == 0 &&
                    results.value.isEmpty()
            if (canApplyLoadedQuestions) {
                questions.value = loadedQuestions
                currentIndex.intValue = 0
                score.intValue = 0
                results.value = emptyList()
                summary.value = null
                emotionErrors.clear()
                learnedEmotions.clear()
                learningEmotionId.value = null
                resetCurrentRound(start = false)
            }
        } finally {
            roundLoading.value = false
            shouldLoadSessionFromBackend.value = false
            storyEntryDecisionResolved.value = true
        }
    }

    LaunchedEffect(
        isStoryMode,
        storyEntryDecisionResolved.value,
        sessionId.value,
        currentIndex.intValue,
        score.intValue,
        results.value,
        questions.value,
        maxErrors.intValue,
        startRequested.value,
        challengeStarted.value,
        challengeState.value,
        summary.value,
        backendSessionClosed.value
    ) {
        persistStoryCheckpoint(force = false)
    }

    if (showStoryResumeDialog.value) {
        val checkpoint = pendingStoryCheckpoint.value
        if (isStoryMode && checkpoint != null) {
            val answered = checkpoint.results.size.coerceAtMost(checkpoint.questions.size)
            CvStoryResumeDialog(
                answeredCount = answered,
                totalCount = checkpoint.questions.size,
                onContinue = {
                    showStoryResumeDialog.value = false
                    pendingStoryCheckpoint.value = null
                    storyEntryDecisionResolved.value = true
                    shouldLoadSessionFromBackend.value = false
                    applyStoryCheckpoint(checkpoint)
                },
                onRestart = {
                    showStoryResumeDialog.value = false
                    pendingStoryCheckpoint.value = null
                    storyEntryDecisionResolved.value = true
                    shouldLoadSessionFromBackend.value = true
                    clearCvStoryCheckpoint(
                        context = context,
                        userId = userId,
                        gameId = gameId,
                        level = level
                    )
                    scope.launch {
                        checkpoint.sessionId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { activeSessionId ->
                                runCatching { repository.abandonSession(activeSessionId) }
                            }
                    }
                }
            )
        }
    }

    val currentQuestion = questions.value[currentIndex.intValue % questions.value.size]
    val targetEmotion = cvEmotionMeta(currentQuestion.prompt.correctAnswer)
    val promptText = displayCvPrompt(currentQuestion.prompt.questionText, targetEmotion)
    val storyPromptText = if (isStoryMode) {
        sanitizeCvStoryScenarioPrompt(hideCvAnswerInStoryPrompt(promptText, targetEmotion))
    } else {
        promptText
    }
    val canShowCameraStep = true
    val shouldMountCamera = challengeState.value == CvChallengeState.CameraStarting ||
        challengeState.value == CvChallengeState.SearchingFace ||
        challengeState.value == CvChallengeState.FaceDetected ||
        challengeState.value == CvChallengeState.Detecting ||
        challengeState.value == CvChallengeState.LoadingCamera ||
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
        (challengeState.value == CvChallengeState.Playing || challengeState.value == CvChallengeState.Detecting) &&
        feedback.value == null &&
        summary.value == null
    val isActiveRequestChallenge = challengeState.value in setOf(
        CvChallengeState.RequestingPermission,
        CvChallengeState.CameraStarting,
        CvChallengeState.SearchingFace,
        CvChallengeState.FaceDetected,
        CvChallengeState.Countdown,
        CvChallengeState.Detecting,
        CvChallengeState.LoadingCamera,
        CvChallengeState.LoadingModel,
        CvChallengeState.CameraReady,
        CvChallengeState.Playing
    ) && startRequested.value

    BackHandler(enabled = isActiveRequestChallenge || (!isStoryMode && isSubmitting.value)) {
        if (!isSubmitting.value) {
            showExitConfirm.value = true
        }
    }

    LaunchedEffect(roundLoading.value, currentQuestion.questionId, isStoryMode) {
        if (
            !isStoryMode &&
            !roundLoading.value &&
            challengeState.value == CvChallengeState.Idle &&
            feedback.value == null &&
            summary.value == null
        ) {
            startCameraChallenge()
        }
    }

    LaunchedEffect(hasCameraPermission.value, challengeState.value, isStoryMode) {
        if (
            !isStoryMode &&
            hasCameraPermission.value &&
            challengeState.value == CvChallengeState.PermissionDenied &&
            feedback.value == null &&
            summary.value == null
        ) {
            startCameraChallenge()
        }
    }

    LaunchedEffect(challengeState.value, isStoryMode) {
        if (isStoryMode) return@LaunchedEffect
        if (challengeState.value == CvChallengeState.SearchingFace) {
            searchingFaceLong.value = false
            delay(3000L)
            if (challengeState.value == CvChallengeState.SearchingFace) {
                searchingFaceLong.value = true
            }
        } else {
            searchingFaceLong.value = false
        }
    }

    LaunchedEffect(cameraReady, detectorReady.value, challengeState.value, feedback.value, summary.value) {
        if (
            !isStoryMode &&
            challengeState.value == CvChallengeState.CameraStarting &&
            cameraReady &&
            detectorReady.value &&
            feedback.value == null &&
            summary.value == null
        ) {
            challengeState.value = CvChallengeState.SearchingFace
            searchingFaceLong.value = false
        }
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

    LaunchedEffect(
        cameraReady,
        detectorReady.value,
        feedback.value,
        summary.value,
        isStoryMode,
        challengeSessionId.value
    ) {
        if (feedback.value != null || summary.value != null) return@LaunchedEffect
        if (challengeState.value != CvChallengeState.CameraReady) return@LaunchedEffect

        val sessionIdAtStart = challengeSessionId.value
        challengeState.value = CvChallengeState.Countdown

        if (isStoryMode) {
            for (value in 3 downTo 1) {
                if (
                    challengeSessionId.value != sessionIdAtStart ||
                    challengeState.value != CvChallengeState.Countdown ||
                    feedback.value != null ||
                    summary.value != null
                ) return@LaunchedEffect
                countdownValue.intValue = value
                delay(1000L)
            }
        } else {
            delay(450L)
        }

        if (
            challengeSessionId.value == sessionIdAtStart &&
            challengeState.value == CvChallengeState.Countdown &&
            feedback.value == null &&
            summary.value == null
        ) {
            challengeState.value = CvChallengeState.Playing
            challengeStarted.value = true
            playingStartedAtMs.value = System.currentTimeMillis()
            questionStartMs.value = System.currentTimeMillis()
        }
    }

    LaunchedEffect(countdownRunId.intValue) {
        if (isStoryMode || countdownRunId.intValue == 0) return@LaunchedEffect
        if (feedback.value != null || summary.value != null) return@LaunchedEffect
        val runSessionId = challengeSessionId.value
        Log.d(CvLogTag, "countdownStart sessionId=$runSessionId")
        challengeState.value = CvChallengeState.FaceDetected
        delay(700L)
        for (value in 3 downTo 1) {
            if (
                runSessionId != challengeSessionId.value ||
                challengeCompleted.value ||
                !detectionActive.value ||
                !faceDetected.value ||
                feedback.value != null ||
                summary.value != null
            ) {
                challengeState.value = CvChallengeState.SearchingFace
                countdownValue.intValue = 3
                return@LaunchedEffect
            }
            countdownValue.intValue = value
            challengeState.value = CvChallengeState.Countdown
            delay(1000L)
        }
        if (
            runSessionId == challengeSessionId.value &&
            detectionActive.value &&
            !challengeCompleted.value &&
            faceDetected.value &&
            feedback.value == null &&
            summary.value == null
        ) {
            if (!challengeStarted.value) {
                remainingSeconds.intValue = roundSeconds
                questionStartMs.value = System.currentTimeMillis()
            }
            challengeStarted.value = true
            playingStartedAtMs.value = System.currentTimeMillis()
            challengeState.value = CvChallengeState.Detecting
            Log.d(CvLogTag, "detectingStart sessionId=$runSessionId")
        } else {
            challengeState.value = CvChallengeState.SearchingFace
        }
    }

    LaunchedEffect(cameraMayStart, cameraPreviewReady.value, challengeState.value, currentIndex.intValue) {
        if (
            cameraMayStart &&
            (challengeState.value == CvChallengeState.LoadingCamera || challengeState.value == CvChallengeState.CameraStarting) &&
            !cameraPreviewReady.value
        ) {
            delay(8000)
            if (
                cameraMayStart &&
                (challengeState.value == CvChallengeState.LoadingCamera || challengeState.value == CvChallengeState.CameraStarting) &&
                !cameraPreviewReady.value &&
                feedback.value == null &&
                summary.value == null
            ) {
                cameraMessage.value = "Không mở được camera. Vui lòng thử lại."
                startRequested.value = false
                challengeStarted.value = false
                challengeState.value = if (isStoryMode) CvChallengeState.Failed else CvChallengeState.Error
                cameraPreviewReady.value = false
                detectorReady.value = false
                isRequestingCameraPermission.value = false
            }
        }
    }

    LaunchedEffect(cameraReady, detectorReady.value, challengeState.value, currentIndex.intValue) {
        if (
            cameraReady &&
            (challengeState.value == CvChallengeState.LoadingModel || challengeState.value == CvChallengeState.CameraStarting) &&
            !detectorReady.value
        ) {
            delay(8000)
            if (
                cameraReady &&
                (challengeState.value == CvChallengeState.LoadingModel || challengeState.value == CvChallengeState.CameraStarting) &&
                !detectorReady.value &&
                feedback.value == null &&
                summary.value == null
            ) {
                cameraMessage.value = "Bộ nhận diện chưa sẵn sàng. Vui lòng thử lại."
                startRequested.value = false
                challengeStarted.value = false
                challengeState.value = if (isStoryMode) CvChallengeState.Failed else CvChallengeState.Error
                cameraPreviewReady.value = false
                detectorReady.value = false
            }
        }
    }

    LaunchedEffect(challengeState.value, feedback.value, summary.value, currentIndex.intValue) {
        val waitingForFrames = challengeState.value == CvChallengeState.Playing ||
            challengeState.value == CvChallengeState.Detecting
        if (!waitingForFrames || feedback.value != null || summary.value != null) {
            return@LaunchedEffect
        }
        while (
            (challengeState.value == CvChallengeState.Playing || challengeState.value == CvChallengeState.Detecting) &&
            feedback.value == null &&
            summary.value == null
        ) {
            delay(1000L)
            val startedAt = playingStartedAtMs.value ?: System.currentTimeMillis()
            val lastFrameAt = lastDetectionAtMs.value ?: startedAt
            if (System.currentTimeMillis() - lastFrameAt > 5000L) {
                cameraMessage.value = "Bộ nhận diện chưa sẵn sàng. Vui lòng thử lại."
                if (isStoryMode) {
                    recordAttempt(success = false, confidence = 0f)
                } else {
                    startRequested.value = false
                    challengeStarted.value = false
                    challengeState.value = CvChallengeState.Error
                    cameraPreviewReady.value = false
                    detectorReady.value = false
                }
                break
            }
        }
    }

    LaunchedEffect(currentIndex.intValue, challengeState.value, feedback.value, summary.value) {
        if (!timerActive) return@LaunchedEffect
        if (!isStoryMode && challengeState.value == CvChallengeState.Detecting) {
            while (
                challengeState.value == CvChallengeState.Detecting &&
                detectionActive.value &&
                !challengeCompleted.value &&
                feedback.value == null &&
                summary.value == null
            ) {
                delay(1000L)
                if (
                    challengeState.value != CvChallengeState.Detecting ||
                    !detectionActive.value ||
                    challengeCompleted.value
                ) break
                remainingSeconds.intValue = (remainingSeconds.intValue - 1).coerceAtLeast(0)
                if (remainingSeconds.intValue <= 0) {
                    challengeState.value = CvChallengeState.Timeout
                    Log.d(CvLogTag, "timerTimeout sessionId=${challengeSessionId.value}")
                    val timeoutAverage = currentRequestRoundAverageConfidence()
                    val timeoutConfidence = maxOf(
                        timeoutAverage,
                        currentConfidence.value,
                        detectedConfidence.value,
                        highestConfidence.value
                    ).coerceIn(0f, 100f)
                    recordAttempt(success = false, confidence = timeoutConfidence)
                    break
                }
            }
            return@LaunchedEffect
        }
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

    fun handleCvDetection(emotionId: String?, confidence: Float) {
        if (
            screenDisposed.value ||
            !detectionActive.value ||
            challengeCompleted.value ||
            challengeState.value == CvChallengeState.Success ||
            challengeState.value == CvChallengeState.Timeout ||
            challengeState.value == CvChallengeState.Error ||
            challengeState.value == CvChallengeState.Ended
        ) {
            return
        }
        if (!detectorReady.value) {
            detectorReady.value = true
        }
        val now = System.currentTimeMillis()
        val normalizedEmotion = emotionId?.takeIf { it.isNotBlank() }?.let(::normalizeCvEmotion)
        val normalizedConfidence = normalizeJsConfidence(confidence)
        val hasFaceInFrame = normalizedEmotion != null || normalizedConfidence > 0f
        val isTargetEmotion = normalizedEmotion == targetEmotion.id
        val targetRawScore = if (isTargetEmotion) normalizedConfidence else 0f
        val previousScore = currentConfidence.value
        val smoothedScore = if (lastDetectionAtMs.value == null) {
            targetRawScore
        } else if (targetRawScore >= previousScore) {
            (previousScore * 0.35f) + (targetRawScore * 0.65f)
        } else {
            (previousScore * 0.88f) + (targetRawScore * 0.12f)
        }.coerceIn(0f, 100f)
        val effectiveTargetScore = smoothedScore
        val deltaMs = (now - (lastDetectionAtMs.value ?: now)).coerceIn(120L, 700L)
        val withinStrongGrace = lastStrongTargetAtMs.value?.let { now - it <= 500L } == true

        lastDetectionAtMs.value = now
        faceDetected.value = hasFaceInFrame
        detectedEmotion.value = normalizedEmotion
        detectedConfidence.value = if (hasFaceInFrame) normalizedConfidence else 0f
        currentConfidence.value = if (hasFaceInFrame) effectiveTargetScore else 0f
        if (isTargetEmotion) {
            highestConfidence.value = maxOf(highestConfidence.value, effectiveTargetScore)
        }
        Log.d(
            CvLogTag,
            "detection result sessionId=${challengeSessionId.value} emotion=$normalizedEmotion confidence=$normalizedConfidence hold=${holdProgressMs.value}"
        )

        if (isStoryMode || feedback.value != null || summary.value != null) return

        if (!hasFaceInFrame) {
            if (challengeState.value == CvChallengeState.Detecting && withinStrongGrace) {
                return
            }
            if (
                challengeState.value == CvChallengeState.Detecting ||
                challengeState.value == CvChallengeState.Countdown ||
                challengeState.value == CvChallengeState.FaceDetected ||
                challengeState.value == CvChallengeState.CameraStarting
            ) {
                challengeState.value = CvChallengeState.SearchingFace
            }
            return
        }

        if (
            challengeState.value == CvChallengeState.SearchingFace ||
            challengeState.value == CvChallengeState.CameraStarting
        ) {
            challengeState.value = CvChallengeState.FaceDetected
            countdownRunId.intValue += 1
            return
        }

        if (challengeState.value != CvChallengeState.Detecting) return

        val holdConfidenceScore = if (isTargetEmotion) effectiveTargetScore.coerceIn(0f, 100f) else 0f
        if (isTargetEmotion) {
            roundConfidenceWeightedSum.value += holdConfidenceScore * deltaMs.toFloat()
            roundConfidenceDurationMs.value += deltaMs
        }
        val isHoldingCorrectEmotion = isTargetEmotion && holdConfidenceScore >= CvRequiredConfidence
        if (isHoldingCorrectEmotion) {
            lastStrongTargetAtMs.value = now
            if (correctHoldStartedAt.value == null) {
                correctHoldStartedAt.value = now
                sustainedConfidenceDuringHold.value = holdConfidenceScore
            } else {
                sustainedConfidenceDuringHold.value = maxOf(
                    sustainedConfidenceDuringHold.value,
                    holdConfidenceScore
                )
            }
            val previousHoldMs = holdProgressMs.value
            val nextHoldMs = (previousHoldMs + deltaMs).coerceAtMost(CvRequiredHoldMs)
            val gainedHoldMs = (nextHoldMs - previousHoldMs).coerceAtLeast(0L)
            if (gainedHoldMs > 0L) {
                holdConfidenceWeightedSum.value += holdConfidenceScore * gainedHoldMs.toFloat()
            }
            holdProgressMs.value = nextHoldMs
            if (holdProgressMs.value >= CvRequiredHoldMs) {
                val averageConfidence = if (holdProgressMs.value > 0L) {
                    (holdConfidenceWeightedSum.value / holdProgressMs.value.toFloat()).coerceIn(0f, 100f)
                } else {
                    holdConfidenceScore
                }
                recordAttempt(
                    success = true,
                    confidence = averageConfidence
                )
            }
        } else {
            if (isTargetEmotion && holdConfidenceScore >= 60f) {
                val previousHoldMs = holdProgressMs.value
                val partialHoldCap = (CvRequiredHoldMs * 0.6f).toLong()
                val nextHoldMs = (previousHoldMs + (deltaMs / 5)).coerceAtMost(partialHoldCap)
                val gainedHoldMs = (nextHoldMs - previousHoldMs).coerceAtLeast(0L)
                if (gainedHoldMs > 0L) {
                    holdConfidenceWeightedSum.value += holdConfidenceScore * gainedHoldMs.toFloat()
                }
                holdProgressMs.value = nextHoldMs
                return
            }
            val decay = if (withinStrongGrace && holdProgressMs.value > 0L) {
                deltaMs / 8
            } else if (isTargetEmotion && holdConfidenceScore >= 50f) {
                deltaMs / 4
            } else {
                deltaMs / 2
            }
            val previousHoldMs = holdProgressMs.value
            val nextHoldMs = (previousHoldMs - decay).coerceAtLeast(0L)
            holdProgressMs.value = nextHoldMs
            if (previousHoldMs > 0L && nextHoldMs > 0L) {
                val retainRatio = nextHoldMs.toFloat() / previousHoldMs.toFloat()
                holdConfidenceWeightedSum.value = (holdConfidenceWeightedSum.value * retainRatio).coerceAtLeast(0f)
            }
            if (holdProgressMs.value == 0L) {
                correctHoldStartedAt.value = null
                sustainedConfidenceDuringHold.value = 0f
                holdConfidenceWeightedSum.value = 0f
            }
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
                onBack = {
                    when {
                        !isStoryMode && isSubmitting.value -> Unit
                        isActiveRequestChallenge -> showExitConfirm.value = true
                        else -> {
                            if (isStoryMode && summary.value == null) {
                                persistStoryCheckpoint(force = true)
                                onBack()
                            } else {
                                abandonSessionIfNeeded {
                                    onBack()
                                }
                            }
                        }
                    }
                }
            )

            if (summary.value != null) {
                if (isStoryMode) {
                    CvStoryLevelSummaryCard(
                        summary = summary.value.orEmpty(),
                        onReplay = { replayCurrentLevel() },
                        onBack = {
                            abandonSessionIfNeeded {
                                onBack()
                            }
                        }
                    )
                } else {
                    GameLevelSummaryCard(
                        summary = summary.value.orEmpty(),
                        onBack = {
                            abandonSessionIfNeeded {
                                onBack()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(80.dp))
            } else if (!isStoryMode) {
                CvRequestChallengeScreen(
                    targetEmotion = targetEmotion,
                    challengeState = challengeState.value,
                    cameraMessage = cameraMessage.value,
                    detectedEmotionId = detectedEmotion.value,
                    confidence = currentConfidence.value,
                    detectedConfidence = detectedConfidence.value,
                    faceDetected = faceDetected.value,
                    searchingFaceLong = searchingFaceLong.value,
                    holdProgress = holdProgressMs.value.toFloat() / CvRequiredHoldMs.toFloat(),
                    remainingSeconds = remainingSeconds.intValue,
                    countdownValue = countdownValue.intValue,
                    timerActive = timerActive,
                    roundLoading = roundLoading.value,
                    isRequestingCameraPermission = isRequestingCameraPermission.value,
                    shouldShowCamera = cameraMayStart &&
                        detectionActive.value &&
                        !challengeCompleted.value &&
                        feedback.value == null &&
                        cameraMessage.value == null,
                    detectionActive = detectionActive.value && !challengeCompleted.value,
                    completedTimeSeconds = completedTimeSeconds.intValue,
                    score = score.intValue.coerceIn(0, 100),
                    resultSummary = requestResultSummary.value,
                    isSubmitting = isSubmitting.value,
                    onCameraReady = {
                        if (screenDisposed.value || challengeCompleted.value) return@CvRequestChallengeScreen
                        Log.d(CvLogTag, "cameraReady sessionId=${challengeSessionId.value}")
                        cameraPreviewReady.value = true
                        cameraMessage.value = null
                    },
                    onDetectorReady = {
                        if (screenDisposed.value || challengeCompleted.value) return@CvRequestChallengeScreen
                        Log.d(CvLogTag, "detectorReady sessionId=${challengeSessionId.value}")
                        detectorReady.value = true
                        cameraMessage.value = null
                    },
                    onDetection = { emotionId, confidence ->
                        if (!screenDisposed.value && detectionActive.value && !challengeCompleted.value) {
                            handleCvDetection(emotionId, confidence)
                        }
                    },
                    onCameraError = { message ->
                        if (screenDisposed.value || challengeCompleted.value) return@CvRequestChallengeScreen
                        detectionActive.value = false
                        Log.d(CvLogTag, "cameraError sessionId=${challengeSessionId.value} message=$message")
                        cameraMessage.value = message.ifBlank { "Không mở được camera. Vui lòng thử lại." }
                        startRequested.value = false
                        challengeStarted.value = false
                        challengeState.value = CvChallengeState.Error
                        cameraPreviewReady.value = false
                        detectorReady.value = false
                        isRequestingCameraPermission.value = false
                    },
                    onRetry = {
                        when {
                            isSubmitting.value -> Unit
                            feedback.value != null -> {
                                score.intValue = 0
                                results.value = emptyList()
                                requestResultSummary.value = null
                                startNewBackendSession {
                                    startCameraChallenge()
                                }
                            }
                            else -> startCameraChallenge()
                        }
                    },
                    onStop = { showExitConfirm.value = true },
                    onSelectAnother = {
                        if (!isSubmitting.value) {
                            abandonSessionIfNeeded {
                                onBack()
                            }
                        }
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            } else {
                CvStoryScenarioCard(
                    promptLabel = "Tình huống",
                    scenarioTitle = "",
                    scenarioText = storyPromptText
                )

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
                detectedConfidence = detectedConfidence.value,
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
                    searchingFaceLong = searchingFaceLong.value,
                    remainingSeconds = remainingSeconds.intValue,
                    countdownValue = countdownValue.intValue,
                    timerActive = timerActive,
                    isStoryMode = isStoryMode,
                    storyScenarioTitle = null,
                    storyScenarioText = null,
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
                        val normalizedConfidence = normalizeJsConfidence(confidence)
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
                        detectedConfidence.value = normalizedConfidence
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
                    if (!roundLoading.value) {
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
                    }
                },
                onRetry = { resetCurrentRound(start = false) },
                onMarkSuccess = { recordAttempt(success = true, confidence = 100f) },
                onNext = {
                    if (isStoryMode) {
                        if (feedback.value != null) {
                            goNextOrFinish()
                        } else {
                            skipStoryQuestion()
                        }
                    } else {
                        goNextOrFinish()
                    }
                },
                onExit = {
                    challengeState.value = CvChallengeState.Ended
                    if (isStoryMode && summary.value == null) {
                        persistStoryCheckpoint(force = true)
                        onFinish()
                    } else {
                        abandonSessionIfNeeded {
                            onFinish()
                        }
                    }
                }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        EmotionLearningDialog(
            emotionId = learningEmotionId.value,
            onDismiss = { learningEmotionId.value = null }
        )

        if (showExitConfirm.value) {
            ConfirmExitDialog(
                onDismiss = { showExitConfirm.value = false },
                onConfirm = {
                    showExitConfirm.value = false
                    detectionActive.value = false
                    challengeCompleted.value = true
                    challengeSessionId.value += 1L
                    Log.d(CvLogTag, "cleanupChallenge exitConfirm sessionId=${challengeSessionId.value}")
                    startRequested.value = false
                    challengeStarted.value = false
                    challengeState.value = CvChallengeState.Ended
                    cameraPreviewReady.value = false
                    detectorReady.value = false
                    if (isStoryMode && summary.value == null) {
                        persistStoryCheckpoint(force = true)
                        onBack()
                    } else {
                        abandonSessionIfNeeded {
                            onBack()
                        }
                    }
                }
            )
        }

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
private fun CvRequestChallengeScreen(
    targetEmotion: CvEmotionMeta,
    challengeState: CvChallengeState,
    cameraMessage: String?,
    detectedEmotionId: String?,
    confidence: Float,
    detectedConfidence: Float,
    faceDetected: Boolean,
    searchingFaceLong: Boolean,
    holdProgress: Float,
    remainingSeconds: Int,
    countdownValue: Int,
    timerActive: Boolean,
    roundLoading: Boolean,
    isRequestingCameraPermission: Boolean,
    shouldShowCamera: Boolean,
    detectionActive: Boolean,
    completedTimeSeconds: Int,
    score: Int,
    resultSummary: String?,
    isSubmitting: Boolean,
    onCameraReady: () -> Unit,
    onDetectorReady: () -> Unit,
    onDetection: (String?, Float) -> Unit,
    onCameraError: (String) -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onSelectAnother: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (challengeState == CvChallengeState.Success || challengeState == CvChallengeState.Timeout) {
            ChallengeResultCard(
                targetEmotion = targetEmotion,
                success = challengeState == CvChallengeState.Success,
                score = score,
                timeUsedSeconds = completedTimeSeconds,
                resultSummary = resultSummary,
                isSubmitting = isSubmitting,
                onReplay = onRetry,
                onSelectAnother = onSelectAnother
            )
        } else {
            CvRequestMissionCard(targetEmotion = targetEmotion)

        if (challengeState == CvChallengeState.PermissionDenied) {
            CameraPermissionContent(
                onOpenSettings = onOpenSettings,
                onBack = onSelectAnother
            )
        } else {
            CvRequestCameraPreviewBox(
            targetEmotion = targetEmotion,
            challengeState = challengeState,
            shouldShowCamera = shouldShowCamera,
            detectionActive = detectionActive,
            cameraMessage = cameraMessage,
            faceDetected = faceDetected,
            searchingFaceLong = searchingFaceLong,
            holdProgress = holdProgress,
            remainingSeconds = remainingSeconds,
            countdownValue = countdownValue,
            timerActive = timerActive,
            roundLoading = roundLoading,
            isRequestingCameraPermission = isRequestingCameraPermission,
            onCameraReady = onCameraReady,
            onDetectorReady = onDetectorReady,
            onDetection = onDetection,
            onCameraError = onCameraError
        )

        DetectionFeedbackCard(
            targetEmotion = targetEmotion,
            challengeState = challengeState,
            detectedEmotionId = detectedEmotionId,
            confidence = confidence,
            detectedConfidence = detectedConfidence,
            faceDetected = faceDetected,
            searchingFaceLong = searchingFaceLong,
            holdProgress = holdProgress,
            remainingSeconds = remainingSeconds
        )

        when (challengeState) {
            CvChallengeState.Error -> ChallengeErrorCard(
                message = cameraMessage ?: "Không mở được camera. Vui lòng thử lại.",
                onRetry = onRetry,
                onBack = onSelectAnother
            )
            else -> OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Text("Dừng", color = EgDesign.blue, fontWeight = FontWeight.Bold)
            }
        }
        }
        }
    }
}

@Composable
private fun CvRequestMissionCard(targetEmotion: CvEmotionMeta) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = Color(0xFFEAF7FF),
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(targetEmotion.emoji, fontSize = 26.sp)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Mục tiêu: ${targetEmotion.label} ${targetEmotion.emoji}",
                    color = EgDesign.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = cvShortChallengeInstruction(targetEmotion.id),
                    color = EgDesign.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = "🔒 Không lưu ảnh/video.",
                    color = EgDesign.blue,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CvRequestCameraPreviewBox(
    targetEmotion: CvEmotionMeta,
    challengeState: CvChallengeState,
    shouldShowCamera: Boolean,
    detectionActive: Boolean,
    cameraMessage: String?,
    faceDetected: Boolean,
    searchingFaceLong: Boolean,
    holdProgress: Float,
    remainingSeconds: Int,
    countdownValue: Int,
    timerActive: Boolean,
    roundLoading: Boolean,
    isRequestingCameraPermission: Boolean,
    onCameraReady: () -> Unit,
    onDetectorReady: () -> Unit,
    onDetection: (String?, Float) -> Unit,
    onCameraError: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(334.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102A43)),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF102A43)),
            contentAlignment = Alignment.Center
        ) {
            if (shouldShowCamera) {
                CvNativeEmotionCamera(
                    modifier = Modifier.fillMaxSize(),
                    isActive = detectionActive,
                    targetEmotionId = targetEmotion.id,
                    onReady = onCameraReady,
                    onDetectorReady = onDetectorReady,
                    onDetection = onDetection,
                    onMatched = {},
                    onError = onCameraError
                )
            } else {
                CameraPlaceholderContent(
                    challengeState = challengeState,
                    cameraMessage = cameraMessage,
                    roundLoading = roundLoading,
                    isRequestingCameraPermission = isRequestingCameraPermission
                )
            }

            FaceGuideOverlay(
                challengeState = challengeState,
                faceDetected = faceDetected,
                searchingFaceLong = searchingFaceLong,
                holdProgress = holdProgress
            )

            ChallengeStatusChip(
                text = cvChallengeStatusLabelV2(challengeState, faceDetected, searchingFaceLong),
                color = cvChallengeStatusColorV2(challengeState, faceDetected),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            )

            val secondaryChipText = when {
                timerActive -> formatCvTime(remainingSeconds)
                challengeState == CvChallengeState.FaceDetected ||
                    challengeState == CvChallengeState.Countdown -> "Sắp bắt đầu"
                else -> null
            }
            secondaryChipText?.let { text ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.94f),
                    border = BorderStroke(
                        1.dp,
                        if (timerActive && remainingSeconds <= 5) Color(0xFFFDA4AF) else EgDesign.cardBorder
                    )
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (timerActive && remainingSeconds <= 5) Color(0xFFBE123C) else EgDesign.blue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (challengeState == CvChallengeState.Countdown) {
                CountdownOverlay(value = countdownValue)
            }
        }
    }
}

@Composable
private fun CameraPlaceholderContent(
    challengeState: CvChallengeState,
    cameraMessage: String?,
    roundLoading: Boolean,
    isRequestingCameraPermission: Boolean
) {
    Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("📷", fontSize = 42.sp)
        Text(
            text = when {
                roundLoading -> "Đang mở camera..."
                isRequestingCameraPermission || challengeState == CvChallengeState.RequestingPermission -> "Đang mở camera..."
                challengeState == CvChallengeState.CameraStarting -> "Đang mở camera..."
                challengeState == CvChallengeState.Error -> "Không mở được camera"
                else -> "Giữ khuôn mặt trong khung một chút nhé."
            },
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        if (roundLoading || isRequestingCameraPermission || challengeState == CvChallengeState.CameraStarting) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }
        Text(
            text = cameraMessage ?: "Việc nhận diện được xử lý trên thiết bị.",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FaceGuideOverlay(
    challengeState: CvChallengeState,
    faceDetected: Boolean,
    searchingFaceLong: Boolean,
    holdProgress: Float
) {
    val borderColor = when {
        challengeState == CvChallengeState.Success || holdProgress >= 0.75f -> Color(0xFF22C55E)
        faceDetected -> Color(0xFF60A5FA)
        challengeState == CvChallengeState.Timeout -> Color(0xFFF59E0B)
        else -> Color.White.copy(alpha = 0.72f)
    }
    val label = when {
        challengeState == CvChallengeState.Success || holdProgress >= 0.75f -> "Đúng rồi, giữ thêm nhé!"
        faceDetected -> "Đã thấy khuôn mặt"
        !searchingFaceLong -> "Giữ mặt trong khung một chút nhé"
        else -> "Bé đưa mặt gần hơn một chút nhé"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(width = 178.dp, height = 220.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(3.dp, borderColor)
        ) {}
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.68f)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChallengeStatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.48f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun CountdownOverlay(value: Int) {
    Surface(
        modifier = Modifier.size(112.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(3.dp, EgDesign.primary)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value.toString(),
                color = EgDesign.blue,
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun DetectionFeedbackCard(
    targetEmotion: CvEmotionMeta,
    challengeState: CvChallengeState,
    detectedEmotionId: String?,
    confidence: Float,
    detectedConfidence: Float,
    faceDetected: Boolean,
    searchingFaceLong: Boolean,
    holdProgress: Float,
    remainingSeconds: Int
) {
    val detectedMeta = detectedEmotionId?.let(::cvEmotionMeta)
    val requiredHoldSeconds = CvRequiredHoldMs / 1000f
    val holdSeconds = holdProgress.coerceIn(0f, 1f) * requiredHoldSeconds
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = Color(0xFFEAF7FF),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(detectedMeta?.emoji ?: targetEmotion.emoji, fontSize = 24.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Mục tiêu: ${targetEmotion.label}",
                        color = EgDesign.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = cvDetectionLine(
                            state = challengeState,
                            detectedMeta = detectedMeta,
                            confidence = detectedConfidence,
                            faceDetected = faceDetected,
                            searchingFaceLong = searchingFaceLong
                        ),
                        color = EgDesign.blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = cvChallengeRealtimeHint(
                    targetEmotionId = targetEmotion.id,
                    detectedEmotionId = detectedEmotionId,
                    confidence = confidence,
                    detectedConfidence = detectedConfidence,
                    faceDetected = faceDetected,
                    searchingFaceLong = searchingFaceLong,
                    holdProgress = holdProgress,
                    challengeState = challengeState
                ),
                color = EgDesign.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            if (challengeState == CvChallengeState.Detecting || holdProgress > 0f) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Giữ biểu cảm: %.1f / %.1f giây".format(holdSeconds, requiredHoldSeconds),
                            color = EgDesign.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Còn ${remainingSeconds.coerceAtLeast(0)} giây",
                            color = EgDesign.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { holdProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = if (holdProgress >= 0.75f) Color(0xFF22C55E) else EgDesign.primary,
                        trackColor = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeResultCard(
    targetEmotion: CvEmotionMeta,
    success: Boolean,
    score: Int,
    timeUsedSeconds: Int,
    resultSummary: String?,
    isSubmitting: Boolean,
    onReplay: () -> Unit,
    onSelectAnother: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (success) Color(0xFFECFDF5) else Color(0xFFFFFBEB)
        ),
        border = BorderStroke(1.dp, if (success) Color(0xFF86EFAC) else Color(0xFFFDE68A))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (success) "🎉" else "🌟", fontSize = 34.sp)
            Text(
                text = if (success) "Tuyệt vời!" else "Gần được rồi!",
                color = EgDesign.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = resultSummary ?: if (isSubmitting) {
                    "Đang lưu tiến trình..."
                } else if (success) {
                    "Bé đã thể hiện cảm xúc ${targetEmotion.label} rất tốt."
                } else {
                    "Bé thử làm cảm xúc ${targetEmotion.label} rõ hơn một chút nhé."
                },
                color = EgDesign.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultStat(
                    label = "Điểm TB lượt này",
                    value = if (success) "${score.coerceIn(0, 100)}/100" else "0/100",
                    modifier = Modifier.weight(1f)
                )
                ResultStat(
                    label = "Thời gian chơi",
                    value = "${timeUsedSeconds.coerceAtLeast(0)} giây",
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = onReplay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
            ) {
                Text(if (success) "Chơi lại" else "Thử lại", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectAnother,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    enabled = !isSubmitting,
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Text("Chọn cảm xúc khác", color = EgDesign.blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, color = EgDesign.blue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = EgDesign.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CameraPermissionContent(onOpenSettings: () -> Unit, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📷", fontSize = 38.sp)
            Text(
                text = "Cần quyền camera",
                color = EgDesign.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "App cần quyền camera để bé chơi thử thách biểu cảm. Ảnh và video sẽ không được lưu.",
                color = EgDesign.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Text("Quay lại", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                ) {
                    Text("Mở cài đặt", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChallengeErrorCard(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
        border = BorderStroke(1.dp, Color(0xFFFDA4AF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Không mở được camera", color = Color(0xFFBE123C), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Text("Quay lại", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                ) {
                    Text("Thử lại", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ConfirmExitDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("⏸", fontSize = 26.sp) },
        title = {
            Text("Dừng thử thách?", color = EgDesign.textPrimary, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Text(
                "Tiến độ hiện tại sẽ không được lưu.",
                color = EgDesign.textSecondary,
                lineHeight = 20.sp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tiếp tục chơi", color = EgDesign.blue, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("Dừng", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun CvStoryResumeDialog(
    answeredCount: Int,
    totalCount: Int,
    onContinue: () -> Unit,
    onRestart: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, EgDesign.cardBorder),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("📚", fontSize = 28.sp)
                Text(
                    "Bé đang chơi dở",
                    color = EgDesign.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Bé đã làm ${answeredCount.coerceAtLeast(0)}/${totalCount.coerceAtLeast(1)} câu. Bé muốn chơi tiếp hay chơi lại từ đầu?",
                    color = EgDesign.textSecondary,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestart,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Text("Chơi lại", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                    ) {
                        Text("Chơi tiếp", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CvStoryStageIntroCard(
    stageIndex: Int,
    totalStages: Int,
    scenarioText: String,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF4FAFF),
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("📖", fontSize = 22.sp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            text = "Tình huống ${stageIndex + 1}/$totalStages",
                            color = EgDesign.blue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = scenarioText,
                            color = EgDesign.textPrimary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            Text(
                text = "Bé hãy đọc tình huống rồi thể hiện cảm xúc phù hợp trước camera nhé.",
                color = EgDesign.textSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
            ) {
                Text("Bắt đầu biểu hiện", color = Color.White, fontWeight = FontWeight.Bold)
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
                Text("Chơi lại cấp độ", color = Color.White, fontWeight = FontWeight.Bold)
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
                    if (scenarioTitle.isNotBlank()) {
                        Text(
                            text = scenarioTitle,
                            color = EgDesign.textPrimary,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
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

private fun List<GameContentDto>.toCvQuestionUiList(defaultPrompt: CvPromptUiItem): List<CvQuestionUi> {
    return mapNotNull { content ->
        val emotion = (content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null }
        val text = content.questionText?.ifBlank { defaultPrompt.questionText } ?: defaultPrompt.questionText
        CvQuestionUi(
            questionId = content.contentId,
            prompt = CvPromptUiItem(questionText = text, correctAnswer = emotion)
        )
    }
}

private fun selectCvStoryQuestions(
    level: Int,
    backendQuestions: List<CvQuestionUi>,
    gameId: String
): List<CvQuestionUi> {
    val backendPool = backendQuestions.distinctBy { it.questionId }
    if (backendPool.isNotEmpty()) {
        return backendPool.take(CvStoryQuestionsPerLevel)
    }
    val localPool = cvStoryLocalQuestionPool(level)
    val seed = System.currentTimeMillis() xor gameId.hashCode().toLong() xor (level * 31L)
    return localPool
        .shuffled(Random(seed))
        .take(CvStoryQuestionsPerLevel)
        .ifEmpty { localPool.take(CvStoryQuestionsPerLevel) }
}

private fun cvStoryLocalQuestionPool(level: Int): List<CvQuestionUi> {
    val scenarios = listOf(
        "happy" to "Bạn nhỏ được cô giáo khen vì đã giúp bạn dọn đồ chơi.",
        "happy" to "Bạn nhỏ nhận được món quà mình thích trong ngày sinh nhật.",
        "sad" to "Bạn nhỏ làm rơi cây kem yêu thích xuống đất.",
        "sad" to "Bạn nhỏ phải tạm biệt người bạn thân sau buổi chơi.",
        "angry" to "Bạn nhỏ đang chơi thì bị bạn khác giật mất món đồ chơi mà không xin phép.",
        "angry" to "Bạn nhỏ bị xô ngã khi đang xếp hàng.",
        "fear" to "Bạn nhỏ nghe thấy tiếng sấm rất lớn khi trời tối.",
        "fear" to "Bạn nhỏ đi lạc bố mẹ trong siêu thị đông người.",
        "surprise" to "Bạn nhỏ mở hộp quà và thấy bên trong là món đồ mình không ngờ tới.",
        "surprise" to "Bạn nhỏ nhìn thấy chiếc bánh sinh nhật được giấu sẵn phía sau cánh cửa.",
        "disgust" to "Bạn nhỏ ngửi thấy mùi rác rất khó chịu ở gần sân chơi.",
        "disgust" to "Bạn nhỏ nếm thử món ăn có mùi vị lạ và muốn quay mặt đi."
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
    detectedConfidence: Float,
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
    searchingFaceLong: Boolean,
    remainingSeconds: Int,
    countdownValue: Int,
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
    val cameraHeight = if (isStoryMode) 334.dp else 280.dp
    val feedbackIcon = when {
        detectedMeta != null -> detectedMeta.emoji
        isStoryMode -> "📖"
        else -> targetEmotion.emoji
    }

    val activeLamp = cvConfidenceLamp(confidence = confidence, attemptSuccess = attemptSuccess)
    val childFeedbackLevel = when {
        isStoryMode && (attemptSuccess == true || challengeState == CvChallengeState.Success) -> "Tốt lắm!"
        isStoryMode && (challengeState == CvChallengeState.Failed || challengeState == CvChallengeState.Ended) -> "Mình thử lại nhé"
        isStoryMode && (challengeState == CvChallengeState.LoadingCamera || challengeState == CvChallengeState.LoadingModel ||
            challengeState == CvChallengeState.CameraReady || challengeState == CvChallengeState.Countdown) -> "Đang chuẩn bị"
        isStoryMode && challengeState != CvChallengeState.Playing -> "Đọc tình huống"
        isStoryMode && !faceDetected -> "Đưa mặt vào giữa khung nhé"
        isStoryMode && detectedMeta != null -> "Đang ghi nhận biểu cảm"
        isStoryMode -> "Con thử thể hiện rõ hơn nhé"
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
        isStoryMode && challengeState == CvChallengeState.Playing -> if (!faceDetected) "Đưa mặt vào giữa khung nhé" else "Đang ghi nhận biểu cảm"
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
        challengeState == CvChallengeState.Playing && isStoryMode && detectedMeta != null ->
            "Con đang thể hiện: ${detectedMeta.label} (${detectedConfidence.toInt()}%)."
        challengeState == CvChallengeState.Playing && isStoryMode -> "Con thử thể hiện rõ cảm xúc trong tình huống nhé."
        challengeState == CvChallengeState.Playing -> cvEmotionGuidance(targetEmotion.id)
        isStoryMode -> "Bé hãy đoán cảm xúc trong tình huống rồi thể hiện nhé."
        else -> "Con hãy làm mặt ${targetEmotion.shortLabel} nhé ${targetEmotion.emoji}"
    }
    val childFeedbackIcon = when {
        isStoryMode && detectedMeta != null -> detectedMeta.emoji
        isStoryMode -> "🎭"
        else -> targetEmotion.emoji
    }
    val childActiveLamp = when {
        attemptSuccess == true || holdProgress >= 0.99f -> 2
        childFeedbackLevel == "Gần đúng rồi" -> 1
        else -> 0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                when {
                    isStoryMode && storyScenarioText.isNullOrBlank() -> 548.dp
                    isStoryMode -> 560.dp
                    else -> 462.dp
                }
            ),
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
                if (isStoryMode) {
                    FaceGuideOverlay(
                        challengeState = challengeState,
                        faceDetected = faceDetected,
                        searchingFaceLong = searchingFaceLong,
                        holdProgress = holdProgress
                    )
                    ChallengeStatusChip(
                        text = cvChallengeStatusLabelV2(challengeState, faceDetected, searchingFaceLong),
                        color = cvChallengeStatusColorV2(challengeState, faceDetected),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    )
                    val secondaryChipText = when {
                        timerActive -> formatCvTime(remainingSeconds)
                        challengeState == CvChallengeState.FaceDetected ||
                            challengeState == CvChallengeState.Countdown -> "Sắp bắt đầu"
                        else -> null
                    }
                    secondaryChipText?.let { text ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.94f),
                            border = BorderStroke(
                                1.dp,
                                if (timerActive && remainingSeconds <= 5) Color(0xFFFDA4AF) else EgDesign.cardBorder
                            )
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                color = if (timerActive && remainingSeconds <= 5) Color(0xFFBE123C) else EgDesign.blue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    if (challengeState == CvChallengeState.Countdown) {
                        CountdownOverlay(value = countdownValue.coerceIn(1, 3))
                    }
                } else {
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
    isActive: Boolean = true,
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
    val activeState = rememberUpdatedState(isActive)

    Box(modifier = modifier) {
        CameraXFramePreview(
            modifier = Modifier.fillMaxSize(),
            isActive = isActive,
            onCameraReady = onReady,
            onCameraError = { error -> onError(error.message ?: "Không thể mở camera.") },
            onFrame = { base64Frame, rotationDegrees ->
                if (!activeState.value || !detectorReadyForFrames.value || !frameInFlight.compareAndSet(false, true)) {
                    return@CameraXFramePreview
                }
                mainHandler.post {
                    val webView = detectorWebView.value
                    if (webView == null || !activeState.value || !detectorReadyForFrames.value) {
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
    fun onDetection(emotionId: String?, confidence: Double) {
        mainHandler.post {
            onDetection(
                emotionId?.takeIf { it.isNotBlank() },
                normalizeJsConfidence(confidence.toFloat())
            )
        }
    }

    @JavascriptInterface
    fun onMatched(confidence: Double) {
        mainHandler.post {
            onMatched(normalizeJsConfidence(confidence.toFloat()))
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
    isActive: Boolean = true,
    onCameraReady: () -> Unit,
    onCameraError: (Throwable) -> Unit,
    onFrame: (String, Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: Executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val readyCallback = rememberUpdatedState(onCameraReady)
    val frameCallback = rememberUpdatedState(onFrame)
    val errorCallback = rememberUpdatedState(onCameraError)
    val activeState = rememberUpdatedState(isActive)
    val analysisRef = remember { arrayOfNulls<ImageAnalysis>(1) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val disposed = AtomicBoolean(false)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                if (!disposed.get()) {
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        if (disposed.get()) {
                            cameraProvider.unbindAll()
                            return@runCatching
                        }
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(320, 240))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                analysisRef[0] = imageAnalysis
                                imageAnalysis.setAnalyzer(
                                    analyzerExecutor,
                                    CvFrameAnalyzer(isActive = { activeState.value }) { base64Frame, rotationDegrees ->
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
                        if (disposed.get()) {
                            analysis.clearAnalyzer()
                            analysisRef[0] = null
                            return@runCatching
                        }
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                        previewView.post {
                            if (!disposed.get()) {
                                readyCallback.value.invoke()
                            }
                        }
                    }.onFailure { error ->
                        if (!disposed.get()) {
                            errorCallback.value.invoke(error)
                        }
                    }
                }
            },
            mainExecutor
        )
        onDispose {
            disposed.set(true)
            Log.d(CvLogTag, "stopAnalyzer releaseCamera")
            runCatching { analysisRef[0]?.clearAnalyzer() }
            analysisRef[0] = null
            runCatching {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            }
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private class CvFrameAnalyzer(
    private val isActive: () -> Boolean = { true },
    private val onFrame: (String, Int) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastFrameMs = 0L

    override fun analyze(image: ImageProxy) {
        if (!isActive()) {
            image.close()
            return
        }
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
    return cvChallengeInstruction(emotionId)
}

private fun cvChallengeInstruction(emotionId: String): String {
    return when (normalizeCvEmotion(emotionId)) {
        "happy" -> "Con hãy cười thật tươi nhé."
        "sad" -> "Con thử làm khuôn mặt buồn nhé."
        "angry" -> "Con thử nhíu mày như đang tức giận nhé."
        "fear" -> "Con mở mắt to như đang sợ nhé."
        "surprise" -> "Con mở mắt to và há miệng nhẹ nhé."
        "disgust" -> "Con thử nhăn mũi như không thích mùi gì đó nhé."
        else -> "Con hãy nhìn vào camera và thử biểu cảm nhé."
    }
}

private fun cvShortChallengeInstruction(emotionId: String): String {
    val holdSeconds = CvRequiredHoldMs / 1000
    return when (normalizeCvEmotion(emotionId)) {
        "happy" -> "Cười thật tươi trong ${holdSeconds} giây."
        "sad" -> "Làm khuôn mặt buồn trong ${holdSeconds} giây."
        "angry" -> "Nhíu mày tức giận trong ${holdSeconds} giây."
        "fear" -> "Mở mắt như đang sợ trong ${holdSeconds} giây."
        "surprise" -> "Mở mắt to và há miệng nhẹ trong ${holdSeconds} giây."
        "disgust" -> "Nhăn mũi như không thích mùi gì đó trong ${holdSeconds} giây."
        else -> "Làm đúng biểu cảm trong ${holdSeconds} giây."
    }
}

private fun cvChallengeStatusLabel(
    state: CvChallengeState,
    faceDetected: Boolean,
    searchingFaceLong: Boolean
): String {
    return when (state) {
        CvChallengeState.RequestingPermission -> "Đang xin quyền"
        CvChallengeState.PermissionDenied -> "Cần quyền camera"
        CvChallengeState.CameraStarting -> "Đang mở camera"
        CvChallengeState.SearchingFace -> when {
            faceDetected -> "Đã thấy khuôn mặt"
            searchingFaceLong -> "Đưa mặt vào khung"
            else -> "Giữ mặt trong khung"
        }
        CvChallengeState.FaceDetected -> "Đã thấy khuôn mặt"
        CvChallengeState.Countdown -> "Chuẩn bị bắt đầu"
        CvChallengeState.Detecting -> "Đang nhận diện"
        CvChallengeState.Success -> "Hoàn thành"
        CvChallengeState.Timeout -> "Thử lại nhé"
        CvChallengeState.Error -> "Cần thử lại"
        else -> "Chuẩn bị"
    }
}

private fun cvChallengeStatusColor(state: CvChallengeState, faceDetected: Boolean): Color {
    return when (state) {
        CvChallengeState.Success -> Color(0xFF16A34A)
        CvChallengeState.Timeout -> Color(0xFFF59E0B)
        CvChallengeState.Error,
        CvChallengeState.PermissionDenied -> Color(0xFFEF4444)
        CvChallengeState.Detecting -> Color(0xFF0284C7)
        CvChallengeState.FaceDetected,
        CvChallengeState.Countdown -> Color(0xFF2563EB)
        CvChallengeState.SearchingFace -> if (faceDetected) Color(0xFF2563EB) else Color(0xFF64748B)
        else -> Color(0xFF64748B)
    }
}

private fun cvChallengeStatusLabelV2(
    state: CvChallengeState,
    faceDetected: Boolean,
    searchingFaceLong: Boolean
): String {
    return when (state) {
        CvChallengeState.RequestingPermission -> "Đang xin quyền"
        CvChallengeState.PermissionDenied -> "Cần quyền camera"
        CvChallengeState.CameraStarting -> "Đang mở camera"
        CvChallengeState.SearchingFace -> when {
            faceDetected -> "Đã thấy khuôn mặt"
            searchingFaceLong -> "Đưa mặt vào khung"
            else -> "Giữ mặt trong khung"
        }
        CvChallengeState.FaceDetected -> "Đã thấy khuôn mặt"
        CvChallengeState.Countdown -> "Chuẩn bị bắt đầu"
        CvChallengeState.Detecting,
        CvChallengeState.Playing -> "Đang nhận diện"
        CvChallengeState.Success -> "Hoàn thành"
        CvChallengeState.Timeout -> "Thử lại nhé"
        CvChallengeState.Error -> "Cần thử lại"
        else -> "Chuẩn bị"
    }
}

private fun cvChallengeStatusColorV2(state: CvChallengeState, faceDetected: Boolean): Color {
    return when (state) {
        CvChallengeState.Success -> Color(0xFF16A34A)
        CvChallengeState.Timeout -> Color(0xFFF59E0B)
        CvChallengeState.Error,
        CvChallengeState.PermissionDenied -> Color(0xFFEF4444)
        CvChallengeState.Detecting,
        CvChallengeState.Playing -> Color(0xFF0284C7)
        CvChallengeState.FaceDetected,
        CvChallengeState.Countdown -> Color(0xFF2563EB)
        CvChallengeState.SearchingFace -> if (faceDetected) Color(0xFF2563EB) else Color(0xFF64748B)
        else -> Color(0xFF64748B)
    }
}

private fun cvDetectionLine(
    state: CvChallengeState,
    detectedMeta: CvEmotionMeta?,
    confidence: Float,
    faceDetected: Boolean,
    searchingFaceLong: Boolean
): String {
    val percentText = "${confidence.coerceIn(0f, 100f).toInt()}%"
    return when {
        (state == CvChallengeState.Detecting || state == CvChallengeState.Playing) && detectedMeta != null -> {
            "Đang nhận diện: ${detectedMeta.label} $percentText"
        }
        (state == CvChallengeState.Detecting || state == CvChallengeState.Playing) && confidence > 0f ->
            "Đang nhận diện: $percentText"
        (state == CvChallengeState.Detecting || state == CvChallengeState.Playing) && faceDetected ->
            "Đang nhận diện: 0%"
        state == CvChallengeState.SearchingFace -> if (searchingFaceLong) {
            "App chưa thấy khuôn mặt"
        } else {
            "Giữ khuôn mặt trong khung một chút nhé"
        }
        state == CvChallengeState.FaceDetected -> "Đã thấy khuôn mặt"
        state == CvChallengeState.Countdown -> "Chuẩn bị nhé..."
        state == CvChallengeState.Success -> "Hoàn thành"
        state == CvChallengeState.Timeout -> "Hết giờ luyện tập"
        else -> "Chuẩn bị"
    }
}

private fun cvChallengeRealtimeHint(
    targetEmotionId: String,
    detectedEmotionId: String?,
    confidence: Float,
    detectedConfidence: Float,
    faceDetected: Boolean,
    searchingFaceLong: Boolean,
    holdProgress: Float,
    challengeState: CvChallengeState
): String {
    val target = normalizeCvEmotion(targetEmotionId)
    val targetConfidence = if (detectedEmotionId == target) detectedConfidence else confidence
    if (challengeState == CvChallengeState.CameraStarting) return "Đang mở camera..."
    if (challengeState == CvChallengeState.SearchingFace || !faceDetected) {
        return if (searchingFaceLong) {
            "App chưa thấy khuôn mặt, bé đưa mặt gần hơn một chút nhé."
        } else {
            "Giữ khuôn mặt trong khung một chút nhé."
        }
    }
    if (challengeState == CvChallengeState.FaceDetected) return "Đã thấy khuôn mặt! Chuẩn bị bắt đầu..."
    if (challengeState == CvChallengeState.Countdown) return "Sắp bắt đầu, con chuẩn bị nhé."
    if (challengeState == CvChallengeState.Success) return "Tốt lắm, bé đã hoàn thành thử thách."
    if (challengeState == CvChallengeState.Timeout) return "Con làm tốt rồi, mình thử thêm lần nữa nhé."

    if (detectedEmotionId == target && targetConfidence >= CvRequiredConfidence) {
        return when (target) {
            "happy" -> "Tốt lắm, giữ nụ cười thêm một chút."
            "sad" -> "Đúng rồi, giữ khuôn mặt buồn thêm một chút."
            "surprise" -> "Đúng rồi, giữ vẻ ngạc nhiên thêm một chút."
            "angry" -> "Tốt lắm, giữ khuôn mặt tức giận thêm một chút."
            "fear" -> "Giữ khuôn mặt sợ hãi thêm một chút."
            "disgust" -> "Tốt lắm, giữ nét mặt này thêm một chút."
            else -> "Tốt lắm, giữ thêm %.1f giây nhé.".format(
                (1f - holdProgress).coerceAtLeast(0f) * (CvRequiredHoldMs / 1000f)
            )
        }
    }

    if (detectedEmotionId == target && targetConfidence >= 50f) {
        return when (target) {
            "happy" -> "Gần đúng rồi, con cười rõ hơn một chút nhé."
            "sad" -> "Nhìn xuống nhẹ và làm mặt buồn hơn một chút."
            "surprise" -> "Gần đúng rồi, con mở mắt to hơn một chút nhé."
            "angry" -> "Gần đúng rồi, nhíu mày rõ hơn một chút."
            "fear" -> "Con mở mắt to như đang sợ nhé."
            "disgust" -> "Gần đúng rồi, nhăn mặt rõ hơn một chút."
            else -> "Gần đúng rồi, con giữ biểu cảm rõ hơn nhé."
        }
    }

    return when (target) {
        "happy" -> "Con hãy cười thật tươi nhé."
        "sad" -> "Con thử làm khuôn mặt buồn nhé."
        "surprise" -> "Con mở mắt to và há miệng nhẹ nhé."
        "angry" -> "Con thử nhíu mày như đang tức giận nhé."
        "fear" -> "Con mở mắt to như đang sợ nhé."
        "disgust" -> "Con thử nhăn mũi như không thích mùi gì đó nhé."
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
                !cameraMessage.isNullOrBlank() || challengeState == CvChallengeState.PermissionDenied -> {
                    if (isStoryMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                border = BorderStroke(1.dp, EgDesign.cardBorder)
                            ) {
                                Text("Thử lại camera", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onNext,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                            ) {
                                Text("Câu tiếp theo", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = onStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                        ) {
                            Text("Mở camera", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
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

private fun cvStoryCheckpointKey(userId: String, gameId: String, level: Int): String {
    return "$userId::$gameId::$level"
}

private fun saveCvStoryCheckpoint(
    context: Context,
    userId: String,
    gameId: String,
    level: Int,
    checkpoint: CvStoryCheckpoint
) {
    val root = JSONObject().apply {
        if (checkpoint.sessionId == null) {
            put("session_id", JSONObject.NULL)
        } else {
            put("session_id", checkpoint.sessionId)
        }
        put("score", checkpoint.score)
        put("max_errors", checkpoint.maxErrors)
        put("current_index", checkpoint.currentIndex)
        put("saved_at_ms", checkpoint.savedAtMs)
        put(
            "questions",
            JSONArray().apply {
                checkpoint.questions.forEach { question ->
                    put(
                        JSONObject().apply {
                            put("question_id", question.questionId)
                            put("question_text", question.prompt.questionText)
                            put("correct_answer", question.prompt.correctAnswer)
                        }
                    )
                }
            }
        )
        put(
            "results",
            JSONArray().apply {
                checkpoint.results.forEach { result ->
                    put(
                        JSONObject().apply {
                            put("question_id", result.questionId)
                            if (result.answer == null) put("answer", JSONObject.NULL) else put("answer", result.answer)
                            put("is_correct", result.isCorrect)
                            put("response_time_ms", result.responseTimeMs)
                            put("used_hint", result.usedHint)
                            if (result.cvConfidence == null) put("cv_confidence", JSONObject.NULL) else put("cv_confidence", result.cvConfidence)
                        }
                    )
                }
            }
        )
    }
    val preferences = context.getSharedPreferences(CvStoryCheckpointPref, Context.MODE_PRIVATE)
    preferences.edit()
        .putString(cvStoryCheckpointKey(userId, gameId, level), root.toString())
        .commit()
}

private fun loadCvStoryCheckpoint(
    context: Context,
    userId: String,
    gameId: String,
    level: Int
): CvStoryCheckpoint? {
    val preferences = context.getSharedPreferences(CvStoryCheckpointPref, Context.MODE_PRIVATE)
    val key = cvStoryCheckpointKey(userId, gameId, level)
    val raw = preferences.getString(key, null) ?: return null
    return runCatching {
        val root = JSONObject(raw)
        val savedAtMs = root.optLong("saved_at_ms", 0L)
        if (savedAtMs <= 0L || System.currentTimeMillis() - savedAtMs > CvStoryCheckpointTtlMs) {
            preferences.edit().remove(key).apply()
            return null
        }
        val sessionId = root
            .optString("session_id")
            .takeIf { it.isNotBlank() }
        val maxErrors = root.optInt("max_errors", 2).coerceAtLeast(1)
        val score = root.optInt("score", 0).coerceAtLeast(0)
        val currentIndex = root.optInt("current_index", 0).coerceAtLeast(0)
        val questionsArray = root.optJSONArray("questions") ?: return null
        val questions = buildList {
            for (index in 0 until questionsArray.length()) {
                val item = questionsArray.optJSONObject(index) ?: continue
                val questionId = item.optString("question_id")
                val questionText = item.optString("question_text")
                val correctAnswer = item.optString("correct_answer")
                if (questionId.isBlank() || correctAnswer.isBlank()) continue
                add(
                    CvQuestionUi(
                        questionId = questionId,
                        prompt = CvPromptUiItem(
                            questionText = questionText,
                            correctAnswer = correctAnswer
                        )
                    )
                )
            }
        }
        if (questions.isEmpty()) return null
        val resultsArray = root.optJSONArray("results") ?: JSONArray()
        val results = buildList {
            for (index in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(index) ?: continue
                val questionId = item.optString("question_id")
                if (questionId.isBlank()) continue
                val answer = if (item.isNull("answer")) null else item.optString("answer")
                val cvConfidence = if (item.isNull("cv_confidence")) null else item.optDouble("cv_confidence", 0.0).toFloat()
                add(
                    AnswerResultDto(
                        questionId = questionId,
                        answer = answer,
                        isCorrect = item.optBoolean("is_correct", false),
                        responseTimeMs = item.optInt("response_time_ms", 0),
                        usedHint = item.optBoolean("used_hint", false),
                        cvConfidence = cvConfidence
                    )
                )
            }
        }
        if (results.size >= questions.size) {
            preferences.edit().remove(key).apply()
            return null
        }
        CvStoryCheckpoint(
            sessionId = sessionId,
            score = score,
            maxErrors = maxErrors,
            currentIndex = currentIndex.coerceIn(0, (questions.size - 1).coerceAtLeast(0)),
            questions = questions,
            results = results,
            savedAtMs = savedAtMs
        )
    }.getOrNull()
}

private fun clearCvStoryCheckpoint(
    context: Context,
    userId: String,
    gameId: String,
    level: Int
) {
    val preferences = context.getSharedPreferences(CvStoryCheckpointPref, Context.MODE_PRIVATE)
    preferences.edit().remove(cvStoryCheckpointKey(userId, gameId, level)).commit()
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

private fun normalizeJsConfidence(value: Float): Float {
    val normalized = if (value in 0f..1f) value * 100f else value
    return normalized.coerceIn(0f, 100f)
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

private fun sanitizeCvStoryScenarioPrompt(prompt: String): String {
    return prompt
        .replace(Regex("\\s*Hãy chọn cảm xúc phù hợp( với tình huống)?\\.", RegexOption.IGNORE_CASE), ".")
        .replace(Regex("\\s*Cảm xúc nào phù hợp nhất\\?", RegexOption.IGNORE_CASE), ".")
        .replace(Regex("\\s*Theo con, bạn ấy đang có cảm xúc nào\\?", RegexOption.IGNORE_CASE), ".")
        .replace(Regex("\\s*Theo con, bạn ấy đang cảm thấy thế nào\\?", RegexOption.IGNORE_CASE), ".")
        .replace(Regex("\\s*Theo con, khuôn mặt của bạn ấy sẽ như thế nào\\?", RegexOption.IGNORE_CASE), ".")
        .replace(Regex("\\s*Theo con, khuôn mặt của bạn ấy sẽ thể hiện điều gì\\?", RegexOption.IGNORE_CASE), ".")
        .replace(Regex("\\.{2,}"), ".")
        .trim()
}

private fun displayCvTitle(rawTitle: String, gameId: String): String {
    if (rawTitle.isNotBlank() && !hasEncodingIssue(rawTitle)) return rawTitle
    return if (gameId == GameUiCatalog.GAME_CV_STORY) {
        "Câu chuyện khuôn mặt"
    } else {
        "Thử thách biểu cảm"
    }
}

private fun hasEncodingIssue(value: String): Boolean {
    return value.contains("Ã") || value.contains("Â") || value.contains("Æ") || value.contains("áº") || value.contains("ðŸ")
}
