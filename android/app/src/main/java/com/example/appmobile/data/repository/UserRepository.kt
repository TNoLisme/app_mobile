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
import org.json.JSONObject
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserRepository(
    private val apiService: ApiService,
    private val firebaseAuthHelper: FirebaseAuthHelper,
    private val userDao: UserDao
) {
    private suspend fun saveProfileToLocal(profile: UserProfileDto) {
        val resolvedUserId = profile.userId?.takeIf { it.isNotBlank() } ?: return
        userDao.insertUser(
            UserEntity(
                userId = resolvedUserId,
                username = profile.username,
                email = profile.email.orEmpty(),
                role = profile.role ?: "child",
                name = profile.name,
                createdAt = profile.createdAt
            )
        )
        profile.child?.let { child ->
            userDao.insertChild(
                ChildEntity(
                    userId = resolvedUserId,
                    age = child.age,
                    gender = child.gender,
                    dateOfBirth = child.dob,
                    phoneNumber = child.phone,
                    reportPreferences = child.reportPref
                )
            )
        }
    }

    suspend fun getCachedProfile(userId: String): UserProfileDto? {
        return try {
            val localUser = userDao.getUserById(userId) ?: return null
            val localChild = userDao.getChildById(userId)
            UserProfileDto(
                userId = localUser.userId,
                username = localUser.username,
                email = localUser.email,
                role = localUser.role,
                name = localUser.name,
                createdAt = localUser.createdAt,
                child = localChild?.let { child ->
                    com.example.appmobile.data.remote.dto.ChildDto(
                        userId = child.userId,
                        age = child.age,
                        gender = child.gender,
                        dob = child.dateOfBirth,
                        phone = child.phoneNumber,
                        reportPref = child.reportPreferences
                    )
                }
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun currentAccountEmail(): String? {
        return firebaseAuthHelper.auth.currentUser?.email
            ?.trim()
            ?.takeIf { it.isNotBlank() && "@" in it }
    }

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
        firebaseAuthHelper.auth.signOut()
        return registerWithBackend(
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
                val uid = response.body()?.data?.userId ?: throw Exception("Backend không trả user_id.")
                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                runCatching {
                    userDao.insertUser(UserEntity(uid, safeUsername, email, "child", name, currentTime))
                    userDao.insertChild(ChildEntity(uid, age, gender, dateOfBirth, phoneNumber, null))
                }
                Result.success(uid)
            } else {
                Result.failure(
                    Exception(
                        extractBackendErrorMessage(
                            response = response,
                            defaultMessage = "Đăng ký thất bại. Vui lòng thử lại."
                        )
                    )
                )
            }
        } catch (e: Exception) {
            val lower = (e.message ?: "").lowercase()
            val friendly = when {
                "failed to connect" in lower ||
                    "cannot connect to backend" in lower ||
                    "unable to resolve host" in lower ||
                    "timeout" in lower -> "Không kết nối được máy chủ. Kiểm tra mạng và thử lại."
                else -> e.message ?: "Đăng ký thất bại. Vui lòng thử lại."
            }
            Result.failure(Exception(friendly))
        }
    }

    private fun extractBackendErrorMessage(
        response: Response<*>,
        defaultMessage: String
    ): String {
        val code = response.code()
        val raw = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
        val detail = runCatching {
            if (raw.isBlank()) null else JSONObject(raw).optString("detail").ifBlank { null }
        }.getOrNull()
        val detailLower = detail?.lowercase().orEmpty()

        return when {
            code == 400 && "already exists" in detailLower ->
                "Email hoặc tên đăng nhập đã tồn tại. Vui lòng dùng thông tin khác."
            code == 422 ->
                "Thông tin đăng ký chưa hợp lệ. Vui lòng kiểm tra lại các trường."
            detail != null && detail.isNotBlank() ->
                detail
            code in 500..599 ->
                "Máy chủ đang bận. Vui lòng thử lại sau."
            else -> defaultMessage
        }
    }

    suspend fun loginWithBackend(username: String, password: String): Result<UserProfileDto> {
        return try {
            val response = apiService.loginUser(BackendLoginRequestDto(username = username, password = password))
            val profile = response.body()?.user
            if (response.isSuccessful && profile?.userId != null) {
                runCatching {
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
                        userDao.insertChild(
                            ChildEntity(
                                child.userId,
                                child.age,
                                child.gender,
                                child.dob,
                                child.phone,
                                child.reportPref
                            )
                        )
                    }
                }
                Result.success(profile)
            } else {
                val message = if (response.isSuccessful) {
                    "Đăng nhập chưa hoàn tất vì thiếu thông tin hồ sơ. Vui lòng thử lại."
                } else {
                    extractBackendErrorMessage(
                        response = response,
                        defaultMessage = response.body()?.message ?: "Sai tài khoản hoặc mật khẩu."
                    )
                }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProfile(userId: String): UserProfileDto? {
        return try {
            val response = apiService.getUserProfile(userId, currentAccountEmail())
            if (response.isSuccessful) {
                response.body()?.also { saveProfileToLocal(it) } ?: getCachedProfile(userId)
            } else {
                getCachedProfile(userId)
            }
        } catch (e: Exception) {
            getCachedProfile(userId)
        }
    }

    suspend fun updateProfile(userId: String, update: UserProfileUpdateDto): UserProfileDto? {
        return try {
            val response = apiService.updateUserProfile(
                UserProfileUpdateRequestDto(
                    userId,
                    update,
                    currentAccountEmail()
                )
            )
            if (response.isSuccessful) {
                response.body()?.also { saveProfileToLocal(it) }
            } else {
                null
            }
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
