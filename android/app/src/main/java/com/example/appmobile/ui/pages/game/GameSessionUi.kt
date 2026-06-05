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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
                    .padding(start = 52.dp, end = 18.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    val isCorrect = message.startsWith("Đúng")
        || message.startsWith("Phá án đúng")
        || message.startsWith("Chính xác")
    val color = if (isCorrect) EgDesign.success else EgDesign.warning
    Surface(
        shape = RoundedCornerShape(EgDesign.radiusLarge),
        color = color.copy(alpha = if (AppSettingsState.activeDarkTheme.value) 0.18f else 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EgVectorEmojiIcon(if (isCorrect) "check" else "bulb", size = 18.dp, tint = color)
            Text(
                text = message,
                color = EgDesign.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
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
                    modifier = Modifier.size(30.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = EgDesign.accentSoft,
                    border = BorderStroke(1.dp, EgDesign.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EgVectorEmojiIcon(iconKey, size = 17.dp, tint = EgDesign.blue)
                    }
                }
                Text(
                    text = title,
                    color = EgDesign.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = EgDesign.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
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
    val neutralState = visualState.borderColor == EgEmotionCardBorder
    Surface(
        modifier = modifier
            .egTactileClick(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(EgDesign.radiusMedium),
        color = visualState.containerColor,
        border = BorderStroke(
            width = if (neutralState) 1.dp else 2.dp,
            color = visualState.borderColor
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (neutralState) 0.dp else 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 9.dp else 12.dp,
                    vertical = if (compact) 8.dp else 12.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(if (compact) 30.dp else 40.dp),
                contentAlignment = Alignment.Center
            ) {
                EgEmotionVectorIcon(emotionId, size = if (compact) 22.dp else 30.dp)
            }
            Text(
                emotionName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 32.dp else 44.dp),
                color = EgDesign.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 13.sp else 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
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
    onReplay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (summaryData?.passed == true) "Chúc mừng!" else "Kết thúc level",
                fontWeight = FontWeight.Bold,
                color = if (summaryData?.passed == true) Color(0xFF2E7D32) else EgDesign.textPrimary
            )

            if (summaryData != null) {
                Text(
                    text = if (summaryData.passed) "Bạn đã vượt qua level!" else "Bạn chưa qua level, hãy cố gắng hơn nhé!",
                    color = if (summaryData.passed) Color(0xFF2E7D32) else Color(0xFFE65100),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                SummaryRow("Điểm số", "${summaryData.score}/${summaryData.totalScore}")
                SummaryRow("Chính xác", "${summaryData.correctCount}/${summaryData.totalQuestions} câu")
                SummaryRow("Tỉ lệ đúng", "${summaryData.accuracy.toInt()}%")
            } else {
                Text(summary, color = EgDesign.textSecondary)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onReplay, modifier = Modifier.weight(1f)) {
                    Text("Chơi lại")
                }
                Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Chọn level")
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = EgDesign.textSecondary, fontWeight = FontWeight.Medium)
        Text(value, color = EgDesign.textPrimary, fontWeight = FontWeight.Bold)
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
