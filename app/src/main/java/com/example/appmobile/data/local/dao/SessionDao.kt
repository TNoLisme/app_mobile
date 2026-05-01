package com.example.appmobile.data.local.dao

import androidx.room3.*
import com.example.appmobile.data.local.entity.SessionEntity
import com.example.appmobile.data.local.entity.SessionQuestionEntity
import kotlinx.coroutines.flow.Flow

// sessions, session_questions, game_data, game_data_question
@Dao
interface SessionDao {

    // --- Xử lý Session (Phiên chơi) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE session_id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE user_id = :userId ORDER BY start_time DESC")
    fun getSessionsByUser(userId: String): Flow<List<SessionEntity>>

    // --- Xử lý Chi tiết từng câu hỏi (Session Questions) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionQuestions(questions: List<SessionQuestionEntity>)

    @Query("SELECT * FROM session_questions WHERE session_id = :sessionId")
    suspend fun getQuestionsBySession(sessionId: String): List<SessionQuestionEntity>

    // --- Giao dịch gộp (Transaction) ---
    // Đảm bảo nếu lưu lỗi thì sẽ hủy toàn bộ, tránh dữ liệu rác
    @Transaction
    suspend fun saveCompleteSession(session: SessionEntity, questions: List<SessionQuestionEntity>) {
        insertSession(session)
        insertSessionQuestions(questions)
    }
}