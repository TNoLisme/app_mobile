package com.example.appmobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

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