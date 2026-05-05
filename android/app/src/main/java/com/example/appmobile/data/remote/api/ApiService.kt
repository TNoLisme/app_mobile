package com.example.appmobile.data.remote.api

import com.example.appmobile.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // --- AUTH & PROFILE ---
    @POST("users/sync")
    suspend fun syncUser(@Body user: UserDto): Response<UserDto>

    @POST("users/register-sync")
    suspend fun registerUserSync(@Body userData: Map<String, String>): Response<Unit>

    @GET("children/{uid}")
    suspend fun getChildProfile(@Path("uid") uid: String): Response<ChildDto>

    // --- GAMES ---
    @GET("games")
    suspend fun getGames(): Response<List<GameDto>>

    @GET("emotion-concepts")
    suspend fun getLearningCards(): Response<List<EmotionConceptDto>>

    @GET("game-content/{gameId}")
    suspend fun getQuestionsByGame(
        @Path("gameId") gameId: String,
        @Query("level") level: Int? = null
    ): Response<List<GameContentDto>>

    // --- SESSIONS ---
    @POST("games/start/{gameId}")
    suspend fun startGame(
        @Path("gameId") gameId: String,
        @Body request: StartGameRequestDto
    ): Response<StartGameResponseDto>

    @POST("games/end-level")
    suspend fun endLevel(@Body request: EndLevelRequestDto): Response<EndLevelResponseDto>

    @GET("games/progress/{gameId}")
    suspend fun getGameProgress(
        @Path("gameId") gameId: String,
        @Query("user_id") userId: String
    ): Response<GameProgressDto?>

    @POST("sessions/save")
    suspend fun saveSession(@Body session: SessionDto): Response<Boolean>

    @POST("sessions/questions")
    suspend fun saveSessionQuestions(@Body questions: List<SessionQuestionDto>): Response<Boolean>

    // --- CHATBOT ---
    @POST("chatbot/logs")
    suspend fun uploadChatLog(@Body log: ChatbotLogDto): Response<Boolean>

    // --- PROGRESS & REPORTS ---
    @GET("progress/{childId}")
    suspend fun getProgress(@Path("childId") childId: String): Response<List<ProgressDto>>

    @GET("reports/{childId}")
    suspend fun getReports(@Path("childId") childId: String): Response<List<ReportDto>>
}
