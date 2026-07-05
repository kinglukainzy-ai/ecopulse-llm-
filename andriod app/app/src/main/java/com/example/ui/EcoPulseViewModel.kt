package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.data.repository.ClimateApiService
import com.example.data.repository.EcoPulseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EcoPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EcoPulseRepository

    // UI state flows
    val hazardAlerts: StateFlow<List<HazardAlert>>
    val challenges: StateFlow<List<InvestigationChallenge>>
    val reportedIncidents: StateFlow<List<ReportedIncident>>
    val activities: StateFlow<List<UserActivity>>
    val quizQuestions: StateFlow<List<QuizQuestion>>
    val userProfile: StateFlow<UserProfile?>

    // Selected navigation tab: "feed", "map", "investigate", "learn"
    private val _currentTab = MutableStateFlow("feed")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Leaderboard state flows (Task 4)
    private val _leaderboard = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardEntry>> = _leaderboard.asStateFlow()

    private val _isLeaderboardLoading = MutableStateFlow(false)
    val isLeaderboardLoading: StateFlow<Boolean> = _isLeaderboardLoading.asStateFlow()

    private val _leaderboardStale = MutableStateFlow(false)
    val leaderboardStale: StateFlow<Boolean> = _leaderboardStale.asStateFlow()

    // Current active OSINT challenge being detailed (null if none selected)
    private val _selectedChallenge = MutableStateFlow<InvestigationChallenge?>(null)
    val selectedChallenge: StateFlow<InvestigationChallenge?> = _selectedChallenge.asStateFlow()

    // Interactive AI chat guidance states
    private val _aiGuideResponse = MutableStateFlow<String>(
        "Habari! I am Eco, your EcoPulse AI Climate Guide. I explain complex environmental " +
        "alerts in local terms and guide you through each OSINT investigation step. " +
        "Tap an alert or ask a question to begin."
    )
    val aiGuideResponse: StateFlow<String> = _aiGuideResponse.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // Server status indicator (shown in the AI Guide card)
    private val _aiServerOnline = MutableStateFlow<Boolean?>(null) // null = unknown
    val aiServerOnline: StateFlow<Boolean?> = _aiServerOnline.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = EcoPulseRepository(database.appDao())

        // Collect and expose states
        hazardAlerts = repository.hazardAlerts.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        challenges = repository.challenges.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        reportedIncidents = repository.reportedIncidents.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        activities = repository.activities.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        quizQuestions = repository.quizQuestions.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        userProfile = repository.userProfile.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), null
        )

        // Seed initial DB records
        viewModelScope.launch {
            repository.initializeDatabaseIfEmpty()
            // Sync incidents on startup (Task 1)
            syncIncidents()
            // Fetch initial leaderboard (Task 4)
            refreshLeaderboard(cityOnly = true)
        }

        // Check bot server health in background on startup
        viewModelScope.launch {
            val health = ClimateApiService.health()
            _aiServerOnline.value = health.ok
            Log.i("EcoPulseVM", "Bot server health: ok=${health.ok} ollama=${health.ollamaReachable}")
        }
    }

    fun selectTab(tab: String) {
        _currentTab.value = tab
        if (tab != "investigate") {
            _selectedChallenge.value = null
        }
        if (tab == "map") {
            syncIncidents() // Pull latest incidents from server when map tab opens (Task 1)
        }
    }

    fun syncIncidents() {
        viewModelScope.launch {
            repository.syncIncidentsFromRemote()
        }
    }

    fun refreshLeaderboard(cityOnly: Boolean) {
        viewModelScope.launch {
            _isLeaderboardLoading.value = true
            val city = if (cityOnly) userProfile.value?.city.orEmpty() else ""
            val remoteLeaderboard = ClimateApiService.fetchLeaderboard(city)
            _isLeaderboardLoading.value = false
            if (remoteLeaderboard != null) {
                val myRemoteId = userProfile.value?.remoteId
                var mapped = remoteLeaderboard.map { entry ->
                    entry.copy(isCurrentUser = myRemoteId != null && entry.userId == myRemoteId)
                }
                
                // Highlight current user even if they are outside top N
                val alreadyInList = mapped.any { it.isCurrentUser }
                if (!alreadyInList && myRemoteId != null) {
                    val curProfile = userProfile.value
                    if (curProfile != null) {
                        mapped = mapped + LeaderboardEntry(
                            rank = mapped.size + 1, // display as 20+ equivalent
                            userId = myRemoteId,
                            name = curProfile.name,
                            city = curProfile.city,
                            points = curProfile.points,
                            level = curProfile.level,
                            isCurrentUser = true
                        )
                    }
                }
                _leaderboard.value = mapped
                _leaderboardStale.value = false
            } else {
                _leaderboardStale.value = true // server unreachable -> show cached last-fetched leaderboard with a stale indicator
            }
        }
    }

    fun selectChallenge(challenge: InvestigationChallenge?) {
        _selectedChallenge.value = challenge
    }

    // --- Interactive Operations ---

    fun upvoteIncident(incidentId: Int) {
        viewModelScope.launch {
            repository.upvoteIncident(incidentId)
        }
    }

    fun submitReport(title: String, type: String, location: String, description: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            repository.submitIncidentReport(
                title = title,
                type = type,
                location = location,
                description = description,
                lat = lat,
                lng = lng,
                reporter = userProfile.value?.name ?: "Eco-Warrior"
            )
        }
    }

    fun advanceChallengeStep(challengeId: Int) {
        viewModelScope.launch {
            val updated = repository.advanceChallengeStep(challengeId)
            _selectedChallenge.value = updated

            // Ask AI Guide to narrate the next step via the bot server
            if (updated != null) {
                val stepPrompt = buildStepPrompt(updated)
                askAiGuide(stepPrompt)
            }
        }
    }

    fun submitQuizAnswer(questionId: Int, isCorrect: Boolean, reward: Int) {
        viewModelScope.launch {
            repository.submitQuizAnswer(questionId, isCorrect, reward)
        }
    }

    fun logDailyAction(actionName: String, points: Int) {
        viewModelScope.launch {
            repository.logDailyClimateAction(actionName, points)
        }
    }

    fun readAlert(alertId: Int) {
        viewModelScope.launch {
            repository.readAlert(alertId)

            // Ask the real AI bot to explain the alert
            val alert = hazardAlerts.value.find { it.id == alertId }
            if (alert != null) {
                val prompt = "Give me a brief, plain-language explanation of this alert: " +
                        "${alert.title} in ${alert.city}. Severity: ${alert.severity}. " +
                        "Hazard type: ${alert.hazardType}. What should residents do?"
                askAiGuide(prompt)
            }
        }
    }

    /**
     * Sends a question to the EcoPulse bot server (bot/server.py → climate_agent.ask()).
     * Falls back gracefully to keyword-based replies if the server is offline.
     */
    fun askAiGuide(query: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiGuideResponse.value = "Eco is thinking..."

            val result = ClimateApiService.ask(text = query, userId = getUserId())

            _isAiLoading.value = false
            _aiGuideResponse.value = result.reply

            // Update server status based on whether we got a real reply
            _aiServerOnline.value = result.error == null
        }
    }

    /**
     * Fetch a real-time weather alert from the bot server's Open-Meteo integration.
     */
    fun askWeatherAlert(location: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiGuideResponse.value = "Fetching live weather data for $location..."

            val alert = ClimateApiService.getWeatherAlert(location)
            _isAiLoading.value = false
            _aiGuideResponse.value = alert
        }
    }

    // Tracks whether the first live location-based alert fetch has run,
    // so the UI knows to show a loading/empty state vs. a real fetch failure.
    private val _hasFetchedLiveAlert = MutableStateFlow(false)
    val hasFetchedLiveAlert: StateFlow<Boolean> = _hasFetchedLiveAlert.asStateFlow()

    /**
     * Fetches a real, live hazard alert for the device's current coordinates
     * from the bot server (Open-Meteo backed) and replaces whatever is in the
     * hazard_alerts table with it. Called from the UI layer once location
     * permission is granted and a fix is available - see the permission +
     * FusedLocationProviderClient wiring in EcoPulseApp.kt / MainActivity.kt.
     */
    fun fetchLiveLocationAlert(lat: Double, lon: Double, cityLabel: String? = null) {
        viewModelScope.launch {
            val locationQuery = "$lat,$lon"
            val alertText = ClimateApiService.getWeatherAlert(locationQuery)
            repository.refreshLiveAlert(cityLabel ?: locationQuery, alertText)
            _hasFetchedLiveAlert.value = true
        }
    }

    /**
     * Clears the conversation history on the bot server for this user.
     */
    fun clearAiHistory() {
        viewModelScope.launch {
            ClimateApiService.clearHistory(getUserId())
            _aiGuideResponse.value = "Conversation cleared. Habari! Ask me anything about climate hazards or OSINT investigations."
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /** Uses a stable user id based on the profile (falls back to 0 for anonymous). */
    private fun getUserId(): Int = userProfile.value?.id ?: 0

    /**
     * Builds a natural-language prompt for the AI to narrate an OSINT step.
     * The actual wording comes from the AI, not hardcoded strings.
     */
    private fun buildStepPrompt(challenge: InvestigationChallenge): String {
        return when (challenge.currentStep) {
            0 -> "I'm starting an OSINT environmental audit called '${challenge.title}'. " +
                    "Brief me on what satellite imagery analysis involves and what to look for."
            1 -> "I just completed satellite imagery analysis for '${challenge.title}'. " +
                    "I found suspicious clearing. Now guide me on EXIF metadata verification of the photos."
            2 -> "I verified the EXIF geotags match the clearing location in '${challenge.title}'. " +
                    "Now guide me on checking public land registry concessions records."
            3 -> "Registry check is complete - no valid concessions found for '${challenge.title}'. " +
                    "Summarize what I've proven and explain the impact of submitting this audit."
            4 -> "I've published the verified environmental audit for '${challenge.title}'. " +
                    "Congratulate me and explain what happens next with the audit findings."
            else -> "Guide me through the next step of the OSINT investigation '${challenge.title}'."
        }
    }
}
