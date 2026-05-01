package com.example.appmobile.data.local.dao

import androidx.room3.*
import com.example.appmobile.data.local.entity.ChatbotLogEntity
import com.example.appmobile.data.local.entity.ChildEntity
import com.example.appmobile.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

// User, Child, Chatbot
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChild(child: ChildEntity)

    @Query("SELECT * FROM users WHERE user_id = :uid")
    suspend fun getUserById(uid: String): UserEntity?

    @Insert
    suspend fun insertChatLog(log: ChatbotLogEntity)

    @Query("SELECT * FROM chatbot_logs WHERE child_id = :childId ORDER BY timestamp DESC")
    fun getChatLogs(childId: String): Flow<List<ChatbotLogEntity>>

    @Query("SELECT * FROM users WHERE user_id = :uid")
    fun getUserByIdFlow(uid: String): Flow<UserEntity?> // Thêm hàm này
}