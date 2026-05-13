package com.example.appmobile.data.repository

import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.data.remote.dto.AssistantChatHistoryDto
import com.example.appmobile.data.remote.dto.AssistantChatRequestDto
import com.example.appmobile.data.remote.dto.ChatbotLogDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AssistantRepository(private val apiService: ApiService) {
    suspend fun chat(
        gameId: String,
        level: Int?,
        message: String,
        childId: String?,
        history: List<AssistantChatHistoryDto>
    ): String {
        return try {
            val response = apiService.chatAssistant(
                AssistantChatRequestDto(
                    gameId = gameId,
                    level = level,
                    message = message,
                    childId = childId,
                    history = history.takeLast(10)
                )
            )
            if (response.isSuccessful) {
                response.body()?.reply?.takeIf { it.isNotBlank() }
                    ?: "Mình chưa có câu trả lời phù hợp. Con thử hỏi ngắn hơn nhé."
            } else {
                localFallbackReply(gameId, level, message)
            }
        } catch (e: Exception) {
            localFallbackReply(gameId, level, message)
        }
    }

    suspend fun uploadLog(childId: String?, sender: String, content: String) {
        val safeChildId = childId?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            apiService.uploadChatLog(
                ChatbotLogDto(
                    logId = 0,
                    childId = safeChildId,
                    sender = sender,
                    content = content,
                    timestamp = nowIsoUtc()
                )
            )
        }
    }

    private fun nowIsoUtc(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }

    private fun localFallbackReply(gameId: String, level: Int?, message: String): String {
        val text = message.lowercase(Locale("vi", "VN"))
        val levelText = level?.let { " Cấp độ hiện tại là $it." }.orEmpty()

        if (listOf("camera", "máy ảnh", "không bật", "quyền").any { it in text }) {
            return "Nếu camera chưa bật, con hãy bấm Bắt đầu thử thách rồi cho phép quyền camera. Hãy để mặt ở giữa khung hình và phòng đủ sáng nhé."
        }
        if (listOf("mic", "micro", "giọng nói", "không nghe").any { it in text }) {
            return "Nếu mic chưa nghe được, con hãy cấp quyền micro và nói chậm hơn. Nếu máy không hỗ trợ nhận diện giọng nói, con có thể gõ câu hỏi nhé."
        }
        if (listOf("nên chơi", "chơi gì", "game nào").any { it in text }) {
            return "Nếu mới chơi, con nên bắt đầu với Chiếc hộp cảm xúc. Khi đã quen, con thử Thử thách cảm xúc để luyện biểu cảm qua camera nhé."
        }
        if (listOf("vui", "buồn", "tức", "giận", "sợ", "ngạc nhiên", "ghê tởm").any { it in text }) {
            return "Con hãy quan sát mắt, miệng và lông mày. Nếu chưa chắc, con vào màn Học để xem video mẫu của cảm xúc đó trước nhé."
        }

        return when (gameId) {
            "gameCV" -> "Ở Câu chuyện khuôn mặt, con đọc tình huống, tự đoán cảm xúc rồi thể hiện bằng khuôn mặt trước camera.$levelText"
            "game_cv_2" -> "Ở Thử thách cảm xúc, con chọn cảm xúc rồi bấm Bắt đầu thử thách để thể hiện đúng biểu cảm trước camera.$levelText"
            "learn" -> "Ở màn Học, con chọn một cảm xúc để xem video mẫu và dấu hiệu nhận biết."
            "select_game" -> "Ở màn Chơi game, con chọn trò phù hợp. Nếu mới bắt đầu, hãy chơi game dễ trước."
            "level_select" -> "Ở màn Chọn cấp độ, con chọn cấp đang mở để bắt đầu chơi.$levelText"
            else -> "Mình đang trả lời tạm trên máy vì chưa kết nối được backend. Con có thể hỏi cách chơi, gợi ý cảm xúc hoặc cách dùng app nhé."
        }
    }
}
