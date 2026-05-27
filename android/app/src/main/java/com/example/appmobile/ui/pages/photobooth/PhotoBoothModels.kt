package com.example.appmobile.ui.pages.photobooth

import androidx.compose.ui.graphics.Color

data class PhotoBoothEmotion(
    val id: String,
    val name: String
)

data class PhotoBoothFrameTemplate(
    val id: String,
    val name: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val textColor: Color
)

data class PhotoBoothShot(
    val emotionId: String,
    val photoUri: String,
    val accepted: Boolean = true
)

enum class PhotoBoothLayoutType {
    VerticalStrip,
    Grid2x2
}

enum class PhotoBoothPhase {
    Intro,
    PickingEmotions,
    PickingFrame,
    Preparing,
    Capturing,
    ReviewingShot,
    Composing,
    PreviewFinal,
    Saving,
    PermissionDenied,
    Error
}

data class PhotoBoothUiState(
    val phase: PhotoBoothPhase = PhotoBoothPhase.Intro,
    val selectedEmotionIds: List<String> = emptyList(),
    val selectedFrameId: String = PhotoBoothCatalog.frames.first().id,
    val selectedLayout: PhotoBoothLayoutType = PhotoBoothLayoutType.VerticalStrip,
    val currentStepIndex: Int = 0,
    val shots: List<PhotoBoothShot> = emptyList(),
    val currentPreviewUri: String? = null,
    val composedPhotoUri: String? = null,
    val countdown: Int? = null,
    val validationMessage: String? = null,
    val friendlyMessage: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)

sealed interface PhotoBoothEvent {
    data class CapturePhoto(val outputPath: String) : PhotoBoothEvent
}

object PhotoBoothCatalog {
    val emotions = listOf(
        PhotoBoothEmotion("happy", "Vui vẻ"),
        PhotoBoothEmotion("sad", "Buồn bã"),
        PhotoBoothEmotion("angry", "Tức giận"),
        PhotoBoothEmotion("fear", "Sợ hãi"),
        PhotoBoothEmotion("surprise", "Ngạc nhiên"),
        PhotoBoothEmotion("disgust", "Ghê tởm")
    )

    val frames = listOf(
        PhotoBoothFrameTemplate(
            id = "garden_blue",
            name = "Khu vườn xanh",
            description = "Nền xanh pastel, hoa lá nhẹ ở các góc.",
            primaryColor = Color(0xFFE7F7FF),
            secondaryColor = Color(0xFFBDEBDA),
            textColor = Color(0xFF0B3A6E)
        ),
        PhotoBoothFrameTemplate(
            id = "rainbow_feelings",
            name = "Cầu vồng cảm xúc",
            description = "Mây mềm, cầu vồng nhỏ và màu sáng vui mắt.",
            primaryColor = Color(0xFFEAF7FF),
            secondaryColor = Color(0xFFFFE28A),
            textColor = Color(0xFF0B3A6E)
        ),
        PhotoBoothFrameTemplate(
            id = "emotion_stickers",
            name = "Sticker cảm xúc",
            description = "Các biểu tượng cảm xúc nhỏ quanh viền ảnh.",
            primaryColor = Color(0xFFDFF0FF),
            secondaryColor = Color(0xFFFFD6E5),
            textColor = Color(0xFF123B61)
        ),
        PhotoBoothFrameTemplate(
            id = "flower_booth",
            name = "Vườn hoa cảm xúc",
            description = "Hoa nhỏ và lá xanh theo tinh thần EmoGarden.",
            primaryColor = Color(0xFFE9FAEF),
            secondaryColor = Color(0xFFC9E8FF),
            textColor = Color(0xFF0E4A3E)
        ),
        PhotoBoothFrameTemplate(
            id = "starry_night",
            name = "Đêm sao dịu dàng",
            description = "Nền xanh navy, sao nhỏ và viền xanh sáng.",
            primaryColor = Color(0xFF172A42),
            secondaryColor = Color(0xFF7CC8FF),
            textColor = Color(0xFFF4FAFF)
        ),
        PhotoBoothFrameTemplate(
            id = "cute_minimal",
            name = "Đơn giản dễ thương",
            description = "Nền sáng, ít họa tiết, nhìn gọn và sạch.",
            primaryColor = Color(0xFFFFFFFF),
            secondaryColor = Color(0xFFDCEEFF),
            textColor = Color(0xFF0B3A6E)
        )
    )

    fun emotionName(id: String): String {
        return emotions.firstOrNull { it.id == id }?.name ?: "Cảm xúc"
    }

    fun frame(id: String): PhotoBoothFrameTemplate {
        return frames.firstOrNull { it.id == id } ?: frames.first()
    }

    fun layoutFor(count: Int): PhotoBoothLayoutType {
        return if (count >= 4) PhotoBoothLayoutType.Grid2x2 else PhotoBoothLayoutType.VerticalStrip
    }
}
