package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- Hazard Alerts ---
    @Query("SELECT * FROM hazard_alerts ORDER BY timestamp DESC")
    fun getAllHazardAlerts(): Flow<List<HazardAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHazardAlerts(alerts: List<HazardAlert>)

    @Update
    suspend fun updateHazardAlert(alert: HazardAlert)

    // --- Investigation Challenges ---
    @Query("SELECT * FROM investigation_challenges ORDER BY id ASC")
    fun getAllChallenges(): Flow<List<InvestigationChallenge>>

    @Query("SELECT * FROM investigation_challenges WHERE id = :id")
    suspend fun getChallengeById(id: Int): InvestigationChallenge?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenges(challenges: List<InvestigationChallenge>)

    @Update
    suspend fun updateChallenge(challenge: InvestigationChallenge)

    // --- Reported Incidents (Live Map) ---
    @Query("SELECT * FROM reported_incidents ORDER BY timestamp DESC")
    fun getAllReportedIncidents(): Flow<List<ReportedIncident>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReportedIncident(incident: ReportedIncident)

    @Update
    suspend fun updateReportedIncident(incident: ReportedIncident)

    // --- User Activities (Points/Action Log) ---
    @Query("SELECT * FROM user_activities ORDER BY timestamp DESC")
    fun getAllActivities(): Flow<List<UserActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: UserActivity)

    // --- Quiz Questions ---
    @Query("SELECT * FROM quiz_questions ORDER BY id ASC")
    fun getAllQuizQuestions(): Flow<List<QuizQuestion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestions(questions: List<QuizQuestion>)

    @Update
    suspend fun updateQuizQuestion(question: QuizQuestion)

    // --- User Profile ---
    @Query("SELECT * FROM user_profiles WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE id = 1")
    suspend fun getUserProfileSync(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Update
    suspend fun updateUserProfile(profile: UserProfile)
}
