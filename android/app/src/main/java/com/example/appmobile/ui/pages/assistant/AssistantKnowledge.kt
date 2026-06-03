package com.example.appmobile.ui.pages.assistant

import com.example.appmobile.data.garden.GardenHomeSummary
import java.text.Normalizer
import java.util.Locale

enum class ChatAudience {
    CHILD,
    PARENT,
    UNKNOWN
}

enum class ChatActionType {
    OPEN_LEARNING,
    OPEN_GAME,
    OPEN_REPORT,
    OPEN_GARDEN,
    OPEN_PHOTOBOOTH,
    OPEN_SETTINGS,
    OPEN_PARENT_AREA,
    OPEN_PARENT_EMAIL_SETTINGS,
    OPEN_PRIVACY_SETTINGS,
    OPEN_EMOTION_LESSON,
    START_EMOTION_CHALLENGE,
    ASK_CONFIRM_SEND_REPORT,
    ASK_CONFIRM_SAVE_PHOTO
}

data class ChatAction(
    val type: ChatActionType,
    val label: String,
    val target: String? = null
)

data class AppChatContext(
    val currentRoute: String,
    val currentScreenName: String,
    val currentEmotionId: String? = null,
    val currentGameId: String? = null,
    val level: Int? = null,
    val audience: ChatAudience = ChatAudience.CHILD,
    val isParentArea: Boolean = false,
    val parentEmailExists: Boolean? = null,
    val reportSummaryText: String? = null,
    val gardenSummary: GardenHomeSummary? = null,
    val weakEmotionIds: List<String> = emptyList(),
    val recommendedEmotionId: String? = null
)

data class AppFeature(
    val id: String,
    val name: String,
    val descriptionForChild: String,
    val descriptionForParent: String,
    val route: String?,
    val exampleQuestions: List<String>,
    val safeActions: List<ChatAction>
)

data class AssistantReply(
    val text: String,
    val actions: List<ChatAction> = emptyList()
)

object AppFeatureRegistry {
    val features = listOf(
        AppFeature(
            id = "home",
            name = "Trang chủ",
            descriptionForChild = "Nơi gợi ý hôm nay con nên học gì, chơi gì và xem nhanh tiến độ.",
            descriptionForParent = "Trang tổng quan các hoạt động học cảm xúc, báo cáo, vườn và photobooth.",
            route = "home",
            exampleQuestions = listOf("Hôm nay con nên học gì?", "Báo cáo của bé ở đâu?"),
            safeActions = listOf(
                ChatAction(ChatActionType.OPEN_LEARNING, "Mở Học"),
                ChatAction(ChatActionType.OPEN_GAME, "Mở Chơi game"),
                ChatAction(ChatActionType.OPEN_REPORT, "Mở Báo cáo"),
                ChatAction(ChatActionType.OPEN_GARDEN, "Mở Vườn")
            )
        ),
        AppFeature(
            id = "learning",
            name = "Học cảm xúc",
            descriptionForChild = "Con xem mô tả, ví dụ và dấu hiệu khuôn mặt của từng cảm xúc.",
            descriptionForParent = "Màn học giúp bé nhận diện từng cảm xúc trước khi luyện bằng game.",
            route = "learn",
            exampleQuestions = listOf("Cảm xúc này là gì?", "Cho con ví dụ nhé"),
            safeActions = listOf(ChatAction(ChatActionType.OPEN_LEARNING, "Mở Học"))
        ),
        AppFeature(
            id = "game",
            name = "Chơi game",
            descriptionForChild = "Con luyện nhận diện và thể hiện cảm xúc qua các trò chơi.",
            descriptionForParent = "Khu vực trò chơi giúp bé thực hành cảm xúc theo cấp độ.",
            route = "select_game/all",
            exampleQuestions = listOf("Cách chơi game này thế nào?", "Con cần làm gì để đúng?"),
            safeActions = listOf(ChatAction(ChatActionType.OPEN_GAME, "Mở Chơi game"))
        ),
        AppFeature(
            id = "camera_game",
            name = "Game camera",
            descriptionForChild = "Con làm khuôn mặt cảm xúc trước camera để app nhận diện.",
            descriptionForParent = "Camera chỉ dùng để nhận diện biểu cảm trong trò chơi, không tự lưu video.",
            route = null,
            exampleQuestions = listOf("Camera không thấy mặt con", "Làm sao để biểu hiện đúng?"),
            safeActions = listOf(ChatAction(ChatActionType.OPEN_PRIVACY_SETTINGS, "Xem quyền camera"))
        ),
        AppFeature(
            id = "report",
            name = "Báo cáo của bé",
            descriptionForChild = "Con xem thành tích tuần này và gửi báo cáo cho bố mẹ.",
            descriptionForParent = "Báo cáo PDF/email giúp phụ huynh xem điểm trung bình, cảm xúc cần luyện và gợi ý.",
            route = "report",
            exampleQuestions = listOf("Điểm trung bình nghĩa là gì?", "Gửi báo cáo cho bố mẹ thế nào?"),
            safeActions = listOf(
                ChatAction(ChatActionType.OPEN_REPORT, "Mở Báo cáo"),
                ChatAction(ChatActionType.ASK_CONFIRM_SEND_REPORT, "Chuẩn bị gửi báo cáo")
            )
        ),
        AppFeature(
            id = "photobooth",
            name = "EmoGarden Photobooth",
            descriptionForChild = "Con chọn nhiều cảm xúc, chụp từng khuôn mặt rồi app ghép thành một ảnh.",
            descriptionForParent = "Photobooth là trải nghiệm sáng tạo; ảnh chỉ lưu hoặc chia sẻ khi người dùng xác nhận.",
            route = "photobooth",
            exampleQuestions = listOf("Photobooth là gì?", "Ảnh ghép lưu ở đâu?"),
            safeActions = listOf(ChatAction(ChatActionType.OPEN_PHOTOBOOTH, "Mở Photobooth"))
        ),
        AppFeature(
            id = "garden",
            name = "Vườn cảm xúc",
            descriptionForChild = "Con làm nhiệm vụ để nhận nước, ánh nắng và chăm các loài thực vật cảm xúc.",
            descriptionForParent = "Vườn cảm xúc là hệ thống động lực học tập nhẹ, không có phạt hay mất chuỗi.",
            route = "garden",
            exampleQuestions = listOf("Làm sao để nhận nước?", "Ánh nắng dùng để làm gì?"),
            safeActions = listOf(ChatAction(ChatActionType.OPEN_GARDEN, "Mở Vườn cảm xúc"))
        ),
        AppFeature(
            id = "settings",
            name = "Cài đặt",
            descriptionForChild = "Nơi chỉnh âm thanh, Mầm Mầm, video và các lựa chọn trong app.",
            descriptionForParent = "Nơi quản lý tài khoản, email phụ huynh, quyền riêng tư và dữ liệu học tập.",
            route = "settings",
            exampleQuestions = listOf("Thêm email phụ huynh thế nào?", "Bật/tắt camera ở đâu?"),
            safeActions = listOf(
                ChatAction(ChatActionType.OPEN_SETTINGS, "Mở Cài đặt"),
                ChatAction(ChatActionType.OPEN_PARENT_EMAIL_SETTINGS, "Email phụ huynh")
            )
        )
    )
}

object AssistantKnowledge {
    fun contextFor(
        contextId: String,
        level: Int?,
        gardenSummary: GardenHomeSummary? = null
    ): AppChatContext {
        val emotion = extractEmotion(contextId)
        return when {
            contextId.startsWith("learn_") -> AppChatContext(
                currentRoute = "learn_detail",
                currentScreenName = "Học cảm xúc",
                currentEmotionId = emotion,
                level = level,
                gardenSummary = gardenSummary
            )

            contextId == "learn" -> AppChatContext(
                currentRoute = "learn",
                currentScreenName = "Học cảm xúc",
                level = level,
                gardenSummary = gardenSummary
            )

            contextId == "home" -> AppChatContext(
                currentRoute = "home",
                currentScreenName = "Trang chủ",
                recommendedEmotionId = gardenSummary?.suggestedEmotionToCare,
                gardenSummary = gardenSummary
            )

            contextId == "select_game" || contextId == "level_select" -> AppChatContext(
                currentRoute = contextId,
                currentScreenName = if (contextId == "level_select") "Chọn cấp độ" else "Chơi game",
                level = level,
                gardenSummary = gardenSummary
            )

            contextId == "gameCV" -> AppChatContext(
                currentRoute = "camera_story_game",
                currentScreenName = "Câu chuyện khuôn mặt",
                currentGameId = "gameCV",
                level = level,
                gardenSummary = gardenSummary
            )

            contextId.startsWith("game_cv_2") -> AppChatContext(
                currentRoute = "camera_challenge_game",
                currentScreenName = "Thử thách cảm xúc",
                currentGameId = "game_cv_2",
                currentEmotionId = emotion,
                level = level,
                gardenSummary = gardenSummary
            )

            contextId.startsWith("game") || contextId in setOf("emotions_box", "face_assembly", "emotion_match", "detective_game") -> AppChatContext(
                currentRoute = "game",
                currentScreenName = "Chơi game",
                currentGameId = contextId,
                level = level,
                gardenSummary = gardenSummary
            )

            contextId == "report" -> AppChatContext(
                currentRoute = "report",
                currentScreenName = "Báo cáo của bé",
                level = level,
                gardenSummary = gardenSummary
            )

            contextId == "photobooth" -> AppChatContext(
                currentRoute = "photobooth",
                currentScreenName = "EmoGarden Photobooth",
                level = level,
                gardenSummary = gardenSummary
            )

            contextId == "garden" -> AppChatContext(
                currentRoute = "garden",
                currentScreenName = "Vườn cảm xúc",
                level = level,
                gardenSummary = gardenSummary
            )

            contextId == "settings" -> AppChatContext(
                currentRoute = "settings",
                currentScreenName = "Cài đặt",
                level = level,
                audience = ChatAudience.PARENT,
                gardenSummary = gardenSummary
            )

            contextId == "parent_area" || contextId == "settings_parent_email" -> AppChatContext(
                currentRoute = "parent_area",
                currentScreenName = "Khu vực phụ huynh",
                level = level,
                audience = ChatAudience.PARENT,
                isParentArea = true,
                gardenSummary = gardenSummary
            )

            contextId == "profile" -> AppChatContext(
                currentRoute = "profile",
                currentScreenName = "Hồ sơ",
                level = level,
                gardenSummary = gardenSummary
            )

            else -> AppChatContext(
                currentRoute = contextId,
                currentScreenName = "màn hiện tại",
                level = level,
                gardenSummary = gardenSummary
            )
        }
    }

    fun welcome(context: AppChatContext): String {
        return when (context.currentRoute) {
            "home" -> "Chào con! Mình có thể gợi ý hôm nay nên học gì, chơi gì, chăm Vườn cảm xúc hoặc xem báo cáo nhé."
            "learn", "learn_detail" -> {
                val emotion = context.currentEmotionId?.let(::emotionDisplayName)
                if (emotion != null) {
                    "Con đang học cảm xúc $emotion. Mình có thể giải thích cảm xúc này hoặc cho con ví dụ."
                } else {
                    "Con đang ở màn Học cảm xúc. Mình có thể giải thích từng cảm xúc bằng ví dụ dễ hiểu."
                }
            }
            "camera_story_game", "camera_challenge_game" -> "Mình có thể hướng dẫn con đưa mặt vào khung, biểu hiện cảm xúc và thử lại khi camera chưa nhận rõ."
            "report" -> "Mình có thể giải thích điểm số, cảm xúc cần luyện thêm hoặc cách gửi báo cáo cho bố mẹ."
            "photobooth" -> "Mình có thể hướng dẫn con chọn cảm xúc, chụp ảnh và lưu ảnh photobooth."
            "garden" -> "Mình có thể giải thích cách nhận nước, ánh nắng và chăm các loài thực vật cảm xúc."
            "settings", "parent_area" -> "Mình có thể giúp phụ huynh tìm cài đặt email, camera và quyền riêng tư."
            "profile" -> "Mình có thể giải thích huy hiệu, thống kê chơi game và thông tin hồ sơ của con."
            else -> "Chào con! Mình là Mầm Mầm. Con có thể hỏi mình về học cảm xúc, chơi game, báo cáo, Photobooth hoặc Vườn cảm xúc nhé."
        }
    }

    fun contextText(context: AppChatContext): String {
        val levelText = context.level?.let { " · Cấp độ $it" }.orEmpty()
        return when (context.currentRoute) {
            "home" -> "Đang hỗ trợ: Trang chủ"
            "learn_detail" -> "Đang hỗ trợ: Học ${context.currentEmotionId?.let(::emotionDisplayName) ?: "cảm xúc"}$levelText"
            "learn" -> "Đang hỗ trợ: Học cảm xúc"
            "camera_story_game" -> "Đang hỗ trợ: Câu chuyện khuôn mặt$levelText"
            "camera_challenge_game" -> "Đang hỗ trợ: Thử thách cảm xúc$levelText"
            "game", "select_game", "level_select" -> "Đang hỗ trợ: Chơi game$levelText"
            "report" -> "Đang hỗ trợ: Báo cáo của bé"
            "photobooth" -> "Đang hỗ trợ: EmoGarden Photobooth"
            "garden" -> gardenContextText(context.gardenSummary)
            "settings" -> "Đang hỗ trợ: Cài đặt"
            "parent_area" -> "Đang hỗ trợ: Khu vực phụ huynh"
            "profile" -> "Đang hỗ trợ: Hồ sơ"
            else -> "Đang hỗ trợ: ${context.currentScreenName}$levelText"
        }
    }

    fun quickSuggestions(context: AppChatContext): List<String> {
        return when (context.currentRoute) {
            "home" -> listOf("Hôm nay con nên học gì?", "Vườn cảm xúc là gì?", "Báo cáo của bé ở đâu?", "Photobooth dùng thế nào?")
            "learn", "learn_detail" -> listOf("Cảm xúc này là gì?", "Khi nào con cảm thấy như vậy?", "Con nên làm gì?", "Cho con ví dụ nhé")
            "game", "select_game", "level_select" -> listOf("Cách chơi game này thế nào?", "Con cần làm gì để đúng?", "Nếu con sai thì sao?", "Mở thử thách cảm xúc")
            "camera_story_game", "camera_challenge_game" -> listOf("Camera không thấy mặt con", "Làm sao để biểu hiện đúng?", "Vì sao con chưa đúng?", "Thử lại thế nào?")
            "report" -> listOf("Điểm trung bình nghĩa là gì?", "Cảm xúc cần luyện thêm là gì?", "Gửi báo cáo cho bố mẹ thế nào?", "Vì sao chưa gửi được báo cáo?")
            "photobooth" -> listOf("Photobooth là gì?", "Chọn nhiều cảm xúc thế nào?", "Ảnh ghép lưu ở đâu?", "Có gửi ảnh cho bố mẹ không?")
            "garden" -> listOf("Làm sao để nhận nước?", "Ánh nắng dùng để làm gì?", "Vì sao cây chưa lớn?", "Nhiệm vụ hôm nay ở đâu?")
            "settings", "parent_area" -> listOf("Thêm email phụ huynh thế nào?", "Bật/tắt camera ở đâu?", "Dữ liệu của bé có an toàn không?", "Báo cáo gửi thế nào?")
            else -> listOf("Hôm nay nên học gì?", "Gợi ý trò chơi", "Báo cáo của bé", "Vườn cảm xúc")
        }
    }

    fun reply(message: String, context: AppChatContext): AssistantReply {
        val normalized = normalize(message)
        val emotion = detectEmotion(normalized) ?: context.currentEmotionId

        return when {
            hasAny(normalized, "photobooth", "photo booth", "chup", "anh ghep", "khung anh", "luu anh") -> photoboothReply(normalized)
            hasAny(normalized, "vuon", "cay", "hoa", "nuoc", "anh nang", "nhiem vu", "tuoi", "tam nang") -> gardenReply(context)
            hasAny(normalized, "bao cao", "email", "gmail", "bo me", "phu huynh", "pdf", "diem trung binh", "gui") -> reportReply(context)
            hasAny(normalized, "camera", "may anh", "mat", "khung", "quyen camera", "khong thay") -> cameraReply()
            hasAny(normalized, "cai dat", "quyen rieng tu", "du lieu", "bao mat", "tai khoan") -> settingsReply(context)
            hasAny(normalized, "cam xuc", "vui", "buon", "tuc", "gian", "so", "ngac", "ghe") -> emotionReply(emotion)
            hasAny(normalized, "game", "choi", "thu thach", "cap do", "dung", "sai") -> gameReply(context)
            hasAny(normalized, "hoc gi", "nen hoc", "tiep theo", "goi y", "hom nay") -> nextStepReply(context)
            hasAny(normalized, "man nay", "dang o dau", "lam gi", "huong dan") -> explainCurrentScreen(context)
            isOutsideAppQuestion(normalized) -> AssistantReply(
                text = "Câu này hơi ngoài EmoGarden rồi. Mình có thể giúp con học cảm xúc, chơi game, báo cáo, Photobooth hoặc Vườn cảm xúc nhé."
            )
            else -> AssistantReply(
                text = "Mình chưa hiểu rõ lắm. Con có thể hỏi về học cảm xúc, chơi game, camera, báo cáo, Photobooth hoặc Vườn cảm xúc nhé.",
                actions = listOf(
                    ChatAction(ChatActionType.OPEN_LEARNING, "Học cảm xúc"),
                    ChatAction(ChatActionType.OPEN_GARDEN, "Vườn cảm xúc")
                )
            )
        }
    }

    fun emotionDisplayName(id: String): String {
        return when (normalizeEmotionId(id)) {
            "happy" -> "Vui vẻ"
            "sad" -> "Buồn bã"
            "angry" -> "Tức giận"
            "fear" -> "Sợ hãi"
            "surprise" -> "Ngạc nhiên"
            "disgust" -> "Ghê tởm"
            else -> id
        }
    }

    fun normalizeEmotionId(value: String): String {
        val normalized = normalize(value)
        return when {
            normalized.contains("happy") || normalized.contains("vui") -> "happy"
            normalized.contains("sad") || normalized.contains("buon") -> "sad"
            normalized.contains("angry") || normalized.contains("tuc") || normalized.contains("gian") -> "angry"
            normalized.contains("fear") || normalized.contains("so") -> "fear"
            normalized.contains("surprise") || normalized.contains("ngac") -> "surprise"
            normalized.contains("disgust") || normalized.contains("ghe") -> "disgust"
            else -> value.lowercase(Locale.ROOT)
        }
    }

    private fun explainCurrentScreen(context: AppChatContext): AssistantReply {
        val feature = AppFeatureRegistry.features.firstOrNull { it.route == context.currentRoute || it.id == context.currentRoute }
        val text = when {
            context.currentRoute == "garden" -> gardenReply(context).text
            context.currentRoute == "report" -> reportReply(context).text
            context.currentRoute == "photobooth" -> photoboothReply("").text
            context.audience == ChatAudience.PARENT && feature != null -> feature.descriptionForParent
            feature != null -> feature.descriptionForChild
            else -> "Màn này là ${context.currentScreenName}. Con có thể hỏi mình cần làm gì tiếp theo nhé."
        }
        return AssistantReply(text = text, actions = actionsForContext(context))
    }

    private fun nextStepReply(context: AppChatContext): AssistantReply {
        val recommended = context.recommendedEmotionId ?: context.gardenSummary?.suggestedEmotionToCare
        val emotionText = recommended?.let(::emotionDisplayName)
        return when (context.currentRoute) {
            "home" -> AssistantReply(
                text = if (emotionText != null) {
                    "Hôm nay con có thể luyện thêm cảm xúc $emotionText, rồi ghé Vườn cảm xúc để nhận nước hoặc ánh nắng nhé."
                } else {
                    "Hôm nay con có thể học một cảm xúc, chơi một trò chơi ngắn, rồi ghé Vườn cảm xúc để chăm cây nhé."
                },
                actions = listOf(
                    recommended?.let { ChatAction(ChatActionType.OPEN_EMOTION_LESSON, "Học $emotionText", it) }
                        ?: ChatAction(ChatActionType.OPEN_LEARNING, "Mở Học"),
                    ChatAction(ChatActionType.OPEN_GARDEN, "Chăm vườn")
                )
            )
            "garden" -> AssistantReply(
                text = "Con xem phần Nhiệm vụ hôm nay trước. Khi có nước hoặc ánh nắng, con chọn một loài thực vật rồi bấm Tưới nước hoặc Tắm nắng.",
                actions = listOf(ChatAction(ChatActionType.OPEN_GARDEN, "Xem Vườn"))
            )
            else -> AssistantReply(
                text = "Con có thể học một cảm xúc trước, sau đó chơi thử thách hoặc chụp Photobooth để luyện thêm.",
                actions = listOf(
                    ChatAction(ChatActionType.OPEN_LEARNING, "Mở Học"),
                    ChatAction(ChatActionType.OPEN_GAME, "Mở Chơi game")
                )
            )
        }
    }

    private fun emotionReply(emotionId: String?): AssistantReply {
        val id = emotionId?.let(::normalizeEmotionId)
        val text = when (id) {
            "happy" -> "Vui vẻ là khi con thấy thích thú, thoải mái hoặc muốn cười. Con có thể cười nhẹ, mắt sáng hơn và chia sẻ niềm vui với người khác."
            "sad" -> "Buồn bã là khi con thấy mất hứng, muốn yên lặng hoặc cần được an ủi. Con có thể nói với người lớn: 'Con đang buồn'."
            "angry" -> "Tức giận là khi con thấy không hài lòng hoặc bị làm phiền. Con có thể hít thở chậm, nói điều con không thích và nhờ người lớn giúp."
            "fear" -> "Sợ hãi là khi con thấy lo lắng hoặc không an toàn. Con nên đứng gần người lớn, nói điều làm con sợ và thử thở chậm."
            "surprise" -> "Ngạc nhiên là khi con gặp điều bất ngờ. Mắt con có thể mở to, miệng hơi chữ O, rồi con nhìn kỹ xem chuyện gì đang xảy ra."
            "disgust" -> "Ghê tởm là khi con gặp mùi, vị hoặc điều làm con thấy khó chịu. Con có thể quay đi nhẹ nhàng và nói: 'Con không thích điều này'."
            else -> "Cảm xúc là tín hiệu giúp con hiểu trong lòng mình đang thế nào. Con có thể hỏi mình về Vui vẻ, Buồn bã, Tức giận, Sợ hãi, Ngạc nhiên hoặc Ghê tởm."
        }
        val actions = id?.let {
            listOf(
                ChatAction(ChatActionType.OPEN_EMOTION_LESSON, "Học ${emotionDisplayName(it)}", it),
                ChatAction(ChatActionType.START_EMOTION_CHALLENGE, "Thử biểu cảm", it)
            )
        } ?: listOf(ChatAction(ChatActionType.OPEN_LEARNING, "Mở Học cảm xúc"))
        return AssistantReply(text = text, actions = actions)
    }

    private fun gameReply(context: AppChatContext): AssistantReply {
        return when (context.currentRoute) {
            "camera_story_game" -> AssistantReply(
                text = "Ở Câu chuyện khuôn mặt, con đọc tình huống rồi làm khuôn mặt phù hợp trước camera. Không cần chọn đáp án. Nếu chưa đúng, con thử biểu hiện rõ hơn và giữ mặt trong khung nhé.",
                actions = listOf(ChatAction(ChatActionType.OPEN_PRIVACY_SETTINGS, "Xem quyền camera"))
            )
            "camera_challenge_game" -> AssistantReply(
                text = "Ở Thử thách cảm xúc, con làm đúng cảm xúc trong khung camera và giữ trong vài giây. Nếu app chưa thấy mặt, con đưa mặt vào giữa khung và ngồi nơi sáng hơn nhé.",
                actions = context.currentEmotionId?.let {
                    listOf(ChatAction(ChatActionType.START_EMOTION_CHALLENGE, "Thử ${emotionDisplayName(it)}", it))
                } ?: emptyList()
            )
            else -> AssistantReply(
                text = "Khi chơi game, con quan sát tình huống hoặc hình ảnh thật kỹ rồi làm theo hướng dẫn trên màn hình. Sai cũng không sao, mình thử lại từng bước nhé.",
                actions = listOf(ChatAction(ChatActionType.OPEN_GAME, "Mở Chơi game"))
            )
        }
    }

    private fun cameraReply(): AssistantReply {
        return AssistantReply(
            text = "Con thử đưa cả khuôn mặt vào giữa khung, ngồi nơi sáng hơn và nhìn vào camera nhé. Camera dùng để nhận diện biểu cảm khi chơi; EmoGarden không tự động lưu video.",
            actions = listOf(
                ChatAction(ChatActionType.OPEN_PRIVACY_SETTINGS, "Xem quyền camera"),
                ChatAction(ChatActionType.OPEN_SETTINGS, "Mở Cài đặt")
            )
        )
    }

    private fun reportReply(context: AppChatContext): AssistantReply {
        val summary = context.reportSummaryText?.takeIf { it.isNotBlank() }
        val base = summary?.let {
            "Báo cáo đang ghi nhận: $it. "
        }.orEmpty()
        val emailHint = when (context.parentEmailExists) {
            false -> "Nếu chưa có email của bố mẹ, con nhờ phụ huynh thêm email trong Khu vực phụ huynh. "
            true -> "Khi bấm gửi, app sẽ hỏi lại trước khi gửi đến email phụ huynh. "
            null -> "Nếu chưa gửi được, phụ huynh nên kiểm tra email phụ huynh trong cài đặt. "
        }
        return AssistantReply(
            text = base + "Báo cáo của bé cho biết tuần này con đã luyện thế nào, điểm trung bình ra sao và cảm xúc nào cần luyện thêm. $emailHint EmoGarden không tự gửi báo cáo nếu chưa có xác nhận.",
            actions = listOf(
                ChatAction(ChatActionType.OPEN_REPORT, "Mở Báo cáo"),
                ChatAction(ChatActionType.OPEN_PARENT_EMAIL_SETTINGS, "Email phụ huynh")
            )
        )
    }

    private fun photoboothReply(normalizedMessage: String): AssistantReply {
        val asksSend = hasAny(normalizedMessage, "gui", "bo me", "email", "chia se")
        val text = if (asksSend) {
            "Ảnh Photobooth chỉ được lưu hoặc gửi khi con hoặc phụ huynh chọn xác nhận. EmoGarden không tự động gửi ảnh cho bố mẹ."
        } else {
            "Photobooth là nơi con chọn vài cảm xúc, chụp từng khuôn mặt, chọn khung dễ thương rồi EmoGarden ghép thành một ảnh photobooth cuối cùng. Đây là hoạt động vui, không chấm điểm."
        }
        return AssistantReply(
            text = text,
            actions = listOf(
                ChatAction(ChatActionType.OPEN_PHOTOBOOTH, "Mở Photobooth"),
                ChatAction(ChatActionType.ASK_CONFIRM_SAVE_PHOTO, "Hỏi trước khi lưu")
            )
        )
    }

    private fun gardenReply(context: AppChatContext): AssistantReply {
        val summary = context.gardenSummary
        val stats = summary?.let {
            "Hiện vườn phát triển ${it.gardenProgressPercent}%, có ${it.pendingRewardCount} phần thưởng chờ nhận và ${it.completedTodayTaskCount}/${it.todayTaskCount} nhiệm vụ hôm nay đã xong. "
        }.orEmpty()
        val suggested = summary?.suggestedEmotionToCare?.let {
            "Loài nên chăm thêm là ${emotionDisplayName(it)}. "
        }.orEmpty()
        return AssistantReply(
            text = stats + suggested + "Vườn cảm xúc có 6 loài thực vật: Vui vẻ là Hoa hướng dương, Buồn bã là Cây liễu xanh, Tức giận là Cây ớt đỏ, Sợ hãi là Cây xấu hổ, Ngạc nhiên là Hoa tulip bất ngờ, Ghê tởm là Cây nắp ấm. Con hoàn thành nhiệm vụ để nhận nước và ánh nắng, rồi dùng chúng để chăm cây lớn lên.",
            actions = listOf(ChatAction(ChatActionType.OPEN_GARDEN, "Chăm vườn"))
        )
    }

    private fun settingsReply(context: AppChatContext): AssistantReply {
        val text = if (context.audience == ChatAudience.PARENT || context.isParentArea) {
            "Trong Khu vực phụ huynh, phụ huynh có thể quản lý email nhận báo cáo, tài khoản, quyền camera và dữ liệu học tập. Những thao tác nhạy cảm như đổi email, gửi báo cáo hay xóa dữ liệu cần xác nhận."
        } else {
            "Cài đặt là nơi chỉnh âm thanh, bong bóng Mầm Mầm, camera và quyền riêng tư. Nếu cần thêm email bố mẹ, con nhờ phụ huynh mở Khu vực phụ huynh nhé."
        }
        return AssistantReply(
            text = text,
            actions = listOf(
                ChatAction(ChatActionType.OPEN_SETTINGS, "Mở Cài đặt"),
                ChatAction(ChatActionType.OPEN_PARENT_EMAIL_SETTINGS, "Email phụ huynh")
            )
        )
    }

    private fun gardenContextText(summary: GardenHomeSummary?): String {
        return if (summary == null) {
            "Đang hỗ trợ: Vườn cảm xúc"
        } else {
            "Đang hỗ trợ: Vườn cảm xúc · ${summary.pendingRewardCount} thưởng chờ nhận · ${summary.gardenProgressPercent}%"
        }
    }

    private fun actionsForContext(context: AppChatContext): List<ChatAction> {
        return when (context.currentRoute) {
            "home" -> listOf(ChatAction(ChatActionType.OPEN_LEARNING, "Mở Học"), ChatAction(ChatActionType.OPEN_GARDEN, "Chăm vườn"))
            "learn", "learn_detail" -> listOf(ChatAction(ChatActionType.OPEN_GAME, "Chơi game"))
            "report" -> listOf(ChatAction(ChatActionType.OPEN_REPORT, "Mở Báo cáo"), ChatAction(ChatActionType.OPEN_PARENT_EMAIL_SETTINGS, "Email phụ huynh"))
            "photobooth" -> listOf(ChatAction(ChatActionType.OPEN_PHOTOBOOTH, "Mở Photobooth"))
            "garden" -> listOf(ChatAction(ChatActionType.OPEN_GARDEN, "Mở Vườn"))
            "settings", "parent_area" -> listOf(ChatAction(ChatActionType.OPEN_SETTINGS, "Mở Cài đặt"))
            else -> emptyList()
        }
    }

    private fun extractEmotion(value: String): String? {
        val normalized = normalize(value)
        return detectEmotion(normalized)
    }

    private fun detectEmotion(normalized: String): String? {
        return when {
            hasAny(normalized, "happy", "vui") -> "happy"
            hasAny(normalized, "sad", "buon") -> "sad"
            hasAny(normalized, "angry", "tuc", "gian") -> "angry"
            hasAny(normalized, "fear", "so hai", "so") -> "fear"
            hasAny(normalized, "surprise", "ngac") -> "surprise"
            hasAny(normalized, "disgust", "ghe") -> "disgust"
            else -> null
        }
    }

    private fun hasAny(value: String, vararg needles: String): Boolean {
        return needles.any { value.contains(it) }
    }

    private fun isOutsideAppQuestion(value: String): Boolean {
        return hasAny(value, "thoi tiet", "bong da", "co phieu", "bitcoin", "nau an", "tin tuc", "toan lop", "viet van")
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        val noMarks = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return noMarks.replace('đ', 'd').replace('Đ', 'd')
    }
}
