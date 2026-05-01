package com.example.appmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.appmobile.data.local.entity.EmotionConceptEntity
import com.example.appmobile.data.local.entity.GameContentEntity
import com.example.appmobile.data.local.entity.GameEntity

// Games, Concepts, Content, Questions
@Dao
interface GameContentDao {
    @Query("SELECT * FROM games")
    suspend fun getAllGames(): List<GameEntity>

    @Query("SELECT * FROM game_content WHERE game_id = :gameId AND level = :level")
    suspend fun getContentForLevel(gameId: String, level: Int): List<GameContentEntity>

    @Query("SELECT * FROM emotion_concepts")
    suspend fun getAllConcepts(): List<EmotionConceptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConcept(concept: EmotionConceptEntity)
}