package com.example.appmobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.appmobile.data.local.dao.*
import com.example.appmobile.data.local.entity.*

@Database(
    entities = [
        UserEntity::class, ChildEntity::class, ChatbotLogEntity::class,
        GameEntity::class, EmotionConceptEntity::class, GameContentEntity::class, QuestionEntity::class,
        SessionEntity::class, SessionQuestionEntity::class, GameDataEntity::class, GameDataQuestionEntity::class,
        ProgressEntity::class, ReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun gameContentDao(): GameContentDao
    abstract fun sessionDao(): SessionDao
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "Mobile"
                )
                    .fallbackToDestructiveMigration() // Tự động xóa data cũ nếu bạn thay đổi cấu trúc bảng
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}