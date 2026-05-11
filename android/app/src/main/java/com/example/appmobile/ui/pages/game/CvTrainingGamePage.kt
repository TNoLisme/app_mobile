package com.example.appmobile.ui.pages.game

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import kotlin.random.Random

private const val CvRoundSeconds = 30
private const val CvRequiredConfidence = 60f
private const val CvStoryQuestionsPerLevel = 5

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
    val isStoryMode = gameId == GameUiCatalog.GAME_CV_STORY

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
    val questionStartMs = remember(level, gameId) { mutableStateOf(System.currentTimeMillis()) }
    val maxErrors = remember(level, gameId) { mutableIntStateOf(2) }
    val emotionErrors = remember(level, gameId) { mutableStateMapOf<String, Int>() }
    val learnedEmotions = remember(level, gameId) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level, gameId) { mutableStateOf<String?>(null) }
    val startRequested = remember(level, gameId) { mutableStateOf(false) }
    val isRequestingCameraPermission = remember(level, gameId) { mutableStateOf(false) }
    val challengeStarted = remember(level, gameId) { mutableStateOf(false) }
    val detectorReady = remember(level, gameId) { mutableStateOf(false) }
    val remainingSeconds = remember(level, gameId) { mutableIntStateOf(CvRoundSeconds) }
    val currentConfidence = remember(level, gameId) { mutableStateOf(0f) }
    val detectedEmotion = remember(level, gameId) { mutableStateOf<String?>(null) }
    val cameraMessage = remember(level, gameId) { mutableStateOf<String?>(null) }
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
        detectorReady.value = false
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
        detectorReady.value = false
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
            summary.value = if (isStoryMode) {
                "Hoàn thành cấp độ!\nSố câu hoàn thành: ${finalResults.size}/${questions.value.size}.\nĐiểm: ${response?.score ?: score.intValue}."
            } else if (response != null) {
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
        detectorReady.value = false
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
            listOf(CvQuestionUi("fallback-$gameId-${selectedEmotionKey ?: "default"}", fallbackPrompt))
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

    val currentQuestion = questions.value[currentIndex.intValue % questions.value.size]
    val targetEmotion = cvEmotionMeta(currentQuestion.prompt.correctAnswer)
    val promptText = displayCvPrompt(currentQuestion.prompt.questionText, targetEmotion)
    val storyPromptText = if (isStoryMode) {
        hideCvAnswerInStoryPrompt(promptText, targetEmotion)
    } else {
        promptText
    }
    val canShowCameraStep = !isStoryMode || storyReadyToAct.value
    val cameraMayStart = canShowCameraStep && startRequested.value && hasCameraPermission.value && cameraMessage.value == null
    val cameraReady = cameraMayStart && detectorReady.value
    val timerActive = canShowCameraStep && cameraReady && challengeStarted.value && feedback.value == null && summary.value == null

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
                onCameraError = {
                    cameraMessage.value = "Không thể mở camera. Vui lòng thử lại."
                    challengeStarted.value = false
                    detectorReady.value = false
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
                    isStoryMode = isStoryMode,
                    storyScenarioTitle = if (isStoryMode) "Tình huống ${currentIndex.intValue + 1}" else null,
                    storyScenarioText = if (isStoryMode) storyPromptText else null,
                    isLastQuestion = currentIndex.intValue >= questions.value.lastIndex,
                    isSubmitting = isSubmitting.value,
                    onCameraReady = {
                        detectorReady.value = true
                        cameraMessage.value = null
                    },
                    onDetection = { emotionId, confidence ->
                        detectedEmotion.value = emotionId?.takeIf { it.isNotBlank() }?.let(::normalizeCvEmotion)
                        currentConfidence.value = confidence.coerceIn(0f, 100f)
                    },
                    onEmotionMatched = { confidence ->
                        recordAttempt(success = true, confidence = confidence.coerceIn(0f, 100f))
                    },
                onStart = {
                    startRequested.value = true
                    detectorReady.value = false
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
    cameraReady: Boolean,
    challengeStarted: Boolean,
    startRequested: Boolean,
    isRequestingCameraPermission: Boolean,
    remainingSeconds: Int,
    timerActive: Boolean,
    isStoryMode: Boolean,
    storyScenarioTitle: String?,
    storyScenarioText: String?,
    isLastQuestion: Boolean,
    isSubmitting: Boolean,
    onCameraReady: () -> Unit,
    onDetection: (String?, Float) -> Unit,
    onEmotionMatched: (Float) -> Unit,
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
    val displayTitle = if (isStoryMode) {
        when {
            isRequestingCameraPermission -> "Đang chuẩn bị camera..."
            !startRequested -> "Đọc tình huống"
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
            isRequestingCameraPermission -> "Vui lòng cấp quyền camera để bắt đầu."
            !startRequested -> "Bé hãy đoán cảm xúc trong tình huống rồi thể hiện nhé."
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

    val activeLamp = when {
        attemptSuccess == true -> 2
        challengeStarted || confidence >= CvRequiredConfidence -> 1
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
                if (hasPermission && challengeStarted && feedback == null) {
                    CvEmotionDetectorCamera(
                        modifier = Modifier.fillMaxSize(),
                        targetEmotionId = targetEmotion.id,
                        onReady = onCameraReady,
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
                            CvStatusLamps(activeLamp = activeLamp)
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
                        Text(feedbackIcon, fontSize = 30.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (startRequested) {
                        Text(
                            displayTitle,
                            color = EgDesign.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = displaySubtitle,
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
                    Text(
                        text = "${confidence.toInt()}%",
                        color = EgDesign.blue,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            CvActionRow(
                cameraReady = cameraReady,
                challengeStarted = challengeStarted,
                startRequested = startRequested,
                isRequestingCameraPermission = isRequestingCameraPermission,
                feedback = feedback,
                attemptSuccess = attemptSuccess,
                isStoryMode = isStoryMode,
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CvEmotionDetectorCamera(
    modifier: Modifier = Modifier,
    targetEmotionId: String,
    onReady: () -> Unit,
    onDetection: (String?, Float) -> Unit,
    onMatched: (Float) -> Unit,
    onError: (String) -> Unit
) {
    val readyCallback = rememberUpdatedState(onReady)
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
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                addJavascriptInterface(
                    CvEmotionJsBridge(
                        onReady = { readyCallback.value.invoke() },
                        onDetection = { emotion, confidence -> detectionCallback.value.invoke(emotion, confidence) },
                        onMatched = { confidence -> matchedCallback.value.invoke(confidence) },
                        onError = { message -> errorCallback.value.invoke(message) }
                    ),
                    "AndroidCvBridge"
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        request.grant(request.resources)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            "window.startCvDetector && window.startCvDetector('$targetEmotionId');",
                            null
                        )
                    }
                }
                loadUrl("file:///android_asset/cv_emotion_detector.html")
            }
        }
    )
}

private class CvEmotionJsBridge(
    private val onReady: () -> Unit,
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
    isStoryMode: Boolean,
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
                        isLastQuestion -> if (isStoryMode) "Xem kết quả" else if (attemptSuccess == true) "Hoàn thành" else "Kết thúc lượt"
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
