package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "game_data_question", primaryKeys = ["data_id", "question_id"])
data class GameDataQuestionEntity(
    @ColumnInfo(name = "data_id") val dataId: String,
    @ColumnInfo(name = "question_id") val questionId: String
)