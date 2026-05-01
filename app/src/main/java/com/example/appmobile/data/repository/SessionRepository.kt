package com.example.appmobile.data.repository

import com.example.appmobile.data.local.dao.SessionDao
import com.example.appmobile.data.mapper.toEntity
import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.data.remote.dto.SessionDto
import com.example.appmobile.data.remote.dto.SessionQuestionDto

class SessionRepository(
    private val sessionDao: SessionDao,
    private val apiService: ApiService
) {
    // Lưu kết quả phiên chơi: Lưu Local trước, sau đó đẩy lên SQL Server
    suspend fun saveGameResult(session: SessionDto, questions: List<SessionQuestionDto>) {
        // 1. Lưu Local (SQLite)
        sessionDao.saveCompleteSession(session.toEntity(), questions.map { it.toEntity() })

        // 2. Đẩy lên SQL Server (Remote)
        try {
            apiService.saveSession(session)
            apiService.saveSessionQuestions(questions)
        } catch (e: Exception) {
            // Nếu lỗi mạng, dữ liệu vẫn nằm ở Local, ta có thể sync lại sau
        }
    }
}