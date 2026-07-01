package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hazard_alerts")
data class HazardAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val city: String,
    val hazardType: String, // "Flood", "Heatwave", "Storm", "Wildfire"
    val severity: String,   // "Low", "Medium", "High", "Critical"
    val plainLanguageGuidance: String, // Plain-language what-to-do advice
    val timestamp: Long,
    val isSaved: Boolean = false
)

@Entity(tableName = "investigation_challenges")
data class InvestigationChallenge(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val location: String,
    val type: String, // "Illegal Dumping", "Deforestation", "Unauthorized Mining"
    val difficulty: String, // "Beginner", "Intermediate", "Advanced"
    val satelliteTask: String, // OSINT task: satellite analysis instruction
    val satelliteImageUrl: String, // URL/placeholder representing satellite image
    val geotagTask: String, // OSINT task: metadata/geotag check instruction
    val geotagData: String, // Latitude/longitude in metadata
    val recordsTask: String, // OSINT task: land registration/registry check
    val recordsData: String, // registry document text
    val pointsReward: Int,
    val isCompleted: Boolean = false,
    val isLocked: Boolean = false,
    val currentStep: Int = 0, // 0: Not started, 1: Sat done, 2: Geo done, 3: Records done, 4: Fully verified
    // Quiz gate: how many quizzes must be completed before this challenge can unlock.
    // Combined with the previous challenge being complete. Set to 0 for no quiz gate.
    val minQuizzesCompleted: Int = 0
)

@Entity(tableName = "reported_incidents")
data class ReportedIncident(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    // Server-assigned id; null until this incident has been successfully synced.
    // All server-bound operations (upvote, status pull-back) key off remoteId,
    // NOT the local Room id, to avoid divergence after sync (blocking issue B1).
    val remoteId: String? = null,
    val title: String,
    val type: String, // "Illegal Dumping", "Deforestation", "Unauthorized Mining", "Other"
    val location: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val reporterName: String,
    val timestamp: Long,
    val status: String, // "Submitted", "In Investigation", "Verified"
    val upvotes: Int = 0
)

@Entity(tableName = "user_activities")
data class UserActivity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val pointsEarned: Int,
    val timestamp: Long,
    val category: String // "Action", "Quiz", "Investigation", "Report"
)

@Entity(tableName = "quiz_questions")
data class QuizQuestion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswerIndex: Int, // 0 to 3
    val explanationText: String,
    val pointsReward: Int,
    val isCompleted: Boolean = false
)

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Only 1 profile row
    // Stable UUID generated client-side on first run, stored in SharedPreferences.
    // Used to identify this install on the server for profile sync and leaderboard.
    val remoteId: String? = null,
    val name: String = "Eco-Warrior",
    val city: String = "Nairobi",
    val points: Int = 0,
    val level: Int = 1,
    val badgesEarned: String = "" // Comma-separated list of earned badges
)

/** Leaderboard entry returned by GET /leaderboard — not a Room entity, just a data holder. */
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val name: String,
    val city: String,
    val points: Int,
    val level: Int,
    val isCurrentUser: Boolean = false
)
