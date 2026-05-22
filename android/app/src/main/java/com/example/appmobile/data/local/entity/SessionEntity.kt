package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "end_time") val endTime: String?,
    val score: Int,
    val level: Int,
    @ColumnInfo(name = "emotion_errors") val emotionErrors: String?,
    val state: String? = "playing",
    @ColumnInfo(name = "max_errors") val maxErrors: Int? = 3,
    @ColumnInfo(name = "level_threshold") val levelThreshold: Float? = 70.0f,
    val ratio: String? = null,
    @ColumnInfo(name = "time_limit") val timeLimit: Int? = null,
    @ColumnInfo(name = "question_ids") val questionIds: String? = null
)