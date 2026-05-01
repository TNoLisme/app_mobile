package com.example.appmobile.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

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