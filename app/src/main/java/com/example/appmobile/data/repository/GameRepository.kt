package com.example.appmobile.data.repository

import com.example.appmobile.data.local.dao.GameContentDao
import com.example.appmobile.data.mapper.toDomain
import com.example.appmobile.data.mapper.toEntity
import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.domain.model.Game
import com.example.appmobile.domain.model.EmotionConcept

class GameRepository(
    private val gameDao: GameContentDao,
    private val apiService: ApiService
) {
    // Tải thẻ học cảm xúc (Emotion Concepts)
    suspend fun getEmotionConcepts(): List<EmotionConcept> {
        // 1. Kiểm tra local trước
        val local = gameDao.getAllConcepts()
        if (local.isNotEmpty()) return local.map { it.toDomain() }

        // 2. Nếu local trống thì gọi API
        return try {
            val response = apiService.getLearningCards()
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                // Lưu vào local để dùng offline lần sau
                // gameDao.insertConcepts(dtos.map { it.toEntity() })
                dtos.map { it.toEntity().toDomain() }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}