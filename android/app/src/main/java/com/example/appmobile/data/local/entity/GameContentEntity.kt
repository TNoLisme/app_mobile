package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_content")
data class GameContentEntity(
    @PrimaryKey @ColumnInfo(name = "content_id") val contentId: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    val level: Int,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "media_path") val mediaPath: String?,
    @ColumnInfo(name = "question_text") val questionText: String?,
    @ColumnInfo(name = "correct_answer") val correctAnswer: String?,
    val emotion: String?,
    val explanation: String?
)