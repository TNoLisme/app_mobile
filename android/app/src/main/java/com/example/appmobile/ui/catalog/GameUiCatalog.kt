package com.example.appmobile.ui.catalog

import com.example.appmobile.R

data class GameUiItem(
    val id: String,
    val title: String,
    val description: String,
    val type: String,
    val imageRes: Int,
    val maxLevel: Int
)

data class LevelUiItem(
    val id: Int,
    val name: String,
    val description: String,
    val colorHex: Long
)

data class EmotionUiItem(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String
)

data class CvPromptUiItem(
    val questionText: String,
    val correctAnswer: String
)

object GameUiCatalog {
    const val GAME_RECOGNIZE_EMOTION = "3bcb2108-721c-4a15-a585-31f3084ed000"
    const val GAME_FACE_ASSEMBLY = "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051"
    const val GAME_EMOTION_MATCH = "08bbffbf-d147-4556-bccb-b7621cafbf15"
    const val GAME_DETECTIVE = "aacaf79e-e15e-42a9-a3d1-a522720d919b"
    const val GAME_CV_STORY = "e05909f3-3dee-42a6-9a75-fd985b1bdf47"
    const val GAME_CV_REQUEST = "61f5e09e-eefa-44c1-86e1-87dfceac3b8e"

    val games = listOf(
        GameUiItem(
            GAME_RECOGNIZE_EMOTION,
            "Chiếc hộp cảm xúc",
            "Chọn cảm xúc đúng qua hình ảnh",
            "click_game",
            R.drawable.recognize_emotion,
            8
        ),
        GameUiItem(
            GAME_FACE_ASSEMBLY,
            "Xưởng lắp ghép cảm xúc",
            "Ghép các bộ phận khuôn mặt",
            "click_game",
            R.drawable.game_click_2,
            8
        ),
        GameUiItem(
            GAME_EMOTION_MATCH,
            "Cảm xúc đúng chỗ",
            "Đặt cảm xúc vào đúng ngữ cảnh",
            "click_game",
            R.drawable.game_click_3,
            8
        ),
        GameUiItem(
            GAME_DETECTIVE,
            "Thám tử cảm xúc",
            "Tìm cảm xúc ẩn giấu trong tình huống",
            "click_game",
            R.drawable.game_click_4,
            8
        ),
        GameUiItem(
            GAME_CV_STORY,
            "Câu chuyện trên khuôn mặt",
            "Bắt chước biểu cảm của nhân vật",
            "camera_game",
            R.drawable.game_cv,
            5
        ),
        GameUiItem(
            GAME_CV_REQUEST,
            "Thử thách cảm xúc",
            "Thể hiện biểu cảm theo yêu cầu",
            "camera_game",
            R.drawable.game_cv_2,
            6
        )
    )

    val emotions = listOf(
        EmotionUiItem("happy", "Vui", "😊", "Miệng cười, mắt sáng và có thể cười thành tiếng."),
        EmotionUiItem("sad", "Buồn", "😢", "Mắt nhìn xuống, miệng trầm và giọng nói nhỏ hơn."),
        EmotionUiItem("angry", "Tức giận", "😡", "Lông mày chau lại, mặt nghiêm và có thể nói to hơn."),
        EmotionUiItem("fear", "Sợ hãi", "😨", "Mắt mở to, có thể lùi lại vì cần cảm thấy an toàn."),
        EmotionUiItem("surprise", "Ngạc nhiên", "😲", "Mắt mở to, miệng chữ O khi thấy điều bất ngờ."),
        EmotionUiItem("disgust", "Ghê tởm", "🤢", "Mũi nhăn lại, đầu quay đi khi gặp mùi vị khó chịu.")
    )

    val cvStoryPrompt = CvPromptUiItem(
        questionText = "Nhân vật vừa nhận được lời khen. Hãy thể hiện khuôn mặt vui.",
        correctAnswer = "happy"
    )

    val cvRequestPrompt = CvPromptUiItem(
        questionText = "Hãy cười thật tươi trong 3 giây.",
        correctAnswer = "happy"
    )

    fun gamesByType(type: String): List<GameUiItem> = games.filter { it.type == type }

    fun gameById(id: String): GameUiItem? = games.firstOrNull { it.id == id }

    fun isClickGame(id: String): Boolean = gameById(id)?.type == "click_game"

    fun emotionById(id: String): EmotionUiItem? = emotions.firstOrNull { it.id == id }

    fun gameFromBackend(id: String, title: String, type: String, maxLevel: Int): GameUiItem {
        val fallback = gameById(id)
        return GameUiItem(
            id = id,
            title = title.ifBlank { fallback?.title ?: "Trò chơi" },
            description = fallback?.description ?: "",
            type = type.ifBlank { fallback?.type ?: "click_game" },
            imageRes = fallback?.imageRes ?: R.drawable.logo_emo,
            maxLevel = maxLevel.takeIf { it > 0 } ?: fallback?.maxLevel ?: 1
        )
    }

    fun emotionFromBackend(id: String, title: String, description: String): EmotionUiItem {
        val fallback = emotionById(id)
        return EmotionUiItem(
            id = id,
            name = title.ifBlank { fallback?.name ?: id },
            emoji = fallback?.emoji ?: "",
            description = description.ifBlank { fallback?.description ?: "" }
        )
    }

    fun levelsForGame(gameId: String): List<LevelUiItem> {
        if (gameId == GAME_CV_STORY) return cvStoryLevels()
        val maxLevel = games.firstOrNull { it.id == gameId }?.maxLevel ?: return emptyList()
        return levelsForMaxLevel(maxLevel)
    }

    private fun cvStoryLevels(): List<LevelUiItem> {
        return (1..5).map { level ->
            LevelUiItem(
                id = level,
                name = "Cấp độ $level",
                description = "5 câu hỏi",
                colorHex = when (level) {
                    1 -> 0xFF81C784
                    2 -> 0xFFFFB74D
                    3 -> 0xFFE57373
                    4 -> 0xFF64B5F6
                    else -> 0xFF9575CD
                }
            )
        }
    }

    fun levelsForMaxLevel(maxLevel: Int): List<LevelUiItem> {
        if (maxLevel <= 0) return emptyList()
        return (1..maxLevel).map { level ->
            LevelUiItem(
                id = level,
                name = when (level) {
                    1 -> "Dễ"
                    2 -> "Trung bình"
                    3 -> "Khó"
                    else -> "Cấp độ $level"
                },
                description = "5 cau hoi",
                colorHex = when (level) {
                    1 -> 0xFF81C784
                    2 -> 0xFFFFB74D
                    3 -> 0xFFE57373
                    else -> 0xFF64B5F6
                }
            )
        }
    }
}
