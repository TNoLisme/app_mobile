package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emotion_concepts")
data class EmotionConceptEntity(
    @PrimaryKey @ColumnInfo(name = "concept_id") val conceptId: String,
    val emotion: String,
    val level: Int,
    val title: String,
    @ColumnInfo(name = "video_path") val videoPath: String?,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "audio_path") val audioPath: String?,
    val description: String?
)