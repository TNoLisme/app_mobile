package com.example.appmobile.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "chatbot_logs")
data class ChatbotLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "log_id") val logId: Int = 0,
    @ColumnInfo(name = "child_id") val childId: String,
    val sender: String, // "child" hoặc "bot"
    @ColumnInfo(name = "message_content") val content: String,
    val timestamp: String
)