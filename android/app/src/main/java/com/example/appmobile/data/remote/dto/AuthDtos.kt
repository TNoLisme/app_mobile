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

data class UserProfileDto(
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("child") val child: ChildDto? = null
)

data class UserProfileUpdateDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null
)

data class UserProfileUpdateRequestDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("update") val update: UserProfileUpdateDto
)

data class ChildRegisterRequestDto(
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("role") val role: String = "child",
    @SerializedName("name") val name: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("report_preferences") val reportPreferences: String? = null
)

data class BackendRegisterDataDto(
    @SerializedName("user_id") val userId: String? = null
)

data class BackendRegisterResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: BackendRegisterDataDto? = null
)

data class BackendLoginRequestDto(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class BackendLoginResponseDto(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("user") val user: UserProfileDto? = null
)

data class RecentGameDto(
    @SerializedName("game_id") val gameId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("game_type") val gameType: String? = null,
    @SerializedName("last_played") val lastPlayed: String? = null
)

data class RecentGamesResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("data") val data: List<RecentGameDto>? = null
)

data class EmotionAccuracyDto(
    @SerializedName("correct") val correct: Int? = null,
    @SerializedName("incorrect") val incorrect: Int? = null,
    @SerializedName("accuracy") val accuracy: Float? = null
)

data class EmotionAccuracyResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("data") val data: Map<String, EmotionAccuracyDto>? = null
)

data class PercentMapResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("data") val data: Map<String, Float>? = null
)

data class WeakEmotionDto(
    @SerializedName("emotion") val emotion: String? = null,
    @SerializedName("error_rate") val errorRate: Float? = null
)

data class WeakEmotionsResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("data") val data: List<WeakEmotionDto>? = null
)
