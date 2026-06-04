package com.example.appmobile.ui.catalog

import com.example.appmobile.R
import java.text.Normalizer
import java.util.Locale

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
    const val GAME_RECOGNIZE_EMOTION = "3bcb2108-721c-4a15-a585-31f084ed0000"
    const val GAME_FACE_ASSEMBLY = "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051"
    const val GAME_EMOTION_MATCH = "aacaf79e-e15e-42a9-a3d1-a522720d919b"
    const val GAME_DETECTIVE = "08bbffbf-d147-4556-bccb-b7621cafbf15"
    const val GAME_CV_STORY = "e05909f3-3dee-42a6-9a75-fd985b1bdf47"
    const val GAME_CV_REQUEST = "61f5e09e-eefa-44c1-86e1-87dfceac3b8e"

    private const val LEGACY_RECOGNIZE_EMOTION = "3bcb2108-721c-4a15-a585-31f3084ed000"

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
            "Câu chuyện khuôn mặt",
            "Đọc tình huống và thể hiện cảm xúc phù hợp.",
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
        EmotionUiItem("happy", "Vui", "happy", "Miệng cười, mắt sáng và có thể cười thành tiếng."),
        EmotionUiItem("sad", "Buồn", "sad", "Mắt nhìn xuống, miệng trầm và giọng nói nhỏ hơn."),
        EmotionUiItem("angry", "Tức giận", "angry", "Lông mày chau lại, mặt nghiêm và có thể nói to hơn."),
        EmotionUiItem("fear", "Sợ hãi", "fear", "Mắt mở to, có thể lùi lại vì cần cảm thấy an toàn."),
        EmotionUiItem("surprise", "Ngạc nhiên", "surprise", "Mắt mở to, miệng chữ O khi thấy điều bất ngờ."),
        EmotionUiItem("disgust", "Ghê tởm", "disgust", "Mũi nhăn lại, đầu quay đi khi gặp mùi vị khó chịu.")
    )

    val cvStoryPrompt = CvPromptUiItem(
        questionText = "Bạn nhỏ vừa nhận được lời khen từ cô giáo.",
        correctAnswer = "happy"
    )

    val cvRequestPrompt = CvPromptUiItem(
        questionText = "Hãy cười thật tươi trong 5 giây.",
        correctAnswer = "happy"
    )

    fun gamesByType(type: String): List<GameUiItem> = games.filter { it.type == type }

    fun gameById(id: String): GameUiItem? {
        val canonicalId = when (id.lowercase(Locale.ROOT)) {
            LEGACY_RECOGNIZE_EMOTION -> GAME_RECOGNIZE_EMOTION
            else -> id
        }
        return gameByCanonicalId(canonicalId)
    }

    fun isClickGame(id: String): Boolean = gameById(id)?.type == "click_game"

    fun emotionById(id: String): EmotionUiItem? = emotions.firstOrNull { it.id == id }

    fun gameFromBackend(id: String, title: String, type: String, maxLevel: Int): GameUiItem {
        val fallback = gameByBackendTitle(title) ?: gameById(id)
        val normalizedId = fallback?.id ?: id.lowercase(Locale.ROOT)
        return GameUiItem(
            id = normalizedId,
            title = fallback?.title ?: title.ifBlank { "Trò chơi" },
            description = fallback?.description ?: "",
            type = fallback?.type ?: type.ifBlank { "click_game" },
            imageRes = fallback?.imageRes ?: R.drawable.logo_emo,
            maxLevel = maxLevel.takeIf { it > 0 } ?: fallback?.maxLevel ?: 1
        )
    }

    fun normalizeGameList(items: List<GameUiItem>): List<GameUiItem> {
        val order = games.mapIndexed { index, game -> game.id to index }.toMap()
        return items
            .distinctBy { it.id.lowercase(Locale.ROOT) }
            .sortedWith(
                compareBy<GameUiItem> { order[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.title }
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
        val game = gameById(gameId) ?: return emptyList()
        if (game.id.equals(GAME_CV_STORY, ignoreCase = true)) return cvStoryLevels()
        val maxLevel = game.maxLevel
        return levelsForMaxLevel(maxLevel)
    }

    private fun gameByCanonicalId(id: String): GameUiItem? {
        return games.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    private fun gameByBackendTitle(title: String): GameUiItem? {
        val key = normalizedSearchKey(title)
        return when {
            "chiec" in key || "hop cam xuc" in key || "nhan dien" in key -> gameByCanonicalId(GAME_RECOGNIZE_EMOTION)
            "xuong" in key || "lap ghep" in key -> gameByCanonicalId(GAME_FACE_ASSEMBLY)
            "dung cho" in key -> gameByCanonicalId(GAME_EMOTION_MATCH)
            "tham tu" in key -> gameByCanonicalId(GAME_DETECTIVE)
            "cau chuyen" in key || "khuon mat" in key -> gameByCanonicalId(GAME_CV_STORY)
            "thu thach" in key || "bieu cam" in key -> gameByCanonicalId(GAME_CV_REQUEST)
            else -> null
        }
    }

    private fun normalizedSearchKey(value: String): String {
        val source = if (value.contains("Ã") || value.contains("Ä") || value.contains("áº")) {
            runCatching { String(value.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }
                .getOrDefault(value)
        } else {
            value
        }
        return Normalizer
            .normalize(source, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
    }

    private fun cvStoryLevels(): List<LevelUiItem> {
        return (1..5).map { level ->
            LevelUiItem(
                id = level,
                name = "Cấp độ $level",
                description = "5 màn chơi",
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
                name = "Cấp độ $level",
                description = "",
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
