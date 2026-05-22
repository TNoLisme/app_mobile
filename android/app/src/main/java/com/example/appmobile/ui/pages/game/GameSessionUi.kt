package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appmobile.R
import com.example.appmobile.data.remote.dto.GameContentOptionDto
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.EgDesign
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
    Surface(shape = MaterialTheme.shapes.large, color = EgDesign.cardSoft) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = EgDesign.blue,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun GameFeedbackCard(message: String) {
    val isCorrect = message.startsWith("Đúng")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = EgDesign.cardSoft
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = EgDesign.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
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
                shape = RoundedCornerShape(28.dp),
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
                        Text(info.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Ôn lại ${info.title}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = EgDesign.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Surface(
                            modifier = Modifier.size(32.dp).clickable(onClick = onDismiss),
                            shape = CircleShape,
                            color = EgDesign.cardSoft
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✕", color = EgDesign.textSecondary, fontSize = 14.sp)
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
                            Text("🧩 Ví dụ tình huống", fontWeight = FontWeight.Bold, color = EgDesign.textPrimary, fontSize = 14.sp)
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
                            Text("👀 Dấu hiệu nhận biết", fontWeight = FontWeight.Bold, color = EgDesign.textPrimary, fontSize = 14.sp)
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
    val fallbackEmoji = catalog?.emoji ?: "🙂"
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
            borderColor = Color(0xFF2E7D32),
            containerColor = if (isDark) Color(0xFF153E2A) else Color(0xFFE8F5E9)
        )
        hasFeedback && isSelected -> AnswerVisualState(
            borderColor = Color(0xFFD32F2F),
            containerColor = if (isDark) Color(0xFF51222B) else Color(0xFFFFEBEE)
        )
        isSelected -> AnswerVisualState(
            borderColor = Color(0xFF3B82F6),
            containerColor = EgDesign.cardSoft
        )
        else -> AnswerVisualState(
            borderColor = EgDesign.cardBorder,
            containerColor = EgDesign.card
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
