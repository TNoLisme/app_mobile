package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_data")
data class GameDataEntity(
    @PrimaryKey @ColumnInfo(name = "data_id") val dataId: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val level: Int
)