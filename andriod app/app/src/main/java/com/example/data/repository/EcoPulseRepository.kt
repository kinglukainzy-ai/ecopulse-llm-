package com.example.data.repository

import com.example.data.dao.AppDao
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class EcoPulseRepository(private val appDao: AppDao) {

    // Expose database states
    val hazardAlerts: Flow<List<HazardAlert>> = appDao.getAllHazardAlerts()
    val challenges: Flow<List<InvestigationChallenge>> = appDao.getAllChallenges()
    val reportedIncidents: Flow<List<ReportedIncident>> = appDao.getAllReportedIncidents()
    val activities: Flow<List<UserActivity>> = appDao.getAllActivities()
    val quizQuestions: Flow<List<QuizQuestion>> = appDao.getAllQuizQuestions()
    val userProfile: Flow<UserProfile?> = appDao.getUserProfile()

    // Initialize default mock data if empty
    suspend fun initializeDatabaseIfEmpty() {
        // 1. Initialize User Profile if not existing
        val profile = appDao.getUserProfileSync()
        if (profile == null) {
            appDao.insertUserProfile(
                UserProfile(
                    id = 1,
                    remoteId = java.util.UUID.randomUUID().toString(),
                    name = "Munashe Mwangi",
                    city = "Nairobi",
                    points = 120, // Start with some initial points
                    level = 1,
                    badgesEarned = "First Alert Aware"
                )
            )
        } else if (profile.remoteId == null) {
            appDao.updateUserProfile(profile.copy(remoteId = java.util.UUID.randomUUID().toString()))
        }

        // 2. Initialize Hazard Alerts
        val currentAlerts = appDao.getAllHazardAlerts().first()
        if (currentAlerts.isEmpty()) {
            appDao.insertHazardAlerts(
                listOf(
                    HazardAlert(
                        title = "High Risk Flood Alert - Nairobi River Basin",
                        city = "Nairobi",
                        hazardType = "Flood",
                        severity = "Critical",
                        plainLanguageGuidance = "• Elevate critical electrical and household appliances. \n• Avoid crossing swollen seasonal streams, especially on foot or lightweight boda-bodas.\n• Keep emergency contacts handy and pack essential items in waterproof bags.\n• Stand by for evacuation alerts from community leaders.",
                        timestamp = System.currentTimeMillis() - 3600000 // 1 hour ago
                    ),
                    HazardAlert(
                        title = "Extreme Heatwave Warning - Khartoum North",
                        city = "Khartoum",
                        hazardType = "Heatwave",
                        severity = "High",
                        plainLanguageGuidance = "• Limit high-exertion outdoor activities between 10:00 AM and 4:00 PM. \n• Hydrate continuously (at least 4 liters of water a day, avoid heavy sugar).\n• Keep animals and livestock well-shaded and watered.\n• Set up light cross-ventilation in rooms to lower ambient temperatures.",
                        timestamp = System.currentTimeMillis() - 7200000 // 2 hours ago
                    ),
                    HazardAlert(
                        title = "Coastal Storm Surge and Gale Alert",
                        city = "Lagos",
                        hazardType = "Storm",
                        severity = "Medium",
                        plainLanguageGuidance = "• Secure all loose building materials and roof sheeting. \n• Fishermen and marine operators must avoid open waters.\n• Unplug sensitive electronic equipment to protect against lightning surges.\n• Keep clear of heavy advertising billboards and power line corridors.",
                        timestamp = System.currentTimeMillis() - 14400000 // 4 hours ago
                    )
                )
            )
        }

        // 3. Initialize OSINT Audits
        val currentChallenges = appDao.getAllChallenges().first()
        if (currentChallenges.isEmpty()) {
            appDao.insertChallenges(
                listOf(
                    InvestigationChallenge(
                        title = "Dumping Site Audit: Athi River Margins",
                        description = "Verify a community tip-off regarding a construction company illegally dumping commercial debris in the Athi River wetland buffer.",
                        location = "Athi River, Kenya",
                        type = "Illegal Dumping",
                        difficulty = "Beginner",
                        satelliteTask = "Examine the high-resolution RGB satellite image of the Athi River bank from May 2026. Do you observe an unauthorized grey clearing and truck tracks extending into the green wetland strip?",
                        satelliteImageUrl = "https://images.unsplash.com/photo-1541872703-74c5e44368f9?w=500&auto=format&fit=crop", // Satellite image mockup
                        geotagTask = "Inspect the EXIF camera metadata of the photo submitted by a community member. The photo metadata contains coordinates: Latitude -1.4398, Longitude 36.9582. Does this location match the suspicious grey clearing found in the satellite imagery?",
                        geotagData = "GPS Latitude: 1° 26' 23.28\" S\nGPS Longitude: 36° 57' 29.52\" E\nCamera: TECNO Camon 20\nTimestamp: 2026-06-28 14:22:01",
                        recordsTask = "Search the National Environmental Management Authority (NEMA) public waste-concession registry. Check if 'Athi Green Developers' or similar entities have a valid waste disposal permit for Block 192, Athi River.",
                        recordsData = "NEMA Waste License Search results:\n* No dumping permits active for Block 192.\n* Athi River Wetland is classified as an Ecological Zone Category-A (Strict Protection).\n* Disposal of commercial materials within 100 meters is an offense under Environmental Regulation Act 2012.",
                        pointsReward = 150,
                        isCompleted = false,
                        isLocked = false,
                        currentStep = 0,
                        minQuizzesCompleted = 0
                    ),
                    InvestigationChallenge(
                        title = "Unauthorized Logging: Mabira Forest Buffer",
                        description = "Audit reports of logging machinery encroaching into protected chimpanzee nesting corridors within the Mabira forest boundaries.",
                        location = "Mabira Forest, Uganda",
                        type = "Deforestation",
                        difficulty = "Intermediate",
                        satelliteTask = "Review the forest canopy density change detection layer. Notice the grid-like scars appearing on the eastern ridge over the last 90 days. Are these consistent with selective logging trails or standard seasonal leaf shedding?",
                        satelliteImageUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=500&auto=format&fit=crop",
                        geotagTask = "Compare the user-submitted timber photo geotags with the Uganda National Forestry Authority (NFA) Boundary shapefile. Does the logging location fall within the strict boundary of Compartment 44?",
                        geotagData = "GPS Latitude: 0° 23' 11.4\" N\nGPS Longitude: 32° 58' 45.1\" E\nVerification: Within Compartment 44 Boundaries (Strict Conservation)",
                        recordsTask = "Consult the Ministry of Lands public forest concessions ledger. Does local timber company 'Mabira Lumber Co.' have an active concession for selective logging in Compartment 44?",
                        recordsData = "NFA Forestry Concession Logs:\n* Concession ID NF-442 was suspended on Dec 2025 due to watershed violations.\n* Current status: SUSPENDED/VOID.\n* Any extraction of hardwood in Compartment 44 is active illegal logging.",
                        pointsReward = 250,
                        isCompleted = false,
                        isLocked = true, // Locked initially until previous is completed or unlocked!
                        currentStep = 0,
                        minQuizzesCompleted = 1 // Gate: 1 quiz completed
                    ),
                    InvestigationChallenge(
                        title = "Mercury Gold Mining Audit: Kakamega Creeks",
                        description = "Use geological OSINT to investigate community reports of heavy mud runoff and gold panning sluices poisoning localized creek networks.",
                        location = "Kakamega County, Kenya",
                        type = "Unauthorized Mining",
                        difficulty = "Advanced",
                        satelliteTask = "Trace the Kakamega creek bed downstream in the satellite infrared layer. Observe the high-reflectance tailings ponds (turquoise pools). Has the sediment runoff increased by over 200% since last quarter?",
                        satelliteImageUrl = "https://images.unsplash.com/photo-1578328819058-b69f3a3b0f6b?w=500&auto=format&fit=crop",
                        geotagTask = "Examine the geotags of the video depicting panning operations. Do the coordinates fall in the community agricultural irrigation canal, creating a chemical runoff danger?",
                        geotagData = "GPS Latitude: 0° 16' 48\" N\nGPS Longitude: 34° 45' 03\" E\nOffset: Directly intersects agricultural channel feed.",
                        recordsTask = "Check the Mining Registrar’s mineral map. Are there active artisanal mining licenses registered for Kakamega Watershed Compartment 8A?",
                        recordsData = "Mining Registrar Map Inquiry:\n* Compartment 8A is designated strictly for subsistence farming.\n* Mercury usage is strictly prohibited in KakamegaCounty by Mining Act Amendment Section 14B.",
                        pointsReward = 400,
                        isCompleted = false,
                        isLocked = true,
                        currentStep = 0,
                        minQuizzesCompleted = 2 // Gate: 2 quizzes completed
                    )
                )
            )
        }

        // 4. Initialize Quiz Questions
        val currentQuizzes = appDao.getAllQuizQuestions().first()
        if (currentQuizzes.isEmpty()) {
            appDao.insertQuizQuestions(
                listOf(
                    QuizQuestion(
                        questionText = "Which of the following is a primary indicator of illegal river-sand mining when inspecting high-resolution satellite imagery?",
                        optionA = "Increased growth of water hyacinth",
                        optionB = "Fresh shoreline erosion, heavy-machinery wheel ruts, and unauthorized sand-barges",
                        optionC = "Expansion of nearby residential concrete rooftops",
                        optionD = "Increased water clarity downstream",
                        correctAnswerIndex = 1,
                        explanationText = "Illegal sand mining is visually characterized by active dredging barges, artificial sand mounds, heavy wheel scars on beaches, and dramatic shoreline degradation over time.",
                        pointsReward = 30,
                        isCompleted = false
                    ),
                    QuizQuestion(
                        questionText = "What OSINT metadata element can definitively prove a photo of dumping was taken at the reported coordinates?",
                        optionA = "EXIF GPS metadata embedded in the image file",
                        optionB = "The brightness and contrast settings of the camera",
                        optionC = "The file format of the photo (JPEG vs PNG)",
                        optionD = "The profile name of the person who uploaded the photo",
                        correctAnswerIndex = 0,
                        explanationText = "EXIF (Exchangeable Image File Format) metadata contains precise GPS coordinates (latitude and longitude) captured by the device's camera sensor at the time of exposure.",
                        pointsReward = 30,
                        isCompleted = false
                    ),
                    QuizQuestion(
                        questionText = "When reviewing public concessions to verify illegal logging, which authority's records should you cross-reference?",
                        optionA = "The regional meteorological agency",
                        optionB = "The Ministry of Forestry or land registration concessions registry",
                        optionC = "The local municipal transit authority",
                        optionD = "The agricultural fertilizer distribution records",
                        correctAnswerIndex = 1,
                        explanationText = "Concession registries map out which logging companies have valid legal permits to cut timber in specific forest compartments, allowing you to audit whether a logging site is unauthorized.",
                        pointsReward = 30,
                        isCompleted = false
                    )
                )
            )
        }

        // 5. Initialize Reported Incidents
        val currentReports = appDao.getAllReportedIncidents().first()
        if (currentReports.isEmpty()) {
            // These start with remoteId to prevent duplicate insertions on sync
            appDao.insertReportedIncident(
                ReportedIncident(
                    remoteId = "1",
                    title = "Illegal Industrial Rubble Dumping",
                    type = "Illegal Dumping",
                    location = "Gbagada Expressway, Lagos",
                    description = "A commercial tipper truck is dumping heavy construction concrete block debris right into the mangrove wetland channel under cover of darkness.",
                    latitude = 6.5562,
                    longitude = 3.3854,
                    reporterName = "Seyi Alao",
                    timestamp = System.currentTimeMillis() - 86400000, // 1 day ago
                    status = "Verified",
                    upvotes = 34
                )
            )
            appDao.insertReportedIncident(
                ReportedIncident(
                    remoteId = "2",
                    title = "Sudden Tree Felling in Buffer Forest",
                    type = "Deforestation",
                    location = "Mau Forest West, Kenya",
                    description = "Spotted heavy electric chainsaws cutting down indigenous cedar trees inside the protected forest boundary. Local logs loaded onto unmarked flatbed trucks.",
                    latitude = -0.5833,
                    longitude = 35.8333,
                    reporterName = "Chepkemoi",
                    timestamp = System.currentTimeMillis() - 172800000, // 2 days ago
                    status = "In Investigation",
                    upvotes = 19
                )
            )
        }
    }

    // --- Helper Methods to Handle Points & Actions ---

    suspend fun addPointsAndLogActivity(title: String, description: String, points: Int, category: String) {
        val currentProfile = appDao.getUserProfileSync() ?: return
        val newPoints = currentProfile.points + points
        
        // Calculate Level up (every 300 points = 1 level)
        val newLevel = (newPoints / 300) + 1
        
        // Check for new badges
        var badges = currentProfile.badgesEarned.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        
        if (category == "Report" && !badges.contains("Citizen Reporter")) {
            badges.add("Citizen Reporter")
        }
        if (category == "Investigation" && !badges.contains("OSINT Apprentice")) {
            badges.add("OSINT Apprentice")
        }
        if (newPoints >= 500 && !badges.contains("Eco Champ")) {
            badges.add("Eco Champ")
        }
        
        // Save profile
        val updatedProfile = currentProfile.copy(
            points = newPoints,
            level = newLevel,
            badgesEarned = badges.joinToString(", ")
        )
        appDao.updateUserProfile(updatedProfile)

        // Log Activity
        appDao.insertActivity(
            UserActivity(
                title = title,
                description = description,
                pointsEarned = points,
                timestamp = System.currentTimeMillis(),
                category = category
            )
        )

        // Sync profile to server (Task 4)
        updatedProfile.remoteId?.let { remoteId ->
            ClimateApiService.syncProfile(
                userId = remoteId,
                name = updatedProfile.name,
                city = updatedProfile.city,
                points = updatedProfile.points,
                level = updatedProfile.level
            )
        }
    }

    // --- Core Operations ---

    suspend fun upvoteIncident(incidentId: Int) {
        // We find the incident, increment upvotes, update
        val incidents = appDao.getAllReportedIncidents().first()
        val match = incidents.find { it.id == incidentId }
        if (match != null) {
            val updated = match.copy(upvotes = match.upvotes + 1)
            appDao.updateReportedIncident(updated)
            
            // Sync upvote to remote if it has a remote ID (Task 1)
            updated.remoteId?.let { rId ->
                ClimateApiService.upvoteIncidentRemote(rId)
            }
        }
    }

    suspend fun submitIncidentReport(title: String, type: String, location: String, description: String, lat: Double, lng: Double, reporter: String) {
        val localReport = ReportedIncident(
            title = title,
            type = type,
            location = location,
            description = description,
            latitude = lat,
            longitude = lng,
            reporterName = reporter,
            timestamp = System.currentTimeMillis(),
            status = "Submitted",
            upvotes = 1
        )
        // Write locally first (offline-first responsiveness)
        appDao.insertReportedIncident(localReport)

        // Find the inserted incident to get its local ID
        val inserted = appDao.getAllReportedIncidents().first().firstOrNull {
            it.title == title && it.timestamp == localReport.timestamp
        }

        // Push to server
        val serverId = ClimateApiService.submitIncidentRemote(
            title = title,
            type = type,
            location = location,
            description = description,
            latitude = lat,
            longitude = lng,
            reporterName = reporter
        )

        if (serverId != null && inserted != null) {
            // Update local incident with remoteId
            val updated = inserted.copy(remoteId = serverId)
            appDao.updateReportedIncident(updated)
        }

        // Earn 50 points for reporting!
        addPointsAndLogActivity(
            title = "Reported Eco Incident",
            description = "Logged report: $title in $location",
            points = 50,
            category = "Report"
        )
    }

    suspend fun syncIncidentsFromRemote() {
        val latestLocalIncident = appDao.getAllReportedIncidents().first().maxByOrNull { it.timestamp }
        val since = latestLocalIncident?.timestamp ?: 0L

        val remoteIncidents = ClimateApiService.fetchIncidentsRemote(since) ?: return

        for (i in 0 until remoteIncidents.length()) {
            val obj = remoteIncidents.getJSONObject(i)
            val remoteId = obj.optInt("id").toString()
            val title = obj.optString("title")
            val type = obj.optString("type")
            val location = obj.optString("location")
            val description = obj.optString("description")
            val latitude = obj.optDouble("latitude", 0.0)
            val longitude = obj.optDouble("longitude", 0.0)
            val reporterName = obj.optString("reporter_name", "Anonymous")
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            val status = obj.optString("status", "Submitted")
            val upvotes = obj.optInt("upvotes", 0)

            val existing = appDao.getIncidentByRemoteId(remoteId)
            if (existing != null) {
                val updated = existing.copy(
                    title = title,
                    type = type,
                    location = location,
                    description = description,
                    latitude = latitude,
                    longitude = longitude,
                    reporterName = reporterName,
                    timestamp = timestamp,
                    status = status,
                    upvotes = upvotes
                )
                appDao.updateReportedIncident(updated)
            } else {
                val newIncident = ReportedIncident(
                    remoteId = remoteId,
                    title = title,
                    type = type,
                    location = location,
                    description = description,
                    latitude = latitude,
                    longitude = longitude,
                    reporterName = reporterName,
                    timestamp = timestamp,
                    status = status,
                    upvotes = upvotes
                )
                appDao.insertReportedIncident(newIncident)
            }
        }
    }

    suspend fun advanceChallengeStep(challengeId: Int): InvestigationChallenge? {
        val challenge = appDao.getChallengeById(challengeId) ?: return null
        val nextStep = challenge.currentStep + 1
        
        if (nextStep <= 4) {
            val isFinished = nextStep == 4
            val updated = challenge.copy(
                currentStep = nextStep,
                isCompleted = isFinished
            )
            appDao.updateChallenge(updated)

            // Log micro-progress
            val stepName = when (nextStep) {
                1 -> "Satellite verification complete"
                2 -> "EXIF geotag comparison validated"
                3 -> "Land concessions registry verified"
                4 -> "Audit finalized and published"
                else -> "Progress logged"
            }

            if (isFinished) {
                // Award full points
                addPointsAndLogActivity(
                    title = "OSINT Audit Finalized",
                    description = "Completed environmental audit: ${challenge.title}",
                    points = challenge.pointsReward,
                    category = "Investigation"
                )
                
                // Unlock next challenge if applicable
                unlockNextChallenge(challengeId)
            } else {
                // Mini reward for micro-step
                addPointsAndLogActivity(
                    title = "OSINT Step Verified",
                    description = "$stepName for ${challenge.title}",
                    points = 20,
                    category = "Investigation"
                )
            }
            return appDao.getChallengeById(challengeId)
        }
        return challenge
    }

    private suspend fun unlockNextChallenge(completedId: Int) {
        val activeChallenges = appDao.getAllChallenges().first().sortedBy { it.id }
        val completedQuizCount = appDao.getAllQuizQuestions().first().count { it.isCompleted }

        val compIndex = activeChallenges.indexOfFirst { it.id == completedId }
        if (compIndex != -1 && compIndex + 1 < activeChallenges.size) {
            val nextChallenge = activeChallenges[compIndex + 1]
            if (nextChallenge.isLocked && completedQuizCount >= nextChallenge.minQuizzesCompleted) {
                val unlocked = nextChallenge.copy(isLocked = false)
                appDao.updateChallenge(unlocked)
            }
        }
    }

    suspend fun submitQuizAnswer(questionId: Int, isCorrect: Boolean, reward: Int) {
        val questions = appDao.getAllQuizQuestions().first()
        val question = questions.find { id -> id.id == questionId }
        if (question != null && !question.isCompleted) {
            val updated = question.copy(isCompleted = true)
            appDao.updateQuizQuestion(updated)

            if (isCorrect) {
                addPointsAndLogActivity(
                    title = "Quiz Question Mastered",
                    description = "Correctly answered climate literacy question.",
                    points = reward,
                    category = "Quiz"
                )
                
                // If all quiz questions are completed, award "Climate Scholar" badge
                val allCompletedNow = appDao.getAllQuizQuestions().first().all { q -> q.isCompleted }
                if (allCompletedNow) {
                    val currentProfile = appDao.getUserProfileSync()
                    if (currentProfile != null) {
                        var badges = currentProfile.badgesEarned.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                        if (!badges.contains("Climate Scholar")) {
                            badges.add("Climate Scholar")
                            val updatedProfile = currentProfile.copy(
                                badgesEarned = badges.joinToString(", ")
                            )
                            appDao.updateUserProfile(updatedProfile)
                        }
                    }
                }
            } else {
                // Incorrect - log activity with 0 points
                appDao.insertActivity(
                    UserActivity(
                        title = "Quiz Question Attempted",
                        description = "Reviewed quiz question explanation to build climate literacy.",
                        pointsEarned = 0,
                        timestamp = System.currentTimeMillis(),
                        category = "Quiz"
                    )
                )
            }

            // Check if we can unlock next challenges now that quiz count has increased (Task 3)
            val activeChallenges = appDao.getAllChallenges().first().sortedBy { it.id }
            val completedQuizCount = appDao.getAllQuizQuestions().first().count { it.isCompleted }
            activeChallenges.forEachIndexed { index, challenge ->
                if (challenge.isLocked) {
                    val prevIsCompleted = if (index > 0) activeChallenges[index - 1].isCompleted else true
                    if (prevIsCompleted && completedQuizCount >= challenge.minQuizzesCompleted) {
                        appDao.updateChallenge(challenge.copy(isLocked = false))
                    }
                }
            }
        }
    }

    suspend fun logDailyClimateAction(actionName: String, points: Int) {
        addPointsAndLogActivity(
            title = "Eco Action Logged",
            description = "Completed real-world green action: $actionName",
            points = points,
            category = "Action"
        )
    }

    suspend fun readAlert(alertId: Int) {
        val alerts = appDao.getAllHazardAlerts().first()
        val match = alerts.find { it.id == alertId }
        if (match != null && !match.isSaved) {
            val updated = match.copy(isSaved = true)
            appDao.updateHazardAlert(updated)
            
            // Log that user read and saved the guidance
            addPointsAndLogActivity(
                title = "Hazard Action Plan Read",
                description = "Reviewed hyperlocal action checklist for: ${match.title}",
                points = 10,
                category = "Action"
            )
        }
    }
}
