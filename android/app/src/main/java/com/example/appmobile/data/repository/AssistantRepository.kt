package com.example.appmobile.data.repository

import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.data.remote.dto.AssistantChatRequestDto

class AssistantRepository(private val apiService: ApiService) {
    suspend fun chat(gameId: String, level: Int?, message: String): String {
        return try {
            val response = apiService.chatAssistant(
                AssistantChatRequestDto(
                    gameId = gameId,
                    level = level,
                    message = message
                )
            )
            if (response.isSuccessful) {
                response.body()?.reply?.takeIf { it.isNotBlank() }
                    ?: "Mình chưa có câu trả lời phù hợp."
            } else {
                "Chưa gọi được trợ lý. Backend trả mã ${response.code()}."
            }
        } catch (e: Exception) {
            "Chưa kết nối được trợ lý. Hãy kiểm tra backend đang chạy."
        }
    }
}
