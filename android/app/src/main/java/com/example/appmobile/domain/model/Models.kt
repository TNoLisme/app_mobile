package com.example.appmobile.domain.model

// ==========================================================
// NHÓM 1: NGƯỜI DÙNG & HÀNH VI
// ==========================================================

data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: String
)

data class Child(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val dob: String
)

data class ChatLog(
    val id: Int,
    val sender: String,
    val message: String,
    val time: String
)

// ==========================================================
// NHÓM 2: NỘI DUNG TRÒ CHƠI
// ==========================================================

data class Game(
    val id: String,
    val name: String,
    val type: String,
    val level: Int
)

data class EmotionConcept(
    val id: String,
    val name: String,
    val video: String?,
    val image: String?,
    val desc: String
)

data class GameContent(
    val id: String,
    val text: String,
    val media: String?,
    val answer: String,
    val emotion: String
)

data class Question(
    val id: String,
    val contentId: String,
    val answer: String
)

// ==========================================================
// NHÓM 3: DỮ LIỆU PHIÊN CHƠI
// ==========================================================

data class PlaySession(
    val id: String,
    val score: Int,
    val startTime: String,
    val endTime: String?
)

data class SessionDetail(
    val id: String,
    val isCorrect: Boolean,
    val responseTime: Int,
    val confidence: Float?
)

data class GameData(
    val id: String,
    val userId: String,
    val level: Int
)

data class GameDataQuestion(
    val dataId: String,
    val questionId: String
)

// ==========================================================
// NHÓM 4: THỐNG KÊ & BÁO CÁO
// ==========================================================

data class Statistics(
    val id: String,
    val accuracy: Float,
    val score: Int,
    val lastPlayed: String
)

data class FinalReport(
    val id: String,
    val type: String,
    val summary: String,
    val date: String
)