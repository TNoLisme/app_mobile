package com.example.appmobile.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("username") val username: String?,
    @SerializedName("email") val email: String,
    @SerializedName("role") val role: String,
    @SerializedName("name") val name: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class ChildDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("age") val age: Int?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("date_of_birth") val dob: String?,
    @SerializedName("phone_number") val phone: String?,
    @SerializedName("report_preferences") val reportPref: String?
)

data class ChatbotLogDto(
    @SerializedName("log_id") val logId: Int,
    @SerializedName("child_id") val childId: String,
    @SerializedName("sender") val sender: String,
    @SerializedName("message_content") val content: String,
    @SerializedName("timestamp") val timestamp: String
)