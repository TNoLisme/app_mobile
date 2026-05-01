package com.example.appmobile.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "game_data")
data class GameDataEntity(
    @PrimaryKey @ColumnInfo(name = "data_id") val dataId: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val level: Int
)