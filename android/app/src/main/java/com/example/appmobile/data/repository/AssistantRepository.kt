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
                "Chưa gọi được trợ lý. Backend trả mã ${response.code()}."
            }
        } catch (e: Exception) {
            "Chưa kết nối được trợ lý. Hãy kiểm tra backend đang chạy."
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
}
