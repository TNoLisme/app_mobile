package com.example.appmobile.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GameDto(
    @SerializedName("game_id") val gameId: String,
    @SerializedName("game_type") val gameType: String,
    @SerializedName("name") val name: String,
    @SerializedName("level") val level: Int,
    @SerializedName("max_errors") val maxErrors: Int,
    @SerializedName("time_limit") val timeLimit: Int?
)

data class EmotionConceptDto(
    @SerializedName("concept_id") val conceptId: String,
    @SerializedName("emotion") val emotion: String,
    @SerializedName("title") val title: String,
    @SerializedName("video_path") val videoPath: String?,
    @SerializedName("image_path") val imagePath: String?,
    @SerializedName("description") val description: String?
)

data class GameContentDto(
    @SerializedName("content_id") val contentId: String,
    @SerializedName("game_id") val gameId: String,
    @SerializedName("level") val level: Int?,
    @SerializedName("content_type") val contentType: String?,
    @SerializedName("media_path") val mediaPath: String?,
    @SerializedName("question_text") val questionText: String?,
    @SerializedName("correct_answer") val correctAnswer: String?,
    @SerializedName("emotion") val emotion: String?,
    @SerializedName("explanation") val explanation: String?
)

data class QuestionDto(
    @SerializedName("question_id") val questionId: String,
    @SerializedName("content_id") val contentId: String,
    @SerializedName("correct_answer") val correctAnswer: String
)
