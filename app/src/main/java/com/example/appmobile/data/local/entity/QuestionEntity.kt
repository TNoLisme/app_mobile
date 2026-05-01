package com.example.appmobile.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "content_id") val contentId: String,
    val level: Int,
    @ColumnInfo(name = "correct_answer") val correctAnswer: String
)