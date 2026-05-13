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

    @POST("users/register")
    suspend fun registerUser(@Body request: ChildRegisterRequestDto): Response<BackendRegisterResponseDto>

    @POST("users/login")
    suspend fun loginUser(@Body request: BackendLoginRequestDto): Response<BackendLoginResponseDto>

    @GET("children/{uid}")
    suspend fun getChildProfile(@Path("uid") uid: String): Response<ChildDto>

    @GET("users/me")
    suspend fun getUserProfile(@Query("user_id") userId: String): Response<UserProfileDto>

    @PUT("users/me")
    suspend fun updateUserProfile(@Body request: UserProfileUpdateRequestDto): Response<UserProfileDto>

    @GET("users/stats/recent-games/{userId}")
    suspend fun getRecentGames(
        @Path("userId") userId: String,
        @Query("limit") limit: Int = 4
    ): Response<RecentGamesResponseDto>

    @GET("users/stats/emotion-accuracy/{userId}")
    suspend fun getEmotionAccuracy(@Path("userId") userId: String): Response<EmotionAccuracyResponseDto>

    @GET("users/stats/emotion-improvement/{userId}")
    suspend fun getEmotionImprovement(@Path("userId") userId: String): Response<PercentMapResponseDto>

    @GET("users/stats/game-play-ratio/{userId}")
    suspend fun getGamePlayRatio(@Path("userId") userId: String): Response<PercentMapResponseDto>

    @GET("users/stats/weak-emotions/{userId}")
    suspend fun getWeakEmotions(
        @Path("userId") userId: String,
        @Query("limit") limit: Int = 3
    ): Response<WeakEmotionsResponseDto>

    @GET("sessions/user/{userId}/history")
    suspend fun getSessionHistory(
        @Path("userId") userId: String,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 1000
    ): Response<SessionHistoryResponseDto>

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

    @GET("games/cv/emotion-scores")
    suspend fun getCvEmotionScores(@Query("user_id") userId: String): Response<CvEmotionScoresResponseDto>

    @POST("sessions/save")
    suspend fun saveSession(@Body session: SessionDto): Response<Boolean>

    @POST("sessions/questions")
    suspend fun saveSessionQuestions(@Body questions: List<SessionQuestionDto>): Response<Boolean>

    // --- CHATBOT ---
    @POST("assistant/chat")
    suspend fun chatAssistant(@Body request: AssistantChatRequestDto): Response<AssistantChatResponseDto>

    @POST("chatbot/logs")
    suspend fun uploadChatLog(@Body log: ChatbotLogDto): Response<Boolean>

    // --- PROGRESS & REPORTS ---
    @GET("progress/{childId}")
    suspend fun getProgress(@Path("childId") childId: String): Response<List<ProgressDto>>

    @GET("reports/{childId}")
    suspend fun getReports(@Path("childId") childId: String): Response<List<ReportDto>>

    @GET("reports/preview/{childUserId}")
    suspend fun previewReport(
        @Path("childUserId") childUserId: String,
        @Query("report_type") reportType: String = "weekly"
    ): Response<ReportPreviewResponseDto>

    @GET("reports/history")
    suspend fun getReportHistory(
        @Query("child_user_id") childUserId: String,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 20
    ): Response<ReportHistoryResponseDto>

    @POST("reports/request-report")
    suspend fun requestReport(@Body request: ReportRequestDto): Response<ReportRequestResponseDto>
}
