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
    @SerializedName("progress_count") val progressCount: Int? = null
)

data class ReportPreviewDataDto(
    @SerializedName("child_user_id") val childUserId: String? = null,
    @SerializedName("report_type") val reportType: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("stats") val stats: ReportStatsDto? = null
)

data class ReportPreviewResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("data") val data: ReportPreviewDataDto? = null
)

data class ReportPayloadDto(
    @SerializedName("report_id") val reportId: String? = null,
    @SerializedName("child_id") val childId: String? = null,
    @SerializedName("child_name") val childName: String? = null,
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
    @SerializedName("report_type") val reportType: String = "weekly"
)

data class ReportRequestResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: ReportPayloadDto? = null
)
