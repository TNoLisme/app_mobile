package com.example.appmobile.data.mapper

import com.example.appmobile.data.local.entity.*
import com.example.appmobile.data.remote.dto.*
import com.example.appmobile.domain.model.*

// ==========================================================
// NHÓM 1: AUTH & PROFILE
// ==========================================================

fun UserDto.toEntity(): UserEntity {
    return UserEntity(
        userId = userId,
        username = username,
        email = email,
        role = role,
        name = name,
        createdAt = createdAt
    )
}

fun UserEntity.toDomain(): User {
    return User(
        id = userId,
        name = name ?: "User",
        email = email,
        role = role
    )
}

fun ChildDto.toEntity(): ChildEntity {
    return ChildEntity(
        userId = userId,
        age = age,
        gender = gender,
        dateOfBirth = dob,
        phoneNumber = phone,
        reportPreferences = reportPref
    )
}

fun ChildEntity.toDomain(name: String): Child {
    return Child(
        id = userId,
        name = name,
        age = age ?: 0,
        gender = gender ?: "other",
        dob = dateOfBirth ?: ""
    )
}

fun ChatbotLogDto.toEntity(): ChatbotLogEntity {
    return ChatbotLogEntity(
        logId = logId,
        childId = childId,
        sender = sender,
        content = content,
        timestamp = timestamp
    )
}

fun ChatbotLogEntity.toDomain(): ChatLog {
    return ChatLog(
        id = logId,
        sender = sender,
        message = content,
        time = timestamp
    )
}

// ==========================================================
// NHÓM 2: GAME CONTENT
// ==========================================================

fun GameDto.toEntity(): GameEntity {
    return GameEntity(
        gameId = gameId,
        gameType = gameType,
        name = name,
        level = level,
        difficultyLevel = "Normal",
        maxErrors = maxErrors,
        timeLimit = timeLimit
    )
}

fun GameEntity.toDomain(): Game {
    return Game(
        id = gameId,
        name = name,
        type = gameType,
        level = level
    )
}

fun EmotionConceptDto.toEntity(): EmotionConceptEntity {
    return EmotionConceptEntity(
        conceptId = conceptId,
        emotion = emotion,
        level = 1,
        title = title,
        videoPath = videoPath,
        imagePath = imagePath,
        audioPath = null,
        description = description
    )
}

fun EmotionConceptEntity.toDomain(): EmotionConcept {
    return EmotionConcept(
        id = emotion.ifBlank { conceptId },
        name = title,
        video = videoPath,
        image = imagePath,
        desc = description ?: ""
    )
}

fun GameContentDto.toEntity(): GameContentEntity {
    return GameContentEntity(
        contentId = contentId,
        gameId = gameId,
        level = level ?: 1,
        contentType = contentType ?: "image",
        mediaPath = mediaPath,
        questionText = questionText,
        correctAnswer = correctAnswer,
        emotion = emotion,
        explanation = explanation
    )
}

fun GameContentEntity.toDomain(): GameContent {
    return GameContent(
        id = contentId,
        text = questionText ?: "",
        media = mediaPath,
        answer = correctAnswer ?: "",
        emotion = emotion ?: ""
    )
}

fun QuestionDto.toEntity(): QuestionEntity {
    return QuestionEntity(
        questionId = questionId,
        gameId = "UNKNOWN",
        contentId = contentId,
        level = 1,
        correctAnswer = correctAnswer
    )
}

fun QuestionEntity.toDomain(): Question {
    return Question(
        id = questionId,
        contentId = contentId,
        answer = correctAnswer
    )
}

// ==========================================================
// NHÓM 3: SESSIONS & LOGIC
// ==========================================================

fun SessionDto.toEntity(): SessionEntity {
    return SessionEntity(
        sessionId = sessionId,
        userId = userId,
        gameId = "UNKNOWN",
        startTime = startTime,
        endTime = null,
        score = score,
        level = 1,
        emotionErrors = emotionErrors
    )
}

fun SessionEntity.toDomain(): PlaySession {
    return PlaySession(
        id = sessionId,
        score = score,
        startTime = startTime,
        endTime = endTime
    )
}

fun SessionQuestionDto.toEntity(): SessionQuestionEntity {
    return SessionQuestionEntity(
        id = id,
        sessionId = sessionId,
        questionId = questionId ?: "UNKNOWN",
        userAnswer = null,
        isCorrect = isCorrect,
        responseTimeMs = responseTime,
        cvConfidence = confidence
    )
}

fun SessionQuestionEntity.toDomain(): SessionDetail {
    return SessionDetail(
        id = id,
        isCorrect = isCorrect,
        responseTime = responseTimeMs,
        confidence = cvConfidence
    )
}

fun GameDataDto.toEntity(): GameDataEntity {
    return GameDataEntity(
        dataId = dataId,
        gameId = "UNKNOWN",
        userId = userId,
        level = level
    )
}

fun GameDataEntity.toDomain(): GameData {
    return GameData(
        id = dataId,
        userId = userId,
        level = level
    )
}

fun GameDataQuestionDto.toEntity(): GameDataQuestionEntity {
    return GameDataQuestionEntity(
        dataId = dataId,
        questionId = questionId
    )
}

fun GameDataQuestionEntity.toDomain(): GameDataQuestion {
    return GameDataQuestion(
        dataId = dataId,
        questionId = questionId
    )
}

// ==========================================================
// NHÓM 4: ANALYSIS
// ==========================================================

fun ProgressDto.toEntity(): ProgressEntity {
    return ProgressEntity(
        progressId = progressId,
        childId = childId,
        gameId = "UNKNOWN",
        level = 1,
        accuracy = accuracy,
        score = score,
        lastPlayed = lastPlayed
    )
}

fun ProgressEntity.toDomain(): Statistics {
    return Statistics(
        id = progressId,
        accuracy = accuracy,
        score = score,
        lastPlayed = lastPlayed
    )
}

fun ReportDto.toEntity(): ReportEntity {
    return ReportEntity(
        reportId = reportId,
        childId = "UNKNOWN",
        reportType = type,
        generatedAt = "NOW",
        summary = summary,
        data = jsonData
    )
}

fun ReportEntity.toDomain(): FinalReport {
    return FinalReport(
        id = reportId,
        type = reportType,
        summary = summary,
        date = generatedAt
    )
}
