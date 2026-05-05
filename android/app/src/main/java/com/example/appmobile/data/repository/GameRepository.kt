package com.example.appmobile.data.repository

import com.example.appmobile.data.local.dao.GameContentDao
import com.example.appmobile.data.mapper.toDomain
import com.example.appmobile.data.mapper.toEntity
import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.domain.model.EmotionConcept
import com.example.appmobile.domain.model.Game
import com.example.appmobile.domain.model.GameContent

class GameRepository(
    private val gameDao: GameContentDao,
    private val apiService: ApiService
) {
    suspend fun getGames(type: String? = null): List<Game> {
        val local = if (type.isNullOrBlank()) {
            gameDao.getAllGames()
        } else {
            gameDao.getGamesByType(type)
        }
        if (local.isNotEmpty()) return local.map { it.toDomain() }

        return try {
            val response = apiService.getGames()
            if (!response.isSuccessful) return emptyList()

            val entities = (response.body() ?: emptyList()).map { it.toEntity() }
            gameDao.insertGames(entities)
            val filtered = if (type.isNullOrBlank()) entities else entities.filter { it.gameType == type }
            filtered.map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getContentForLevel(gameId: String, level: Int): List<GameContent> {
        val local = gameDao.getContentForLevel(gameId, level)
        if (local.isNotEmpty()) return local.map { it.toDomain() }

        return try {
            val response = apiService.getQuestionsByGame(gameId)
            if (!response.isSuccessful) return emptyList()

            val entities = (response.body() ?: emptyList()).map { it.toEntity() }
            gameDao.insertContents(entities)
            entities.filter { it.level == level }
                .ifEmpty { entities }
                .map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEmotionConcepts(): List<EmotionConcept> {
        val local = gameDao.getAllConcepts()
        if (local.isNotEmpty()) return local.map { it.toDomain() }

        return try {
            val response = apiService.getLearningCards()
            if (!response.isSuccessful) return emptyList()

            val entities = (response.body() ?: emptyList()).map { it.toEntity() }
            gameDao.insertConcepts(entities)
            entities.map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
