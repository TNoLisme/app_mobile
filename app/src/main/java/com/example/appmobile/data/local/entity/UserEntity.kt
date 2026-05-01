package com.example.appmobile.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String, // Firebase UID
    val username: String?,
    val email: String,
    val role: String = "child",
    val name: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?
)