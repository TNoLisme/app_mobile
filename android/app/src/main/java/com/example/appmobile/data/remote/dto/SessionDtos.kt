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
    @SerializedName("question_id") val questionId: String? = null,
    @SerializedName("is_correct") val isCorrect: Boolean,
    @SerializedName("response_time_ms") val responseTime: Int,
    @SerializedName("cv_confidence") val confidence: Float?,
    @SerializedName("used_hint") val usedHint: Boolean = false
)

data class StartGameRequestDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("level") val level: Int
)

data class StartGameResponseDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("game_id") val gameId: String,
    @SerializedName("level") val level: Int,
    @SerializedName("max_errors") val maxErrors: Int?,
    @SerializedName("level_threshold") val levelThreshold: Float?,
    @SerializedName("time_limit") val timeLimit: Int?,
    @SerializedName("questions") val questions: List<GameContentDto>
)

data class AnswerResultDto(
    @SerializedName("question_id") val questionId: String,
    @SerializedName("answer") val answer: String?,
    @SerializedName("is_correct") val isCorrect: Boolean,
    @SerializedName("response_time_ms") val responseTimeMs: Int = 0,
    @SerializedName("used_hint") val usedHint: Boolean = false
)

data class EndLevelRequestDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("results") val results: List<AnswerResultDto>,
    @SerializedName("review_emotions") val reviewEmotions: List<String> = emptyList()
)

data class EndLevelResponseDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("score") val score: Int,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("emotion_errors") val emotionErrors: Map<String, Int>?,
    @SerializedName("passed") val passed: Boolean
)

data class GameProgressDto(
    @SerializedName("progress_id") val progressId: String?,
    @SerializedName("child_id") val childId: String?,
    @SerializedName("game_id") val gameId: String?,
    @SerializedName("level") val level: Int?,
    @SerializedName("accuracy") val accuracy: Float?,
    @SerializedName("score") val score: Int?,
    @SerializedName("last_played") val lastPlayed: String?,
    @SerializedName("ratio") val ratio: String?,
    @SerializedName("review_emotions") val reviewEmotions: String?
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
