package com.example.appmobile.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "end_time") val endTime: String?,
    val score: Int,
    val level: Int,
    @ColumnInfo(name = "emotion_errors") val emotionErrors: String?
)