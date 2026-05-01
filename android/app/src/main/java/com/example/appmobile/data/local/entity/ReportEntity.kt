package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey @ColumnInfo(name = "report_id") val reportId: String,
    @ColumnInfo(name = "child_id") val childId: String,
    @ColumnInfo(name = "report_type") val reportType: String,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
    val summary: String,
    val data: String
)