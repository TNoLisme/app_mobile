package com.example.appmobile.data.repository

import com.example.appmobile.data.local.dao.ReportDao
import com.example.appmobile.data.mapper.toDomain
import com.example.appmobile.data.mapper.toEntity
import com.example.appmobile.data.remote.api.ApiService
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
}