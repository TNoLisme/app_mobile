package com.example.appmobile.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey

@Entity(
    tableName = "children",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["user_id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class ChildEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    val age: Int?,
    val gender: String?,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: String?,
    @ColumnInfo(name = "phone_number") val phoneNumber: String?,
    @ColumnInfo(name = "report_preferences") val reportPreferences: String?
)