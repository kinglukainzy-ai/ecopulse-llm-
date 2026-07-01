package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AppDao
import com.example.data.model.*

@Database(
    entities = [
        HazardAlert::class,
        InvestigationChallenge::class,
        ReportedIncident::class,
        UserActivity::class,
        QuizQuestion::class,
        UserProfile::class
    ],
    // v2: added remoteId to ReportedIncident + UserProfile (server sync),
    //     minQuizzesCompleted to InvestigationChallenge (quiz-gate).
    //     fallbackToDestructiveMigration() handles upgrade — no real data pre-launch.
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ecopulse_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
