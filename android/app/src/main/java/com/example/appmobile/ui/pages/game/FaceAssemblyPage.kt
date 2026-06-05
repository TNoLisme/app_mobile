package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.AppBackButton
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgEmotionVectorIcon
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.components.GameScreenShell
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class FaceEmotionUi(
    val id: String,
    val label: String,
    val spriteRes: Int
)

private data class AssemblyQuestionUi(
    val questionId: String,
    val text: String,
    val targetEmotion: String,
    val mediaPath: String? = null,
    val explanation: String? = null
)

private val faceEmotions = listOf(
    FaceEmotionUi("happy", "Vui vẻ", R.drawable.face_ensemble_happy),
    FaceEmotionUi("sad", "Buồn bã", R.drawable.face_ensemble_sad),
    FaceEmotionUi("angry", "Tức giận", R.drawable.face_ensemble_angry),
    FaceEmotionUi("fear", "Sợ hãi", R.drawable.face_ensemble_fear),
    FaceEmotionUi("surprise", "Ngạc nhiên", R.drawable.face_ensemble_surprise),
    FaceEmotionUi("disgust", "Ghê tởm", R.drawable.face_ensemble_disgust)
)

@Composable
fun FaceAssemblyPage(
    level: Int = 1,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit = {},
    onGameCompleted: (Int) -> Unit = {}
) {
    val selectedEyebrow = remember(level) { mutableIntStateOf(-1) }
    val selectedEyes    = remember(level) { mutableIntStateOf(-1) }
    val selectedMouth   = remember(level) { mutableIntStateOf(-1) }
    val currentIndex    = remember(level) { mutableIntStateOf(0) }
    val score           = remember(level) { mutableIntStateOf(0) }
    val feedback        = remember(level) { mutableStateOf<String?>(null) }
    val questions       = remember(level) { mutableStateOf(fallbackAssemblyQuestions()) }
    val sessionId       = remember(level) { mutableStateOf<String?>(null) }
    val results         = remember(level) { mutableStateOf<List<AnswerResultDto>>(emptyList()) }
    val summary         = remember(level) { mutableStateOf<String?>(null) }
    val replayCount     = remember { mutableIntStateOf(0) }
    val isSubmitting    = remember(level) { mutableStateOf(false) }
    val questionStartMs = remember(level) { mutableStateOf(System.currentTimeMillis()) }
    val maxErrors       = remember(level) { mutableIntStateOf(3) }
    val emotionErrors   = remember(level) { mutableStateMapOf<String, Int>() }
    val accumulatedErrors = remember(level) { mutableStateMapOf<String, Int>() }
    val learnedEmotions   = remember(level) { mutableStateListOf<String>() }
    val learningEmotionId = remember(level) { mutableStateOf<String?>(null) }
    val pendingLearnEmotion = remember(level) { mutableStateOf<String?>(null) }
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val userId   = remember { FirebaseAuth.getInstance().currentUser?.uid ?: AppSession.currentBackendUserId() ?: "local-player" }
    val repository = remember {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }
    val summaryData = remember(level) { mutableStateOf<LevelSummaryData?>(null) }
    fun resetCurrentQuestion() {
        resetSelections(selectedEyebrow, selectedEyes, selectedMouth)
        feedback.value = null
        questionStartMs.value = System.currentTimeMillis()
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
                    summaryData.value = LevelSummaryData(
                        passed = response.passed, score = response.score, totalScore = 50,
                        accuracy = response.accuracy,
                        correctCount = finalResults.count { it.isCorrect },
                        totalQuestions = finalResults.size
                    )
                    repository.invalidateProgressCache(GameUiCatalog.GAME_FACE_ASSEMBLY, userId)
                    summary.value = "${if (response.passed) "Đã qua level" else "Chưa qua level"}. Điểm: ${response.score}/50."
                } else {
                    summary.value = "Hoàn thành. Điểm tạm tính: ${score.intValue}."
                }
            } catch (_: Exception) {
                summary.value = "Hoàn thành. Điểm tạm tính: ${score.intValue}."
            } finally {
                onGameCompleted(summaryData.value?.score ?: score.intValue)
                isSubmitting.value = false
            }
        }
    }

    fun recordCurrentAnswer(target: FaceEmotionUi) {
        if (feedback.value != null) return
        val targetIdx = targetIndex(target.id)
        val isCorrect = selectedEyebrow.intValue == targetIdx &&
            selectedEyes.intValue == targetIdx && selectedMouth.intValue == targetIdx
        if (isCorrect) score.intValue += 10
        val reviewEmotion = normalizeEmotionForLearning(target.id)
        if (!isCorrect) {
            emotionErrors[reviewEmotion] = (emotionErrors[reviewEmotion] ?: 0) + 1
            val total = (emotionErrors[reviewEmotion] ?: 0) + (accumulatedErrors[reviewEmotion] ?: 0)
            if (total >= maxErrors.intValue && reviewEmotion !in learnedEmotions) {
                learnedEmotions.add(reviewEmotion)
                pendingLearnEmotion.value = reviewEmotion
                learningEmotionId.value = reviewEmotion
            }
        }
        val selectedAnswer = if (
            selectedEyebrow.intValue == selectedEyes.intValue &&
            selectedEyes.intValue == selectedMouth.intValue
        ) faceEmotions.getOrNull(selectedEyebrow.intValue)?.id ?: "unknown" else "mixed"

        val question = questions.value[currentIndex.intValue]
        results.value = results.value + AnswerResultDto(
            questionId = question.questionId, answer = selectedAnswer, isCorrect = isCorrect,
            responseTimeMs = (System.currentTimeMillis() - questionStartMs.value).toInt()
        )
        feedback.value = if (isCorrect)
            "Đúng rồi! Con đã ghép khuôn mặt ${target.label}."
        else
            "Chưa đúng. Đáp án là khuôn mặt ${target.label}."
    }

    fun goNextOrFinish() {
        if (currentIndex.intValue >= questions.value.lastIndex) { finishLevel(results.value); return }
        currentIndex.intValue += 1
        resetCurrentQuestion()
    }

    LaunchedEffect(level, userId, replayCount.intValue) {
        val started = repository.startGame(GameUiCatalog.GAME_FACE_ASSEMBLY, userId, level)
        sessionId.value = started?.sessionId
        maxErrors.intValue = started?.maxErrors ?: 3
        val backendQuestions = started?.questions?.mapNotNull { content ->
            val emotion = normalizeEmotionForLearning(
                (content.correctAnswer ?: content.emotion ?: "").ifBlank { return@mapNotNull null }
            )
            AssemblyQuestionUi(
                questionId = content.contentId,
                text = content.questionText?.ifBlank { "Hình này thể hiện cảm xúc gì?" } ?: "Hình này thể hiện cảm xúc gì?",
                targetEmotion = emotion, mediaPath = content.mediaPath, explanation = content.explanation
            )
        }.orEmpty()
        questions.value = backendQuestions.ifEmpty { fallbackAssemblyQuestions() }
        currentIndex.intValue = 0; score.intValue = 0; results.value = emptyList()
        summary.value = null; summaryData.value = null
        emotionErrors.clear(); accumulatedErrors.clear()
        started?.reviewEmotions?.forEach { (emotion, count) ->
            accumulatedErrors[normalizeEmotionForLearning(emotion)] = count
        }
        learnedEmotions.clear(); learningEmotionId.value = null; pendingLearnEmotion.value = null
        resetCurrentQuestion()
    }

    val question    = questions.value[currentIndex.intValue % questions.value.size]
    val target      = faceEmotions.firstOrNull { it.id == question.targetEmotion } ?: faceEmotions[0]
    val totalQ      = questions.value.size
    val progress    = (currentIndex.intValue + 1).toFloat() / totalQ.toFloat()
    val hasFeedback = feedback.value != null
    val canCheck    = selectedEyebrow.intValue >= 0 && selectedEyes.intValue >= 0 &&
                      selectedMouth.intValue >= 0 && !hasFeedback

    // ──────────────────────────────────────────────────────────────────────────
    GameScreenShell(contentMaxWidth = 900, onOpenAssistant = onOpenAssistant,
        scrollEnabled = false, bottomSpacerHeight = 0.dp) {

        Column(modifier = Modifier.fillMaxSize().background(EgDesign.background)) {

            if (summary.value != null) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(16.dp))
                    GameLevelSummaryCard(
                        summaryData = summaryData.value,
                        summary = summary.value.orEmpty(),
                        onBack = onBack, onReplay = { replayCount.intValue++ }
                    )
                }
            } else {

                /* ═══ TOP BAR ═══ */
                val totalQ = questions.value.size
                GameHeader(
                    title = "Xưởng lắp ghép cảm xúc",
                    level = level,
                    currentQuestion = currentIndex.intValue + 1,
                    totalQuestions = totalQ,
                    score = score.intValue,
                    onBack = onBack
                )

                /* ═══ CARD 1 – Question only (blue, compact) ═══ */
                ClickGameInstructionCard(
                    title = "Quan sát và lắp đúng khuôn mặt",
                    description = question.text,
                    iconKey = "puzzle"
                )

                Spacer(Modifier.height(6.dp))

                /* ═══ CARD 2 – Image (white, weight) ═══ */
                Card(
                    modifier = Modifier.fillMaxWidth().weight(2f),
                    shape = RoundedCornerShape(EgDesign.radiusLarge),
                    colors = CardDefaults.cardColors(containerColor = EgDesign.card),
                    border = BorderStroke(1.dp, EgDesign.cardBorder),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                            .clip(RoundedCornerShape(EgDesign.radiusMedium))
                            .background(EgDesign.cardSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!question.mediaPath.isNullOrBlank()) {
                            val assetPath = if (question.mediaPath.startsWith("/"))
                                "file:///android_asset${question.mediaPath}"
                            else "file:///android_asset/${question.mediaPath}"
                            AsyncImage(
                                model = assetPath, contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            EgEmotionVectorIcon(target.id, size = 68.dp)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                /* ═══ CARD 3 – Face preview + controls (2 col × 3 row, weight) ═══ */
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1.8f),
                    shape = RoundedCornerShape(EgDesign.radiusLarge),
                    colors = CardDefaults.cardColors(containerColor = EgDesign.card),
                    border = BorderStroke(1.dp, EgDesign.cardBorder),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Row 1 – Lông mày
                        FaceAssemblyRow(
                            emotionIndex = selectedEyebrow.intValue,
                            partIndex = 0,
                            icon = "eye",
                            label = "Lông mày",
                            enabled = !hasFeedback && !isSubmitting.value,
                            onClick = { selectedEyebrow.intValue = nextEmotionIndex(selectedEyebrow.intValue) },
                            modifier = Modifier.weight(1f)
                        )
                        HorizontalDivider(color = EgDesign.cardBorder, thickness = 0.5.dp)
                        // Row 2 – Mắt
                        FaceAssemblyRow(
                            emotionIndex = selectedEyes.intValue,
                            partIndex = 1,
                            icon = "eye",
                            label = "Mắt",
                            enabled = !hasFeedback && !isSubmitting.value,
                            onClick = { selectedEyes.intValue = nextEmotionIndex(selectedEyes.intValue) },
                            modifier = Modifier.weight(1f)
                        )
                        HorizontalDivider(color = EgDesign.cardBorder, thickness = 0.5.dp)
                        // Row 3 – Miệng
                        FaceAssemblyRow(
                            emotionIndex = selectedMouth.intValue,
                            partIndex = 2,
                            icon = "mouth",
                            label = "Miệng",
                            enabled = !hasFeedback && !isSubmitting.value,
                            onClick = { selectedMouth.intValue = nextEmotionIndex(selectedMouth.intValue) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                /* ═══ FEEDBACK strip (fixed height – does not shift layout) ═══ */
                Box(
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasFeedback) GameFeedbackCard(feedback.value.orEmpty())
                }

                /* ═══ BOTTOM BUTTONS ═══ */
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thử lại
                        OutlinedButton(
                            onClick = { resetCurrentQuestion() },
                            enabled = !hasFeedback && !isSubmitting.value,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(EgDesign.pillRadius),
                            border = BorderStroke(1.5.dp, EgDesign.cardBorder)
                        ) {
                            Text("Thử lại", fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = EgDesign.textPrimary,
                                maxLines = 1)
                        }
                        // Kiểm tra / Câu tiếp / Hoàn thành
                        Button(
                            onClick = {
                                if (!hasFeedback) {
                                    recordCurrentAnswer(target)
                                } else {
                                    if (pendingLearnEmotion.value != null) {
                                        learningEmotionId.value = pendingLearnEmotion.value
                                        return@Button
                                    }
                                    goNextOrFinish()
                                }
                            },
                            enabled = (canCheck || hasFeedback) && !isSubmitting.value && learningEmotionId.value == null,
                            modifier = Modifier.weight(1.5f).height(46.dp),
                            shape = RoundedCornerShape(EgDesign.pillRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                        ) {
                            val learnTarget = pendingLearnEmotion.value?.let { GameUiCatalog.emotionById(it)?.name ?: it }
                            Text(
                                when {
                                    isSubmitting.value -> "Đang lưu..."
                                    !hasFeedback -> "Kiểm tra"
                                    pendingLearnEmotion.value != null -> "Học $learnTarget"
                                    currentIndex.intValue >= questions.value.lastIndex -> "Hoàn thành"
                                    else -> "Câu tiếp →"
                                },
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = Color.White, maxLines = 1
                            )
                        }
                    }
                }

            } // end else (non-summary)
        }

        EmotionLearningDialog(
            emotionId = learningEmotionId.value,
            onDismiss = {
                val emotion = learningEmotionId.value
                learningEmotionId.value = null; pendingLearnEmotion.value = null
                if (emotion != null) {
                    emotionErrors[emotion] = 0; accumulatedErrors[emotion] = 0
                    scope.launch {
                        try { repository.resetReviewEmotions(GameUiCatalog.GAME_FACE_ASSEMBLY, userId, listOf(emotion)) }
                        catch (_: Exception) {}
                    }
                }
            }
        )
    }
}

/**
 * Single row: left = face sprite slice, right = icon + label.
 * Clicking anywhere cycles that face part.
 */
@Composable
private fun FaceAssemblyRow(
    emotionIndex: Int,
    partIndex: Int,
    icon: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left column – sprite preview (2/3 width)
        Box(
            modifier = Modifier.weight(2f).fillMaxHeight().background(EgDesign.cardSoft),
            contentAlignment = Alignment.Center
        ) {
            if (emotionIndex < 0 || emotionIndex >= faceEmotions.size) {
                Text("?", fontSize = 20.sp, color = EgDesign.cardBorder)
            } else {
                val bitmap = ImageBitmap.imageResource(id = faceEmotions[emotionIndex].spriteRes)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val partH = bitmap.height / 3
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(0, partIndex * partH),
                        srcSize = IntSize(bitmap.width, partH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                }
            }
        }

        // Vertical separator
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = 0.5.dp,
            color = EgDesign.cardBorder
        )

        // Right column – compact part selector.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EgVectorEmojiIcon(icon, size = 20.dp, tint = EgDesign.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = EgDesign.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/* ── Utilities ── */
private fun fallbackAssemblyQuestions(): List<AssemblyQuestionUi> = listOf(
    AssemblyQuestionUi("fallback-assembly-happy",    "Hình này thể hiện cảm xúc gì?", "happy",    explanation = "Mắt híp lại, miệng cười tươi."),
    AssemblyQuestionUi("fallback-assembly-sad",      "Hình này thể hiện cảm xúc gì?", "sad",      explanation = "Lông mày chùng xuống, miệng mếu."),
    AssemblyQuestionUi("fallback-assembly-angry",    "Hình này thể hiện cảm xúc gì?", "angry",    explanation = "Lông mày nhíu chặt, miệng gắt gỏng."),
    AssemblyQuestionUi("fallback-assembly-fear",     "Hình này thể hiện cảm xúc gì?", "fear",     explanation = "Mắt mở to và miệng há hốc sợ sệt."),
    AssemblyQuestionUi("fallback-assembly-surprise", "Hình này thể hiện cảm xúc gì?", "surprise", explanation = "Lông mày rướn cao, mắt tròn xoe.")
)

private fun targetIndex(emotionId: String): Int = faceEmotions.indexOfFirst { it.id == emotionId }
private fun nextEmotionIndex(current: Int): Int  = if (current < 0) 0 else (current + 1) % faceEmotions.size
private fun resetSelections(eb: MutableIntState, ey: MutableIntState, mo: MutableIntState) {
    eb.intValue = -1; ey.intValue = -1; mo.intValue = -1
}
