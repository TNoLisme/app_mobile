package com.example.appmobile.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AssistantChatRequestDto(
    @SerializedName("game_id") val gameId: String,
    @SerializedName("level") val level: Int? = null,
    @SerializedName("message") val message: String
)

data class AssistantChatResponseDto(
    @SerializedName("reply") val reply: String? = null
)
