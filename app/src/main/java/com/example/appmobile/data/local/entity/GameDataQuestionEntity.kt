package com.example.appmobile.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(tableName = "game_data_question", primaryKeys = ["data_id", "question_id"])
data class GameDataQuestionEntity(
    @ColumnInfo(name = "data_id") val dataId: String,
    @ColumnInfo(name = "question_id") val questionId: String
)