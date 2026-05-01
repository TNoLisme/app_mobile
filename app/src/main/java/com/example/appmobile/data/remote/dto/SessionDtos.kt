package com.example.appmobile.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SessionDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("score") val score: Int,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("emotion_errors") val emotionErrors: String?
)

data class SessionQuestionDto(
    @SerializedName("id") val id: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("is_correct") val isCorrect: Boolean,
    @SerializedName("response_time_ms") val responseTime: Int,
    @SerializedName("cv_confidence") val confidence: Float?
)

data class GameDataDto(
    @SerializedName("data_id") val dataId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("level") val level: Int
)

data class GameDataQuestionDto(
    @SerializedName("data_id") val dataId: String,
    @SerializedName("question_id") val questionId: String
)