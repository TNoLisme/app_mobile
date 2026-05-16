package com.example.appmobile.data.repository

import com.example.appmobile.data.local.dao.ReportDao
import com.example.appmobile.data.mapper.toDomain
import com.example.appmobile.data.mapper.toEntity
import com.example.appmobile.data.remote.api.ApiService
import com.example.appmobile.data.remote.dto.ReportPayloadDto
import com.example.appmobile.data.remote.dto.ReportPreviewDataDto
import com.example.appmobile.data.remote.dto.ReportRequestDto
import com.example.appmobile.domain.model.Statistics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AnalysisRepository(
    private val reportDao: ReportDao,
    private val apiService: ApiService
) {
    // Lấy tiến độ học tập (accuracy, score...)
    fun getChildStatistics(childId: String): Flow<List<Statistics>> {
        return reportDao.getAllProgressForChild(childId).map { list ->
            list.map { it.toDomain() }
        }
    }

    // Tải báo cáo mới nhất từ Server về
    suspend fun refreshReports(childId: String) {
        val response = apiService.getReports(childId)
        if (response.isSuccessful) {
            response.body()?.let { dtos ->
                reportDao.clearOldReports(childId)
                reportDao.insertReports(dtos.map { it.toEntity() })
            }
        }
    }

    suspend fun previewReport(childId: String): ReportPreviewDataDto? {
        return try {
            val response = apiService.previewReport(childId)
            if (response.isSuccessful) response.body()?.data else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getReportHistory(childId: String): List<ReportPayloadDto> {
        return try {
            val response = apiService.getReportHistory(childId)
            if (response.isSuccessful) response.body()?.data.orEmpty() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun requestReport(childId: String, reportType: String = "weekly"): ReportPayloadDto? {
        return try {
            val response = apiService.requestReport(ReportRequestDto(childUserId = childId, reportType = reportType))
            if (response.isSuccessful) response.body()?.data else null
        } catch (e: Exception) {
            null
        }
    }
}
