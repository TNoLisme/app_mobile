package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appmobile.R
import com.example.appmobile.data.remote.dto.GameContentOptionDto
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.EgEmotionCardBackground
import com.example.appmobile.ui.components.EgEmotionCardBorder
import com.example.appmobile.ui.components.EgEmotionCardSelectedBackground
import com.example.appmobile.ui.components.EgEmotionCardSelectedBorder
import com.example.appmobile.ui.components.EgEmotionVectorIcon
import com.example.appmobile.ui.components.EgVectorEmojiIcon
import com.example.appmobile.ui.components.egTactileClick
import com.example.appmobile.ui.state.AppSettingsState
import coil.compose.AsyncImage
import android.content.Context
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import com.example.appmobile.data.remote.dto.AnswerResultDto

data class EmotionLearningInfo(
    val id: String,
    val title: String,
    val emoji: String,
    val description: String,
    val situation: String,
    val cues: List<String>
)

data class AnswerVisualState(
    val borderColor: Color,
    val containerColor: Color
)

fun scoreFromCorrectAnswers(correctCount: Int, totalQuestions: Int): Int {
    val total = totalQuestions.coerceAtLeast(1)
    val correct = correctCount.coerceIn(0, total)
    return ((correct * 100f) / total).roundToInt().coerceIn(0, 100)
}

private const val ClickGameCheckpointPref = "click_game_checkpoint"
private const val ClickGameCheckpointTtlMs = 24L * 60L * 60L * 1000L
private const val GameUnlockedLevelPref = "game_unlocked_level"

data class ClickGameResumePreview(
    val answeredCount: Int,
    val totalCount: Int
)

fun clickGameCheckpointKey(userId: String, gameId: String, level: Int): String {
    return "$userId::$gameId::$level"
}

private fun gameUnlockedLevelKey(userId: String, gameId: String): String {
    return "$userId::$gameId"
}

fun saveLocalUnlockedLevel(
    context: Context,
    userId: String,
    gameId: String,
    completedLevel: Int,
    score: Int
) {
    if (score < 80) return
    val preferences = context.getSharedPreferences(GameUnlockedLevelPref, Context.MODE_PRIVATE)
    val key = gameUnlockedLevelKey(userId, gameId)
    val nextLevel = completedLevel + 1
    val current = preferences.getInt(key, 1)
    if (nextLevel > current) {
        preferences.edit().putInt(key, nextLevel).apply()
    }
}

fun loadLocalUnlockedLevel(context: Context, userId: String, gameId: String): Int {
    return context.getSharedPreferences(GameUnlockedLevelPref, Context.MODE_PRIVATE)
        .getInt(gameUnlockedLevelKey(userId, gameId), 1)
        .coerceAtLeast(1)
}

fun clearClickGameCheckpoint(
    context: Context,
    userId: String,
    gameId: String,
    level: Int
) {
    context.getSharedPreferences(ClickGameCheckpointPref, Context.MODE_PRIVATE)
        .edit()
        .remove(clickGameCheckpointKey(userId, gameId, level))
        .apply()
}

fun saveClickGameCheckpoint(
    context: Context,
    userId: String,
    gameId: String,
    level: Int,
    sessionId: String?,
    score: Int,
    currentIndex: Int,
    maxErrors: Int,
    results: List<AnswerResultDto>,
    questions: JSONArray
) {
    if (questions.length() <= 0) return
    val root = JSONObject().apply {
        put("session_id", sessionId ?: JSONObject.NULL)
        put("score", score.coerceIn(0, 100))
        put("current_index", currentIndex.coerceIn(0, (questions.length() - 1).coerceAtLeast(0)))
        put("max_errors", maxErrors.coerceAtLeast(1))
        put("saved_at_ms", System.currentTimeMillis())
        put("questions", questions)
        put("results", JSONArray().apply {
            results.forEach { result ->
                put(JSONObject().apply {
                    put("question_id", result.questionId)
                    put("answer", result.answer ?: JSONObject.NULL)
                    put("is_correct", result.isCorrect)
                    put("response_time_ms", result.responseTimeMs)
                    put("used_hint", result.usedHint)
                    if (result.cvConfidence == null) {
                        put("cv_confidence", JSONObject.NULL)
                    } else {
                        put("cv_confidence", result.cvConfidence)
                    }
                })
            }
        })
    }
    context.getSharedPreferences(ClickGameCheckpointPref, Context.MODE_PRIVATE)
        .edit()
        .putString(clickGameCheckpointKey(userId, gameId, level), root.toString())
        .apply()
}

fun loadClickGameCheckpointJson(
    context: Context,
    userId: String,
    gameId: String,
    level: Int
): JSONObject? {
    val preferences = context.getSharedPreferences(ClickGameCheckpointPref, Context.MODE_PRIVATE)
    val key = clickGameCheckpointKey(userId, gameId, level)
    val raw = preferences.getString(key, null) ?: return null
    return runCatching {
        val root = JSONObject(raw)
        val savedAtMs = root.optLong("saved_at_ms", 0L)
        val questions = root.optJSONArray("questions")
        val results = root.optJSONArray("results")
        if (
            savedAtMs <= 0L ||
            System.currentTimeMillis() - savedAtMs > ClickGameCheckpointTtlMs ||
            questions == null ||
            questions.length() <= 0 ||
            results == null ||
            results.length() >= questions.length()
        ) {
            preferences.edit().remove(key).apply()
            return null
        }
        root
    }.getOrNull()
}

fun loadClickGameResumePreview(
    context: Context,
    userId: String,
    gameId: String,
    level: Int
): ClickGameResumePreview? {
    val root = loadClickGameCheckpointJson(context, userId, gameId, level) ?: return null
    val total = root.optJSONArray("questions")?.length() ?: return null
    val answered = root.optJSONArray("results")?.length() ?: 0
    return ClickGameResumePreview(
        answeredCount = answered.coerceIn(0, total),
        totalCount = total.coerceAtLeast(1)
    )
}

fun answerResultsFromCheckpoint(root: JSONObject): List<AnswerResultDto> {
    val array = root.optJSONArray("results") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                AnswerResultDto(
                    questionId = item.optString("question_id"),
                    answer = item.optString("answer").takeIf { it.isNotBlank() && it != "null" },
                    isCorrect = item.optBoolean("is_correct"),
                    responseTimeMs = item.optInt("response_time_ms", 0),
                    usedHint = item.optBoolean("used_hint", false),
                    cvConfidence = if (item.isNull("cv_confidence")) null else item.optDouble("cv_confidence").toFloat()
                )
            )
        }
    }
}

@Composable
fun ClickGameResumeDialog(
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
            color = EgDesign.card,
            border = BorderStroke(1.dp, EgDesign.cardBorder),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                EgVectorEmojiIcon("book", size = 28.dp, tint = EgDesign.primary)
                Text(
                    text = "Bé đang chơi dở",
                    color = EgDesign.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Bé đã làm ${answeredCount.coerceAtLeast(0)}/${totalCount.coerceAtLeast(1)} câu. Bé muốn chơi tiếp hay chơi lại từ đầu?",
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
                        shape = RoundedCornerShape(EgDesign.pillRadius),
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Text("Chơi lại", color = EgDesign.blue, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(EgDesign.pillRadius),
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
fun ClickGameExitConfirmDialog(
    onDismiss: () -> Unit,
    onSaveAndExit: () -> Unit,
    onExitWithoutSaving: () -> Unit
) {
    GameExitConfirmDialog(
        icon = "pause",
        title = "Dừng thử thách?",
        message = "Tiến độ hiện tại sẽ được lưu để con chơi tiếp sau.",
        primaryText = "Dừng thử thách",
        primaryColor = EgDesign.danger,
        onPrimary = onSaveAndExit,
        onDismiss = onDismiss
    )
}

@Composable
fun GameExitConfirmDialog(
    icon: String,
    title: String,
    message: String,
    primaryText: String,
    primaryColor: Color,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    destructiveText: String? = null,
    onDestructive: (() -> Unit)? = null
) {
    val isDark = AppSettingsState.activeDarkTheme.value
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 344.dp),
            shape = RoundedCornerShape(28.dp),
            color = EgDesign.card,
            border = BorderStroke(1.dp, EgDesign.cardBorder),
            shadowElevation = if (isDark) 10.dp else 16.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = if (isDark) 0.18f else 0.11f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = if (isDark) 0.24f else 0.18f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EgVectorEmojiIcon(icon, size = 28.dp, tint = primaryColor)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        title,
                        color = EgDesign.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        message,
                        color = EgDesign.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(EgDesign.pillRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = Color.White
                    )
                ) {
                    Text(primaryText, fontWeight = FontWeight.ExtraBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    destructiveText?.let { text ->
                        OutlinedButton(
                            onClick = { onDestructive?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(EgDesign.pillRadius),
                            border = BorderStroke(1.dp, EgDesign.danger.copy(alpha = if (isDark) 0.45f else 0.30f))
                        ) {
                            Text(text, color = EgDesign.danger, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(EgDesign.pillRadius),
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Text("Ở lại", color = EgDesign.blue, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun GameStatChip(text: String) {
    Surface(
        shape = RoundedCornerShape(EgDesign.pillRadius),
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, EgDesign.cardBorder)
    ) {
        Text(
            text = text.replace("Level", "Cấp"),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            color = EgDesign.blue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun GameHeader(
    title: String,
    level: Int,
    currentQuestion: Int,
    totalQuestions: Int,
    score: Int,
    onBack: () -> Unit
) {
    val titleSize = if (title.length > 18) 17.sp else 19.sp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            com.example.appmobile.ui.components.AppBackButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSize,
                    color = EgDesign.textPrimary
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameStatChip("Level $level")
                GameStatChip("Câu $currentQuestion/$totalQuestions")
                GameStatChip("Điểm $score")
            }
        }
    }
}

@Composable
fun GameFeedbackCard(message: String) {
    val normalizedMessage = message.lowercase()
    val isCorrect = listOf("đúng", "chính xác", "phá án đúng", "tuyệt vời")
        .any(normalizedMessage::startsWith)
    val color = if (isCorrect) EgDesign.success else EgDesign.danger
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        shape = RoundedCornerShape(EgDesign.radiusLarge),
        color = EgDesign.cardSoft,
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EgVectorEmojiIcon(if (isCorrect) "check" else "close", size = 20.dp, tint = color)
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = EgDesign.textPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun GameHintCard(
    text: String? = null,
    onClick: (() -> Unit)? = null
) {
    val expanded = !text.isNullOrBlank()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .then(
                if (onClick != null) {
                    Modifier.egTactileClick(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(EgDesign.radiusLarge),
        color = if (expanded) EgDesign.accentSoft else EgDesign.cardSoft,
        border = BorderStroke(
            1.dp,
            if (expanded) EgDesign.primary.copy(alpha = 0.45f) else EgDesign.cardBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = RoundedCornerShape(10.dp),
                color = EgDesign.card,
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EgVectorEmojiIcon("bulb", size = 18.dp, tint = EgDesign.blue)
                }
            }
            Text(
                text = text ?: "Gợi ý",
                modifier = Modifier.weight(1f),
                color = EgDesign.textPrimary,
                fontSize = if (expanded) 14.sp else 15.sp,
                lineHeight = 20.sp,
                fontWeight = if (expanded) FontWeight.Medium else FontWeight.Bold
            )
            if (!expanded) {
                EgVectorEmojiIcon("next", size = 18.dp, tint = EgDesign.textSecondary)
            }
        }
    }
}

@Composable
fun GameQuestionMedia(
    mediaPath: String?,
    fallbackRes: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val model = mediaPath
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(::normalizeGameMediaPath)

    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier
            .clip(RoundedCornerShape(EgDesign.radiusMedium))
            .background(EgDesign.cardSoft),
        placeholder = painterResource(fallbackRes),
        error = painterResource(fallbackRes),
        fallback = painterResource(fallbackRes),
        contentScale = contentScale
    )
}

private fun normalizeGameMediaPath(path: String): String {
    if (path.startsWith("http://") || path.startsWith("https://") ||
        path.startsWith("content://") || path.startsWith("file://")
    ) {
        return path
    }

    val normalized = path.replace('\\', '/').trimStart('/')
    val assetPath = when {
        normalized.startsWith("fe/") -> normalized
        normalized.startsWith("assets/") -> "fe/$normalized"
        else -> normalized
    }
    return "file:///android_asset/$assetPath"
}

@Composable
fun ClickGameInstructionCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconKey: String = "bulb",
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusLarge),
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        border = BorderStroke(1.dp, EgDesign.cardBorder),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = EgDesign.accentSoft,
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EgVectorEmojiIcon(iconKey, size = 20.dp, tint = EgDesign.blue)
                    }
                }
                Text(
                    text = title,
                    color = EgDesign.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = EgDesign.textSecondary,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (content != null) content()
        }
    }
}

@Composable
fun ClickEmotionOptionCard(
    emotionId: String,
    emotionName: String,
    visualState: AnswerVisualState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val isDark = AppSettingsState.activeDarkTheme.value
    val isCorrectState = visualState.borderColor == EgDesign.success
    val isWrongState = visualState.borderColor == EgDesign.danger
    val isSelectedState = visualState.borderColor == EgEmotionCardSelectedBorder
    val neutralState = !isCorrectState && !isWrongState && !isSelectedState
    val accent = when {
        isCorrectState -> EgDesign.success
        isWrongState -> EgDesign.danger
        isSelectedState -> EgDesign.primary
        else -> EgDesign.cardBorder
    }
    val tileColor = when {
        isCorrectState -> EgDesign.success.copy(alpha = if (isDark) 0.20f else 0.13f)
        isWrongState -> EgDesign.danger.copy(alpha = if (isDark) 0.18f else 0.10f)
        isSelectedState -> EgDesign.primary.copy(alpha = if (isDark) 0.18f else 0.10f)
        else -> EgEmotionCardBackground
    }
    val iconBadgeColor = when {
        isCorrectState -> EgDesign.success.copy(alpha = if (isDark) 0.24f else 0.15f)
        isWrongState -> EgDesign.danger.copy(alpha = if (isDark) 0.22f else 0.13f)
        isSelectedState -> EgDesign.primary.copy(alpha = if (isDark) 0.22f else 0.13f)
        else -> Color.White.copy(alpha = if (isDark) 0.08f else 0.78f)
    }
    Card(
        modifier = modifier
            .height(if (compact) 66.dp else 104.dp)
            .egTactileClick(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = tileColor),
        border = BorderStroke(
            width = if (neutralState) 1.dp else 1.5.dp,
            color = if (neutralState) EgEmotionCardBorder else accent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (neutralState) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = if (compact) 6.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 7.dp, Alignment.CenterVertically)
        ) {
            Surface(
                modifier = Modifier.size(if (compact) 30.dp else 44.dp),
                shape = CircleShape,
                color = iconBadgeColor,
                border = BorderStroke(
                    1.dp,
                    if (neutralState) EgEmotionCardBorder.copy(alpha = 0.65f) else accent.copy(alpha = 0.28f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EgEmotionVectorIcon(emotionId, size = if (compact) 24.dp else 34.dp)
                }
            }
            Text(
                emotionName,
                modifier = Modifier.fillMaxWidth(),
                color = EgDesign.textPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 12.sp else 15.sp,
                lineHeight = if (compact) 14.sp else 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class LevelSummaryData(
    val passed: Boolean,
    val score: Int,
    val totalScore: Int,
    val accuracy: Float,
    val correctCount: Int,
    val totalQuestions: Int
)

@Composable
fun GameLevelSummaryCard(
    summaryData: LevelSummaryData?,
    summary: String,
    onBack: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val passed = summaryData?.passed != false
    val accent = if (passed) EgDesign.success else EgDesign.warning
    val isDark = AppSettingsState.activeDarkTheme.value
    val cardColor = if (isDark) EgDesign.card else Color(0xFFF6FBFF)
    val iconSurface = if (passed) {
        accent.copy(alpha = if (isDark) 0.20f else 0.11f)
    } else {
        EgDesign.warning.copy(alpha = if (isDark) 0.20f else 0.12f)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EgDesign.radiusXLarge),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, EgDesign.cardBorder.copy(alpha = if (isDark) 1f else 0.86f)),
        elevation = CardDefaults.cardElevation(if (isDark) 1.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = CircleShape,
                color = iconSurface,
                border = BorderStroke(1.dp, accent.copy(alpha = if (isDark) 0.20f else 0.14f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EgVectorEmojiIcon(
                        value = if (passed) "trophy" else "refresh",
                        size = 30.dp,
                        tint = accent
                    )
                }
            }
            Text(
                text = if (passed) "Hoàn thành cấp độ!" else "Mình thử lại nhé!",
                fontSize = 21.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                color = EgDesign.textPrimary,
                textAlign = TextAlign.Center
            )

            if (summaryData != null) {
                Text(
                    text = if (summaryData.passed) {
                        "Con đã hoàn thành rất tốt. Cấp độ tiếp theo sẽ mở nếu đủ điểm."
                    } else {
                        "Chỉ cần luyện thêm một chút nữa thôi."
                    },
                    color = EgDesign.textSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryMetric("Điểm", "${summaryData.score}/${summaryData.totalScore}", Modifier.weight(1f))
                    SummaryMetric("Đúng", "${summaryData.correctCount}/${summaryData.totalQuestions}", Modifier.weight(1f))
                    SummaryMetric("Chính xác", "${summaryData.accuracy.toInt()}%", Modifier.weight(1f))
                }
            } else {
                Text(
                    text = summary,
                    color = EgDesign.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onReplay,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(EgDesign.pillRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EgDesign.primaryDark,
                    contentColor = Color.White
                )
            ) {
                Text("Chơi lại", fontWeight = FontWeight.ExtraBold)
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(EgDesign.pillRadius),
                border = BorderStroke(1.dp, EgDesign.cardBorder)
            ) {
                Text("Chọn cấp độ", color = EgDesign.blue, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val isDark = AppSettingsState.activeDarkTheme.value
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(EgDesign.radiusMedium),
        color = if (isDark) EgDesign.cardSoft else Color.White.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, EgDesign.cardBorder.copy(alpha = if (isDark) 1f else 0.78f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(value, color = EgDesign.blue, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(label, color = EgDesign.textSecondary, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun EmotionLearningDialog(emotionId: String?, onDismiss: () -> Unit) {
    if (emotionId.isNullOrBlank()) return

    val normalizedId = normalizeEmotionForLearning(emotionId)
    val info = emotionLearningInfo(normalizedId)
    var pageIndex by remember(normalizedId) { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {}
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(EgDesign.radiusXLarge),
                colors = CardDefaults.cardColors(containerColor = EgDesign.card),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EgEmotionVectorIcon(info.id, size = 30.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Ôn lại ${info.title}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = EgDesign.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Surface(
                            modifier = Modifier.size(32.dp).clickable(onClick = onDismiss),
                            shape = CircleShape,
                            color = EgDesign.cardSoft
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                EgVectorEmojiIcon("close", size = 15.dp, tint = EgDesign.textSecondary)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 11f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EgDesign.cardSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pageIndex == 0) {
                            Image(
                                painter = painterResource(id = emotionDrawableResource(normalizedId)),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Image(
                                painter = painterResource(id = emotionEnsembleResource(normalizedId)),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(4.dp)
                                .size(34.dp)
                                .clickable { pageIndex = if (pageIndex == 0) 1 else 0 },
                            shape = CircleShape,
                            color = EgDesign.card.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, EgDesign.cardBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("‹", color = EgDesign.blue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(4.dp)
                                .size(34.dp)
                                .clickable { pageIndex = if (pageIndex == 0) 1 else 0 },
                            shape = CircleShape,
                            color = EgDesign.card.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, EgDesign.cardBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("›", color = EgDesign.blue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = if (pageIndex == 0) EgDesign.blue else EgDesign.cardBorder
                        ) {}
                        Spacer(Modifier.size(6.dp))
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = if (pageIndex == 1) EgDesign.blue else EgDesign.cardBorder
                        ) {}
                    }

                    Text(info.description, color = EgDesign.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = EgDesign.cardSoft,
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EgVectorEmojiIcon("puzzle", size = 18.dp)
                                Text("Ví dụ tình huống", fontWeight = FontWeight.Bold, color = EgDesign.textPrimary, fontSize = 14.sp)
                            }
                            Text(info.situation, color = EgDesign.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = EgDesign.cardSoft,
                        border = BorderStroke(1.dp, EgDesign.cardBorder)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EgVectorEmojiIcon("eye", size = 18.dp)
                                Text("Dấu hiệu nhận biết", fontWeight = FontWeight.Bold, color = EgDesign.textPrimary, fontSize = 14.sp)
                            }
                            info.cues.forEach { cue ->
                                Text("• $cue", color = EgDesign.textSecondary, fontSize = 13.sp)
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("Đã hiểu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

fun emotionDrawableResource(emotionId: String): Int = when (normalizeEmotionForLearning(emotionId)) {
    "happy" -> R.drawable.happy_1
    "sad" -> R.drawable.sad_1
    "angry" -> R.drawable.angry_1
    "fear" -> R.drawable.fear_1
    "surprise" -> R.drawable.surprise_1
    "disgust" -> R.drawable.disgust_1
    else -> R.drawable.recognize_emotion
}

fun emotionEnsembleResource(emotionId: String): Int = when (normalizeEmotionForLearning(emotionId)) {
    "happy" -> R.drawable.face_ensemble_happy
    "sad" -> R.drawable.face_ensemble_sad
    "angry" -> R.drawable.face_ensemble_angry
    "fear" -> R.drawable.face_ensemble_fear
    "surprise" -> R.drawable.face_ensemble_surprise
    "disgust" -> R.drawable.face_ensemble_disgust
    else -> R.drawable.recognize_emotion
}

fun emotionLearningInfo(rawEmotionId: String): EmotionLearningInfo {
    val normalized = normalizeEmotionForLearning(rawEmotionId)
    val catalog = GameUiCatalog.emotionById(normalized)
    val fallbackTitle = catalog?.name ?: rawEmotionId
    val fallbackEmoji = normalized
    val fallbackDescription = catalog?.description ?: "Hãy quan sát khuôn mặt, giọng nói và tình huống để đoán cảm xúc."

    return when (normalized) {
        "happy" -> EmotionLearningInfo(
            id = normalized,
            title = fallbackTitle,
            emoji = fallbackEmoji,
            description = fallbackDescription,
            situation = "Khi được khen, được tặng quà hoặc chơi cùng bạn, bé thường thấy vui.",
            cues = listOf("Miệng cười", "Mắt sáng", "Giọng nói nhẹ và hào hứng")
        )
        "sad" -> EmotionLearningInfo(
            id = normalized,
            title = fallbackTitle,
            emoji = fallbackEmoji,
            description = fallbackDescription,
            situation = "Khi mất đồ chơi hoặc phải rời xa điều mình thích, bé có thể thấy buồn.",
            cues = listOf("Mắt nhìn xuống", "Miệng trầm", "Có thể khóc hoặc nói nhỏ")
        )
        "angry" -> EmotionLearningInfo(
            id = normalized,
            title = fallbackTitle,
            emoji = fallbackEmoji,
            description = fallbackDescription,
            situation = "Khi bị giành đồ hoặc bị làm đau, bé có thể thấy tức giận.",
            cues = listOf("Lông mày cau lại", "Mặt căng", "Giọng nói to hơn")
        )
        "fear" -> EmotionLearningInfo(
            id = normalized,
            title = fallbackTitle,
            emoji = fallbackEmoji,
            description = fallbackDescription,
            situation = "Khi gặp tiếng động lớn, bóng tối hoặc điều chưa quen, bé có thể thấy sợ.",
            cues = listOf("Mắt mở to", "Người lùi lại", "Muốn tìm người lớn")
        )
        "surprise" -> EmotionLearningInfo(
            id = normalized,
            title = fallbackTitle,
            emoji = fallbackEmoji,
            description = fallbackDescription,
            situation = "Khi có điều bất ngờ xảy ra, bé có thể thấy ngạc nhiên.",
            cues = listOf("Mắt mở to", "Miệng chữ O", "Dừng lại để nhìn kỹ")
        )
        "disgust" -> EmotionLearningInfo(
            id = normalized,
            title = fallbackTitle,
            emoji = fallbackEmoji,
            description = fallbackDescription,
            situation = "Khi ngửi mùi rác hoặc thấy món ăn không thích, bé có thể thấy ghê tởm.",
            cues = listOf("Nhăn mũi", "Quay mặt đi", "Muốn tránh xa")
        )
        else -> EmotionLearningInfo(
            id = normalized,
            title = fallbackTitle,
            emoji = fallbackEmoji,
            description = fallbackDescription,
            situation = "Hãy nối cảm xúc với tình huống đang xảy ra để chọn câu trả lời phù hợp.",
            cues = listOf("Nhìn mắt", "Nhìn miệng", "Đọc kỹ tình huống")
        )
    }
}

fun optionEmotionIdsFromBackend(options: List<GameContentOptionDto>?, correctEmotion: String): List<String> {
    val correct = normalizeEmotionForLearning(correctEmotion)
    val backendOptions = options.orEmpty()
        .mapNotNull { option -> option.emotion ?: option.answerText }
        .map { normalizeEmotionForLearning(it) }
        .filter { GameUiCatalog.emotionById(it) != null }
        .distinct()
    return (listOf(correct) + backendOptions)
        .filter { GameUiCatalog.emotionById(it) != null }
        .distinct()
        .ifEmpty { GameUiCatalog.emotions.map { it.id } }
}

fun answerVisualState(
    optionId: String,
    correctEmotion: String,
    selectedEmotionId: String?,
    hasFeedback: Boolean
): AnswerVisualState {
    val isSelected = selectedEmotionId == optionId
    val isCorrect = normalizeEmotionForLearning(correctEmotion) == optionId
    val isDark = AppSettingsState.activeDarkTheme.value
    return when {
        hasFeedback && isCorrect -> AnswerVisualState(
            borderColor = EgDesign.success,
            containerColor = EgDesign.success.copy(alpha = if (isDark) 0.18f else 0.12f)
        )
        hasFeedback && isSelected -> AnswerVisualState(
            borderColor = EgDesign.danger,
            containerColor = EgDesign.danger.copy(alpha = if (isDark) 0.18f else 0.12f)
        )
        isSelected -> AnswerVisualState(
            borderColor = EgEmotionCardSelectedBorder,
            containerColor = EgEmotionCardSelectedBackground
        )
        else -> AnswerVisualState(
            borderColor = EgEmotionCardBorder,
            containerColor = EgEmotionCardBackground
        )
    }
}

fun normalizeEmotionForLearning(value: String): String {
    val lower = value.trim().lowercase()
    return when {
        lower.contains("happy") || lower.contains("vui") -> "happy"
        lower.contains("sad") || lower.contains("buồn") || lower.contains("buon") -> "sad"
        lower.contains("angry") || lower.contains("tức") || lower.contains("tuc") -> "angry"
        lower.contains("fear") || lower.contains("sợ") || lower.contains("so") -> "fear"
        lower.contains("surprise") || lower.contains("ngạc") || lower.contains("ngac") -> "surprise"
        lower.contains("disgust") || lower.contains("ghê") || lower.contains("ghe") -> "disgust"
        else -> lower
    }
}
