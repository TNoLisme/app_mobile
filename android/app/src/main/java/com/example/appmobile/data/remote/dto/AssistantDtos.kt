package com.example.appmobile.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AssistantChatRequestDto(
    @SerializedName("game_id") val gameId: String,
    @SerializedName("level") val level: Int? = null,
    @SerializedName("message") val message: String,
    @SerializedName("child_id") val childId: String? = null,
    @SerializedName("history") val history: List<AssistantChatHistoryDto> = emptyList()
)

data class AssistantChatHistoryDto(
    @SerializedName("role") val role: String,
    @SerializedName("text") val text: String
)

data class AssistantChatResponseDto(
    @SerializedName("reply") val reply: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("suggestions") val suggestions: List<String>? = null
)
