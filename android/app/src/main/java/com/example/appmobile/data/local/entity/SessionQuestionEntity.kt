package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_questions")
data class SessionQuestionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "user_answer") val userAnswer: String?,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "response_time_ms") val responseTimeMs: Int,
    @ColumnInfo(name = "cv_confidence") val cvConfidence: Float?
)