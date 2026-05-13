package com.example.appmobile.data.repository

import com.example.appmobile.data.local.dao.UserDao
import com.example.appmobile.data.local.entity.ChildEntity
import com.example.appmobile.data.local.entity.UserEntity
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.data.remote.dto.BackendLoginRequestDto
import com.example.appmobile.data.remote.dto.ChildRegisterRequestDto
import com.example.appmobile.data.remote.dto.CvEmotionScoresResponseDto
import com.example.appmobile.data.remote.dto.EmotionAccuracyDto
import com.example.appmobile.data.remote.dto.RecentGameDto
import com.example.appmobile.data.remote.dto.ReportPayloadDto
import com.example.appmobile.data.remote.dto.ReportRequestDto
import com.example.appmobile.data.remote.dto.SessionHistoryItemDto
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateDto
import com.example.appmobile.data.remote.dto.UserProfileUpdateRequestDto
import com.example.appmobile.data.remote.dto.WeakEmotionDto
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserRepository(
    private val apiService: ApiService,
    private val firebaseAuthHelper: FirebaseAuthHelper,
    private val userDao: UserDao
) {
    suspend fun registerNewAccount(
        email: String,
        pass: String,
        name: String,
        age: Int,
        gender: String,
        username: String? = null,
        dateOfBirth: String? = null,
        phoneNumber: String? = null
    ): Result<String> {
        return try {
            val authResult = firebaseAuthHelper.auth.createUserWithEmailAndPassword(email, pass).await()
            val uid = authResult.user?.uid ?: throw Exception("Không lấy được Firebase UID")

            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val safeUsername = username?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")

            val syncData = mapOf(
                "user_id" to uid,
                "username" to safeUsername,
                "email" to email,
                "name" to name,
                "role" to "child",
                "created_at" to currentTime,
                "age" to age.toString(),
                "gender" to gender,
                "date_of_birth" to dateOfBirth.orEmpty(),
                "phone_number" to phoneNumber.orEmpty()
            )

            val response = apiService.registerUserSync(syncData)

            if (response.isSuccessful) {
                userDao.insertUser(UserEntity(uid, safeUsername, email, "child", name, currentTime))
                userDao.insertChild(ChildEntity(uid, age, gender, dateOfBirth, phoneNumber, null))
                Result.success(uid)
            } else {
                Result.failure(Exception("Lỗi đồng bộ SQL Server: ${response.code()}"))
            }
        } catch (e: Exception) {
            registerWithBackend(
                email = email,
                pass = pass,
                name = name,
                age = age,
                gender = gender,
                username = username,
                dateOfBirth = dateOfBirth,
                phoneNumber = phoneNumber
            )
        }
    }

    private suspend fun registerWithBackend(
        email: String,
        pass: String,
        name: String,
        age: Int,
        gender: String,
        username: String? = null,
        dateOfBirth: String? = null,
        phoneNumber: String? = null
    ): Result<String> {
        return try {
            val safeUsername = username?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
            val response = apiService.registerUser(
                ChildRegisterRequestDto(
                    username = safeUsername,
                    email = email,
                    password = pass,
                    name = name,
                    age = age,
                    gender = gender,
                    dateOfBirth = dateOfBirth,
                    phoneNumber = phoneNumber
                )
            )
            if (response.isSuccessful) {
                val uid = response.body()?.data?.userId ?: throw Exception("Backend không trả user_id")
                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                userDao.insertUser(UserEntity(uid, safeUsername, email, "child", name, currentTime))
                userDao.insertChild(ChildEntity(uid, age, gender, dateOfBirth, phoneNumber, null))
                Result.success(uid)
            } else {
                Result.failure(Exception("Đăng ký backend thất bại: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithBackend(username: String, password: String): Result<UserProfileDto> {
        return try {
            val response = apiService.loginUser(BackendLoginRequestDto(username = username, password = password))
            val profile = response.body()?.user
            if (response.isSuccessful && profile?.userId != null) {
                val child = profile.child
                userDao.insertUser(
                    UserEntity(
                        profile.userId,
                        profile.username,
                        profile.email.orEmpty(),
                        profile.role ?: "child",
                        profile.name,
                        profile.createdAt
                    )
                )
                if (child != null) {
                    userDao.insertChild(ChildEntity(child.userId, child.age, child.gender, child.dob, child.phone, child.reportPref))
                }
                Result.success(profile)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Sai tài khoản hoặc mật khẩu."))
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

    suspend fun getSessionHistory(userId: String): List<SessionHistoryItemDto> {
        return try {
            val response = apiService.getSessionHistory(userId)
            if (response.isSuccessful) response.body()?.sessions.orEmpty() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCvEmotionScores(userId: String): CvEmotionScoresResponseDto? {
        return try {
            val response = apiService.getCvEmotionScores(userId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun requestReport(userId: String, reportType: String): ReportPayloadDto? {
        return try {
            val response = apiService.requestReport(ReportRequestDto(childUserId = userId, reportType = reportType))
            if (response.isSuccessful) response.body()?.data else null
        } catch (e: Exception) {
            null
        }
    }
}
