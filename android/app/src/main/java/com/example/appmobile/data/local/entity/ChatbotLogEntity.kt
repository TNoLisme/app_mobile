package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chatbot_logs")
data class ChatbotLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "log_id") val logId: Int = 0,
    @ColumnInfo(name = "child_id") val childId: String,
    val sender: String, // "child" hoặc "bot"
    @ColumnInfo(name = "message_content") val content: String,
    val timestamp: String
)