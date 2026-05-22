package com.example.appmobile.ui.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun GameLevelSummaryCard(summary: String, onBack: () -> Unit, onReplay: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = EgDesign.card),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kết thúc level", fontWeight = FontWeight.Bold, color = EgDesign.textPrimary)
            Text(summary, color = EgDesign.textSecondary)
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
fun EmotionLearningDialog(emotionId: String?, onDismiss: () -> Unit) {
    if (emotionId.isNullOrBlank()) return

    val info = emotionLearningInfo(emotionId)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Đã hiểu")
            }
        },
        title = {
            Text("Ôn lại ${info.title}", color = EgDesign.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(info.emoji, fontSize = 34.sp)
                    Text(info.description, color = EgDesign.textSecondary)
                }
                Surface(shape = MaterialTheme.shapes.large, color = EgDesign.cardSoft) {
                    Text(
                        info.situation,
                        modifier = Modifier.padding(12.dp),
                        color = EgDesign.textPrimary
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dấu hiệu nhận biết", color = EgDesign.textPrimary, fontWeight = FontWeight.SemiBold)
                    info.cues.forEach { cue ->
                        Text("• $cue", color = EgDesign.textSecondary)
                    }
                }
            }
        },
        containerColor = EgDesign.card
    )
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
