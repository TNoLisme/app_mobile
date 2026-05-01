package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "child_progress")
data class ProgressEntity(
    @PrimaryKey @ColumnInfo(name = "progress_id") val progressId: String,
    @ColumnInfo(name = "child_id") val childId: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    val level: Int,
    val accuracy: Float,
    val score: Int,
    @ColumnInfo(name = "last_played") val lastPlayed: String
)