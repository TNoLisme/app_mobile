package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "game_type") val gameType: String,
    val name: String,
    val level: Int,
    @ColumnInfo(name = "difficulty_level") val difficultyLevel: String?,
    @ColumnInfo(name = "max_errors") val maxErrors: Int,
    @ColumnInfo(name = "time_limit") val timeLimit: Int?
)