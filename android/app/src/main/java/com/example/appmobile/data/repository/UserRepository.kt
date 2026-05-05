package com.example.appmobile.data.repository

import com.example.appmobile.data.local.dao.UserDao
import com.example.appmobile.data.local.entity.ChildEntity
import com.example.appmobile.data.local.entity.UserEntity
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.data.remote.dto.EmotionAccuracyDto
import com.example.appmobile.data.remote.dto.RecentGameDto
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateRequestDto
import com.example.appmobile.data.remote.dto.WeakEmotionDto
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class UserRepository(
    private val apiService: ApiService,
    private val firebaseAuthHelper: FirebaseAuthHelper,
    private val userDao: UserDao // Thêm UserDao để lưu local
) {
    suspend fun registerNewAccount(
        email: String,
        pass: String,
        name: String,
        age: Int,
        gender: String
    ): Result<String> {
        return try {
            // 1. Tạo tài khoản trên Firebase
            val authResult = firebaseAuthHelper.auth.createUserWithEmailAndPassword(email, pass).await()
            val uid = authResult.user?.uid ?: throw Exception("Không lấy được Firebase UID")

            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // 2. Đóng gói dữ liệu cho cả bảng User và Child
            val syncData = mapOf(
                "user_id" to uid,
                "email" to email,
                "name" to name,
                "role" to "child",
                "created_at" to currentTime,
                "age" to age.toString(),
                "gender" to gender,
                "date_of_birth" to "", // Có thể bổ sung thêm field nếu cần
                "phone_number" to ""
            )

            // 3. Gửi sang Backend FastAPI
            val response = apiService.registerUserSync(syncData)

            if (response.isSuccessful) {
                // 4. Nếu thành công, lưu vào Local DB (Room) để dùng offline
                userDao.insertUser(UserEntity(uid, null, email, "child", name, currentTime))
                userDao.insertChild(ChildEntity(uid, age, gender, null, null, null))

                Result.success(uid)
            } else {
                Result.failure(Exception("Lỗi đồng bộ CSDL SQL Server: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProfile(userId: String): UserProfileDto? {
        return try {
            val response = apiService.getUserProfile(userId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateProfile(userId: String, update: UserProfileUpdateDto): UserProfileDto? {
        return try {
            val response = apiService.updateUserProfile(UserProfileUpdateRequestDto(userId, update))
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getRecentGames(userId: String): List<RecentGameDto> {
        return try {
            val response = apiService.getRecentGames(userId)
            if (response.isSuccessful) response.body()?.data.orEmpty() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEmotionAccuracy(userId: String): Map<String, EmotionAccuracyDto> {
        return try {
            val response = apiService.getEmotionAccuracy(userId)
            if (response.isSuccessful) response.body()?.data.orEmpty() else emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getWeakEmotions(userId: String): List<WeakEmotionDto> {
        return try {
            val response = apiService.getWeakEmotions(userId)
            if (response.isSuccessful) response.body()?.data.orEmpty() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
