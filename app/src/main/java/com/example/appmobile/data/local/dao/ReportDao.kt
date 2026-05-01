package com.example.appmobile.data.local.dao

import androidx.room3.*
import com.example.appmobile.data.local.entity.ProgressEntity
import com.example.appmobile.data.local.entity.ReportEntity
import kotlinx.coroutines.flow.Flow

// child_progress, reports
@Dao
interface ReportDao {

    // --- Xử lý Tiến độ (Child Progress) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ProgressEntity)

    @Query("SELECT * FROM child_progress WHERE child_id = :childId AND game_id = :gameId")
    suspend fun getProgressByGame(childId: String, gameId: String): ProgressEntity?

    @Query("SELECT * FROM child_progress WHERE child_id = :childId")
    fun getAllProgressForChild(childId: String): Flow<List<ProgressEntity>>

    // --- Xử lý Báo cáo (Reports) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<ReportEntity>)

    @Query("SELECT * FROM reports WHERE child_id = :childId ORDER BY generated_at DESC")
    fun getReportsByChild(childId: String): Flow<List<ReportEntity>>

    @Query("DELETE FROM reports WHERE child_id = :childId")
    suspend fun clearOldReports(childId: String)
}