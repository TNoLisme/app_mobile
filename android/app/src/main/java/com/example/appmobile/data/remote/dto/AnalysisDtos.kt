package com.example.appmobile.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ProgressDto(
    @SerializedName("progress_id") val progressId: String,
    @SerializedName("child_id") val childId: String,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("score") val score: Int,
    @SerializedName("last_played") val lastPlayed: String
)

data class ReportDto(
    @SerializedName("report_id") val reportId: String,
    @SerializedName("report_type") val type: String,
    @SerializedName("summary") val summary: String,
    @SerializedName("data") val jsonData: String
)

data class ReportStatsDto(
    @SerializedName("total_sessions") val totalSessions: Int? = null,
    @SerializedName("avg_score") val avgScore: Float? = null,
    @SerializedName("progress_count") val progressCount: Int? = null,
    @SerializedName("total_games") val totalGames: Int? = null,
    @SerializedName("total_playtime_minutes") val totalPlaytimeMinutes: Int? = null,
    @SerializedName("previous_avg_score") val previousAvgScore: Float? = null
)

data class ReportEmotionStatsDto(
    @SerializedName("correct") val correct: Int? = null,
    @SerializedName("incorrect") val incorrect: Int? = null,
    @SerializedName("accuracy") val accuracy: Float? = null
)

data class ReportGameStatsDto(
    @SerializedName("game_id") val gameId: String? = null,
    @SerializedName("game_name") val gameName: String? = null,
    @SerializedName("sessions") val sessions: Int? = null,
    @SerializedName("avg_score") val avgScore: Float? = null,
    @SerializedName("best_score") val bestScore: Int? = null,
    @SerializedName("level") val level: Int? = null
)

data class ReportInsightsDto(
    @SerializedName("total_playtime_minutes") val totalPlaytimeMinutes: Int? = null,
    @SerializedName("daily_sessions") val dailySessions: Map<String, Int>? = null,
    @SerializedName("games_stats") val gamesStats: List<ReportGameStatsDto>? = null,
    @SerializedName("emotion_stats") val emotionStats: Map<String, ReportEmotionStatsDto>? = null,
    @SerializedName("achievements") val achievements: List<String>? = null,
    @SerializedName("previous_avg_score") val previousAvgScore: Float? = null
)

data class ReportPreviewDataDto(
    @SerializedName("child_user_id") val childUserId: String? = null,
    @SerializedName("report_type") val reportType: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("stats") val stats: ReportStatsDto? = null,
    @SerializedName("insights") val insights: ReportInsightsDto? = null,
    @SerializedName("pdf_enabled") val pdfEnabled: Boolean? = null
)

data class ReportPreviewResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("data") val data: ReportPreviewDataDto? = null
)

data class ReportPayloadDto(
    @SerializedName("report_id") val reportId: String? = null,
    @SerializedName("child_id") val childId: String? = null,
    @SerializedName("child_name") val childName: String? = null,
    @SerializedName("child_email") val childEmail: String? = null,
    @SerializedName("report_type") val reportType: String? = null,
    @SerializedName("generated_at") val generatedAt: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("stats") val stats: ReportStatsDto? = null,
    @SerializedName("data") val data: String? = null
)

data class ReportHistoryResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("data") val data: List<ReportPayloadDto>? = null
)

data class ReportRequestDto(
    @SerializedName("child_user_id") val childUserId: String,
    @SerializedName("report_type") val reportType: String = "weekly",
    @SerializedName("send_email") val sendEmail: Boolean = true
)

data class ReportRequestResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: ReportPayloadDto? = null,
    @SerializedName("email_sent") val emailSent: Boolean? = null,
    @SerializedName("pdf_enabled") val pdfEnabled: Boolean? = null
)

data class SessionHistoryItemDto(
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("game_id") val gameId: String? = null,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("score") val score: Int? = null,
    @SerializedName("level") val level: Int? = null
)

data class SessionHistoryResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("sessions") val sessions: List<SessionHistoryItemDto>? = null
)

data class CvEmotionScoresResponseDto(
    @SerializedName("scores") val scores: Map<String, Float>? = null
)

data class CvCompletedLevelItemDto(
    @SerializedName("level") val level: Int,
    @SerializedName("score") val score: Int? = null,
    @SerializedName("max_score") val maxScore: Int? = null,
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("unlocked") val unlocked: Boolean = false
)

data class CvCompletedLevelsResponseDto(
    @SerializedName("levels") val levels: List<CvCompletedLevelItemDto>? = null,
    @SerializedName("current_level") val currentLevel: Int? = null,
    @SerializedName("max_level") val maxLevel: Int? = null
)
