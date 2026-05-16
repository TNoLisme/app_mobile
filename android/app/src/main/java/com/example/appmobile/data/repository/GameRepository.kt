package com.example.appmobile.data.repository

import com.example.appmobile.data.local.dao.GameContentDao
import com.example.appmobile.data.mapper.toDomain
import com.example.appmobile.data.mapper.toEntity
import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.data.remote.dto.AnswerResultDto
import com.example.appmobile.data.remote.dto.EndLevelRequestDto
import com.example.appmobile.data.remote.dto.EndLevelResponseDto
import com.example.appmobile.data.remote.dto.GameProgressDto
import com.example.appmobile.data.remote.dto.ResetReviewRequestDto
import com.example.appmobile.data.remote.dto.StartGameRequestDto
import com.example.appmobile.data.remote.dto.StartGameResponseDto
import com.example.appmobile.domain.model.EmotionConcept
import com.example.appmobile.domain.model.Game
import com.example.appmobile.domain.model.GameContent

class GameRepository(
    private val gameDao: GameContentDao,
    private val apiService: ApiService
) {
    companion object {
        private const val ProgressCacheTtlMs = 5 * 60 * 1000L
        private val progressCache = mutableMapOf<String, Pair<Long, GameProgressDto?>>()

        private fun cacheKey(gameId: String, userId: String): String = "$userId::$gameId"
    }

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
        if (local.size >= 5) return local.map { it.toDomain() }

        return try {
            val response = apiService.getQuestionsByGame(gameId, level)
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

    suspend fun startGame(gameId: String, userId: String, level: Int): StartGameResponseDto? {
        return try {
            val response = apiService.startGame(gameId, StartGameRequestDto(userId = userId, level = level))
            if (!response.isSuccessful) return null

            response.body()?.also { body ->
                gameDao.insertContents(body.questions.map { it.toEntity() })
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun endLevel(
        sessionId: String,
        results: List<AnswerResultDto>,
        reviewEmotions: List<String> = emptyList(),
        resetReviewEmotions: List<String> = emptyList()
    ): EndLevelResponseDto? {
        return try {
            val response = apiService.endLevel(
                EndLevelRequestDto(
                    sessionId = sessionId,
                    results = results,
                    reviewEmotions = reviewEmotions,
                    resetReviewEmotions = resetReviewEmotions
                )
            )
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getGameProgress(gameId: String, userId: String, forceRefresh: Boolean = false): GameProgressDto? {
        val key = cacheKey(gameId, userId)
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = progressCache[key]
            if (cached != null && now - cached.first <= ProgressCacheTtlMs) {
                return cached.second
            }
        }

        return try {
            val response = apiService.getGameProgress(gameId, userId)
            if (response.isSuccessful) {
                response.body().also { progress ->
                    progressCache[key] = System.currentTimeMillis() to progress
                }
            } else {
                progressCache[key]?.second
            }
        } catch (e: Exception) {
            progressCache[key]?.second
        }
    }

    fun peekGameProgress(gameId: String, userId: String): GameProgressDto? {
        val key = cacheKey(gameId, userId)
        val cached = progressCache[key] ?: return null
        return cached.second
    }

    suspend fun preloadGameProgress(userId: String, gameIds: List<String>) {
        gameIds.distinct().forEach { gameId ->
            getGameProgress(gameId = gameId, userId = userId, forceRefresh = false)
        }
    }

    suspend fun resetReviewEmotions(gameId: String, userId: String, emotions: List<String>): Map<String, Int> {
        if (emotions.isEmpty()) return emptyMap()
        return try {
            val response = apiService.resetReviewEmotions(
                gameId,
                ResetReviewRequestDto(userId = userId, emotions = emotions)
            )
            if (response.isSuccessful) response.body()?.reviewEmotions.orEmpty() else emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getCvEmotionScores(userId: String): Map<String, Float> {
        return try {
            val response = apiService.getCvEmotionScores(userId)
            if (response.isSuccessful) response.body()?.scores.orEmpty() else emptyMap()
        } catch (e: Exception) {
            emptyMap()
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
