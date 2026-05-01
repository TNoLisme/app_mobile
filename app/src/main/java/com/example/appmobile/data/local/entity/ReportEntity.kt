package com.example.appmobile.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey @ColumnInfo(name = "report_id") val reportId: String,
    @ColumnInfo(name = "child_id") val childId: String,
    @ColumnInfo(name = "report_type") val reportType: String,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
    val summary: String,
    val data: String
)