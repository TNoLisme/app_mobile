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