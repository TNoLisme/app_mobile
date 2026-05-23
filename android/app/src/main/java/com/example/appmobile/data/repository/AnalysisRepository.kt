package com.example.appmobile.data.repository

import com.example.appmobile.data.local.dao.ReportDao
import com.example.appmobile.data.mapper.toDomain
import com.example.appmobile.data.mapper.toEntity
import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.data.remote.dto.ReportPayloadDto
import com.example.appmobile.data.remote.dto.ReportPreviewDataDto
import com.example.appmobile.data.remote.dto.ReportRequestDto
import com.example.appmobile.data.remote.dto.ReportRequestResponseDto
import com.example.appmobile.data.remote.dto.SendReportRequestDto
import com.example.appmobile.data.remote.dto.UserProfileDto
import com.example.appmobile.domain.model.Statistics
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class AnalysisRepository(
    private val reportDao: ReportDao?,
    private val apiService: ApiService
) {
    fun getChildStatistics(childId: String): Flow<List<Statistics>> {
        val dao = reportDao ?: return flowOf(emptyList())
        return dao.getAllProgressForChild(childId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun refreshReports(childId: String) {
        val dao = reportDao ?: return
        val response = apiService.getReports(childId)
        if (response.isSuccessful) {
            response.body()?.let { dtos ->
                dao.clearOldReports(childId)
                dao.insertReports(dtos.map { it.toEntity() })
            }
        }
    }

    suspend fun previewReport(childId: String): ReportPreviewDataDto? {
        return try {
            val response = apiService.previewReport(childId)
            if (response.isSuccessful) response.body()?.data else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getReportHistory(childId: String): List<ReportPayloadDto> {
        return try {
            val response = apiService.getReportHistory(childId)
            if (response.isSuccessful) response.body()?.data.orEmpty() else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun requestReport(childId: String, sendEmail: Boolean = false, parentEmail: String? = null): ReportPayloadDto? {
        return requestReportResponse(childId, sendEmail, parentEmail)?.data
    }

    suspend fun requestReportResponse(
        childId: String,
        sendEmail: Boolean = false,
        parentEmail: String? = null
    ): ReportRequestResponseDto? {
        return try {
            val response = apiService.requestReport(
                ReportRequestDto(
                    childUserId = childId,
                    sendEmail = sendEmail,
                    parentEmail = parentEmail
                )
            )
            if (response.isSuccessful) {
                response.body()
            } else {
                ReportRequestResponseDto(
                    status = "error",
                    message = decodeErrorMessage(response.errorBody()?.string())
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendReport(reportId: String, parentEmail: String? = null): ReportRequestResponseDto? {
        return try {
            val response = apiService.sendReport(reportId, SendReportRequestDto(parentEmail = parentEmail))
            if (response.isSuccessful) {
                response.body()
            } else {
                ReportRequestResponseDto(
                    status = "error",
                    message = decodeErrorMessage(response.errorBody()?.string())
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadReportPdf(reportId: String): ByteArray? {
        return try {
            val response = apiService.downloadReportPdf(reportId)
            if (response.isSuccessful) response.body()?.bytes() else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getUserProfile(userId: String): UserProfileDto? {
        return try {
            val lookupEmail = FirebaseAuth.getInstance().currentUser?.email
                ?.trim()
                ?.takeIf { it.isNotBlank() && "@" in it }
            val response = apiService.getUserProfile(userId, lookupEmail)
            if (response.isSuccessful) response.body() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeErrorMessage(raw: String?): String {
        val body = raw?.trim().orEmpty()
        if (body.isBlank()) return "Chưa gửi được báo cáo. Vui lòng thử lại."
        return runCatching {
            val json = JSONObject(body)
            json.optString("message")
                .ifBlank { json.optString("detail") }
                .ifBlank { "Chưa gửi được báo cáo. Vui lòng thử lại." }
        }.getOrDefault("Chưa gửi được báo cáo. Vui lòng thử lại.")
    }
}
