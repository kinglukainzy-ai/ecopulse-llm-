package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoPulseApp(viewModel: EcoPulseViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val hazardAlerts by viewModel.hazardAlerts.collectAsState()
    val challenges by viewModel.challenges.collectAsState()
    val reportedIncidents by viewModel.reportedIncidents.collectAsState()
    val activities by viewModel.activities.collectAsState()
    val quizQuestions by viewModel.quizQuestions.collectAsState()
    val selectedChallenge by viewModel.selectedChallenge.collectAsState()

    val aiResponse by viewModel.aiGuideResponse.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val aiServerOnline by viewModel.aiServerOnline.collectAsState()

    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // EcoPulse brand logo from drawable resource
                        Image(
                            painter = painterResource(id = R.drawable.ecopulse_logo),
                            contentDescription = "EcoPulse Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                        Column {
                            Text(
                                text = "EcoPulse",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF191C19)
                            )
                            Text(
                                text = "${userProfile?.city ?: "Nairobi"}, Africa",
                                fontSize = 11.sp,
                                color = Color(0xFF424940),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.askAiGuide("Explain how I can maximize my environmental impact on EcoPulse.")
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFDCE7D0), CircleShape)
                            .testTag("notification_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Alert notifications",
                            tint = Color(0xFF191C19)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF386B1D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userProfile?.name?.take(2)?.uppercase() ?: "KA",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFBFDF8)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFF1F5EB),
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, Color(0xFFDCE7D0), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = currentTab == "feed",
                    onClick = { viewModel.selectTab("feed") },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home Feed") },
                    label = { Text("Feed", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF386B1D),
                        selectedTextColor = Color(0xFF386B1D),
                        indicatorColor = Color(0xFFDCE7D0),
                        unselectedIconColor = Color(0xFF424940),
                        unselectedTextColor = Color(0xFF424940)
                    ),
                    modifier = Modifier.testTag("nav_feed")
                )
                NavigationBarItem(
                    selected = currentTab == "map",
                    onClick = { viewModel.selectTab("map") },
                    icon = { Icon(Icons.Default.Public, contentDescription = "Incident Map") },
                    label = { Text("Audit Map", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF386B1D),
                        selectedTextColor = Color(0xFF386B1D),
                        indicatorColor = Color(0xFFDCE7D0),
                        unselectedIconColor = Color(0xFF424940),
                        unselectedTextColor = Color(0xFF424940)
                    ),
                    modifier = Modifier.testTag("nav_map")
                )
                NavigationBarItem(
                    selected = currentTab == "investigate",
                    onClick = { viewModel.selectTab("investigate") },
                    icon = { Icon(Icons.Default.TravelExplore, contentDescription = "OSINT Auditing") },
                    label = { Text("OSINT", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF386B1D),
                        selectedTextColor = Color(0xFF386B1D),
                        indicatorColor = Color(0xFFDCE7D0),
                        unselectedIconColor = Color(0xFF424940),
                        unselectedTextColor = Color(0xFF424940)
                    ),
                    modifier = Modifier.testTag("nav_investigate")
                )
                NavigationBarItem(
                    selected = currentTab == "learn",
                    onClick = { viewModel.selectTab("learn") },
                    icon = { Icon(Icons.Default.School, contentDescription = "Climate Literacy") },
                    label = { Text("Learn", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF386B1D),
                        selectedTextColor = Color(0xFF386B1D),
                        indicatorColor = Color(0xFFDCE7D0),
                        unselectedIconColor = Color(0xFF424940),
                        unselectedTextColor = Color(0xFF424940)
                    ),
                    modifier = Modifier.testTag("nav_learn")
                )
            }
        },
        containerColor = Color(0xFFFBFDF8)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFFBFDF8))
        ) {
            // Stats Panel visible at the top of the feed, map, and learn views
            if (selectedChallenge == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Eco-Points
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFF9CD67D), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFB8F396)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ECO-POINTS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF102004)
                                )
                                Icon(
                                    imageVector = Icons.Default.MilitaryTech,
                                    contentDescription = "Points Badge",
                                    tint = Color(0xFF102004),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${userProfile?.points ?: 0}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF102004),
                                modifier = Modifier.testTag("user_points_text")
                            )
                            Text(
                                text = "Level ${userProfile?.level ?: 1} Champion",
                                fontSize = 9.sp,
                                color = Color(0xFF102004).copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Verified Actions
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFFC0CCB5), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCE7D0)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "VERIFIED ACTIONS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF191C19)
                                )
                                Icon(
                                    imageVector = Icons.Default.FactCheck,
                                    contentDescription = "Actions completed",
                                    tint = Color(0xFF191C19),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${activities.size}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF191C19)
                            )
                            Text(
                                text = "Green career builder",
                                fontSize = 9.sp,
                                color = Color(0xFF424940),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Central content block (Dynamic transition/swap)
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    "feed" -> FeedTab(
                        alerts = hazardAlerts,
                        viewModel = viewModel,
                        aiResponse = aiResponse,
                        isAiLoading = isAiLoading,
                        aiServerOnline = aiServerOnline
                    )
                    "map" -> MapTab(
                        incidents = reportedIncidents,
                        viewModel = viewModel,
                        onRequestReport = { showReportDialog = true }
                    )
                    "investigate" -> InvestigateTab(
                        challenges = challenges,
                        selectedChallenge = selectedChallenge,
                        viewModel = viewModel
                    )
                    "learn" -> LearnTab(
                        questions = quizQuestions,
                        userProfile = userProfile,
                        activities = activities,
                        viewModel = viewModel
                    )
                }
            }
        }

        // New Incident Report Dialog
        if (showReportDialog) {
            ReportIncidentDialog(
                onDismiss = { showReportDialog = false },
                onSubmit = { title, category, location, description, lat, lng ->
                    viewModel.submitReport(title, category, location, description, lat, lng)
                    showReportDialog = false
                }
            )
        }
    }
}

// ==========================================
// 1. FEED TAB (Alerts, Guidance & AI Climate Guide)
// ==========================================
@Composable
fun FeedTab(
    alerts: List<HazardAlert>,
    viewModel: EcoPulseViewModel,
    aiResponse: String,
    isAiLoading: Boolean,
    aiServerOnline: Boolean? = null
) {
    var customQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // AI Climate Guide Context Bubble (High density look)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF191C19)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Logo in the AI Guide card header
                        Image(
                            painter = painterResource(id = R.drawable.ecopulse_logo),
                            contentDescription = "EcoPulse Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Eco · EcoPulse AI Guide",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Powered by local LLM · climate_agent",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                        // Server online/offline status dot
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        color = when (aiServerOnline) {
                                            true  -> Color(0xFF7FFF44)
                                            false -> Color(0xFFFF5252)
                                            null  -> Color(0xFFFFD740)
                                        },
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = when (aiServerOnline) {
                                    true  -> "Online"
                                    false -> "Offline"
                                    null  -> "..."
                                },
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 9.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isAiLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color(0xFFB8F396),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "EcoPulse AI is thinking...",
                                color = Color(0xFFB8F396),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = aiResponse,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick-ask interactive buttons
                    Text(
                        text = "TAP TO INQUIRE:",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8F396)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { viewModel.askAiGuide("Explain Nairobi flooding mitigation steps.") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Nairobi floods?", color = Color.White, fontSize = 9.sp)
                        }
                        Button(
                            onClick = { viewModel.askAiGuide("What is the impact of toxic mercuric sand gold panning?") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Mercury gold hazards", color = Color.White, fontSize = 9.sp)
                        }
                        Button(
                            onClick = { viewModel.askAiGuide("Explain OSINT forest density checks.") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("OSINT checks", color = Color.White, fontSize = 9.sp)
                        }
                        // Live weather button — calls real Open-Meteo via bot server
                        Button(
                            onClick = {
                                val city = "Accra" // Default; could use userProfile?.city
                                viewModel.askWeatherAlert(city)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386B1D).copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = "Live weather",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text("Live weather", color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Custom search textfield
                    OutlinedTextField(
                        value = customQuery,
                        onValueChange = { customQuery = it },
                        placeholder = { Text("Ask about hazards, OSINT, or action...", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("ai_query_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFB8F396),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color(0xFFB8F396)
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (customQuery.isNotBlank()) {
                                viewModel.askAiGuide(customQuery)
                                customQuery = ""
                            }
                        }),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (customQuery.isNotBlank()) {
                                        viewModel.askAiGuide(customQuery)
                                        customQuery = ""
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFFB8F396),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = "HYPERLOCAL HAZARD ALERTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C19),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Active alerts
        items(alerts) { alert ->
            val isCritical = alert.severity == "Critical" || alert.severity == "High"
            val cardBg = if (isCritical) Color(0xFFFFDAD6) else Color(0xFFE8F5E9)
            val borderCol = if (isCritical) Color(0xFFFFB4AB) else Color(0xFFC8E6C9)
            val textCol = if (isCritical) Color(0xFF410002) else Color(0xFF1B5E20)
            val iconBg = if (isCritical) Color(0xFFBA1A1A) else Color(0xFF2E7D32)

            var isExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderCol, RoundedCornerShape(24.dp))
                    .clickable {
                        isExpanded = !isExpanded
                        viewModel.readAlert(alert.id)
                    }
                    .testTag("alert_item_${alert.id}"),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(iconBg, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (alert.hazardType) {
                                    "Flood" -> Icons.Default.Warning
                                    "Heatwave" -> Icons.Default.Warning
                                    else -> Icons.Default.Warning
                                },
                                contentDescription = alert.hazardType,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = alert.severity.uppercase() + " ALERT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = textCol,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Tap to view Action Plan",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textCol.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                text = alert.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Divider(color = textCol.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "PLAIN-LANGUAGE ACTION PLAN:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = alert.plainLanguageGuidance,
                                fontSize = 11.sp,
                                color = textCol.copy(alpha = 0.9f),
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (alert.isSaved) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Completed",
                                            tint = textCol,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Plan Read (+10 pts Earned)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textCol
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .background(textCol, RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.askAiGuide("Give me localized expert context for ${alert.title}")
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Ask AI Expert",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Daily Actions Logger (Youth-focused actions)
        item {
            Text(
                text = "LOG DAILY CLIMATE ACTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C19),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFDCE7D0), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5EB)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Claim points for taking action today. Local verified activities strengthen Nairobi and Lagos communities.",
                        fontSize = 11.sp,
                        color = Color(0xFF424940),
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val actions = listOf(
                        "Used public transit instead of taxi/car" to 15,
                        "Separated organic compost & plastic bottle waste" to 20,
                        "Planted localized indigenous tree seedling" to 40,
                        "Cleared garbage blockage from roadside ditch" to 30
                    )

                    actions.forEachIndexed { index, pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFDCE7D0).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.logDailyAction(pair.first, pair.second)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Log action",
                                    tint = Color(0xFF386B1D),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = pair.first,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF191C19)
                                )
                            }
                            Text(
                                text = "+${pair.second} pts",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF386B1D)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================
// 2. AUDIT MAP TAB (Live Incident List & Map View)
// ==========================================
@Composable
fun MapTab(
    incidents: List<ReportedIncident>,
    viewModel: EcoPulseViewModel,
    onRequestReport: () -> Unit
) {
    var selectedIncidentForDetails by remember { mutableStateOf<ReportedIncident?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIVE ENVIRONMENTAL AUDIT MAP",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C19)
            )
            Text(
                text = "OSINT Verified",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF386B1D)
            )
        }

        // Mock Map Canvas (High Fidelity graphic layout representing map coordinates)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .border(2.dp, Color(0xFF9CD67D), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // High fidelity aesthetic map background using satellite imagery or textured representation
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1524661135-423995f22d0b?w=600&auto=format&fit=crop",
                    contentDescription = "Satellite imagery map layer",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Add grid lines or overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF386B1D).copy(alpha = 0.15f))
                )

                // Visual markers overlaid on top of the mock map
                incidents.forEach { incident ->
                    // Position calculations for nice scattering
                    val xOffset = when (incident.id % 3) {
                        0 -> 40.dp
                        1 -> 150.dp
                        else -> 260.dp
                    }
                    val yOffset = when (incident.id % 2) {
                        0 -> 30.dp
                        else -> 90.dp
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = xOffset, y = yOffset)
                            .size(28.dp)
                            .background(
                                color = if (incident.status == "Verified") Color(0xFF386B1D) else Color(0xFFFF9800),
                                shape = CircleShape
                            )
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { selectedIncidentForDetails = incident }
                            .testTag("map_marker_${incident.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (incident.type) {
                                "Illegal Dumping" -> Icons.Default.Delete
                                "Deforestation" -> Icons.Default.Forest
                                else -> Icons.Default.Warning
                            },
                            contentDescription = incident.title,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Map HUD overlay (High density looks)
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF191C19).copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF386B1D), CircleShape))
                        Text("OSINT Verified", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF9800), CircleShape))
                        Text("Investigating", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Action Buttons: File Report
        Button(
            onClick = onRequestReport,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("file_new_report_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386B1D)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Text("File New Environment Audit Report (+50 pts)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Display selected or default details list
        Text(
            text = "TRUSTWORTHY AUDIT LOG",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF191C19)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Selected pin details banner
            if (selectedIncidentForDetails != null) {
                val inc = selectedIncidentForDetails!!
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFB8F396), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFB8F396).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SELECTED INCIDENT DETAILS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF102004)
                                )
                                Text(
                                    text = "Dismiss",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF386B1D),
                                    modifier = Modifier.clickable { selectedIncidentForDetails = null }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = inc.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF191C19))
                            Text(text = "Location: ${inc.location} (${inc.latitude}, ${inc.longitude})", fontSize = 11.sp, color = Color(0xFF424940))
                            Text(text = inc.description, fontSize = 11.sp, color = Color(0xFF191C19), modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF386B1D), modifier = Modifier.size(14.dp))
                                    Text(inc.status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF386B1D))
                                }
                                Button(
                                    onClick = { viewModel.upvoteIncident(inc.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386B1D)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Upvote (${inc.upvotes})", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            items(incidents) { incident ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFDCE7D0), RoundedCornerShape(16.dp))
                        .clickable { selectedIncidentForDetails = incident }
                        .testTag("incident_item_${incident.id}"),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = when (incident.type) {
                                        "Illegal Dumping" -> Icons.Default.Delete
                                        "Deforestation" -> Icons.Default.Forest
                                        else -> Icons.Default.Warning
                                    },
                                    contentDescription = null,
                                    tint = Color(0xFF386B1D),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = incident.type.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF386B1D)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (incident.status == "Verified") Color(0xFFDCE7D0) else Color(0xFFFFE0B2),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = incident.status,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (incident.status == "Verified") Color(0xFF386B1D) else Color(0xFFE65100)
                                )
                            }
                        }

                        Text(
                            text = incident.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF191C19),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Text(
                            text = incident.description,
                            fontSize = 11.sp,
                            color = Color(0xFF424940),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reporter: ${incident.reporterName} • ${incident.location}",
                                fontSize = 9.sp,
                                color = Color(0xFF424940)
                            )
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFFF1F5EB), RoundedCornerShape(6.dp))
                                    .clickable { viewModel.upvoteIncident(incident.id) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.ThumbUp, contentDescription = "Upvote", tint = Color(0xFF386B1D), modifier = Modifier.size(10.dp))
                                Text("${incident.upvotes}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191C19))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. INVESTIGATE TAB (Eco-Investigator OSINT Modules)
// ==========================================
@Composable
fun InvestigateTab(
    challenges: List<InvestigationChallenge>,
    selectedChallenge: InvestigationChallenge?,
    viewModel: EcoPulseViewModel
) {
    if (selectedChallenge != null) {
        // Step-by-Step Investigation Workstation
        ChallengeWorkstation(challenge = selectedChallenge, viewModel = viewModel)
    } else {
        // Challenges Directory (Grid/List View)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "ECO-INVESTIGATOR WORKSTATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF191C19)
                )
                Text(
                    text = "Gamified environment audits using official OSINT techniques. Prove illegal dumping, logging, and unauthorized mining directly to public authorities.",
                    fontSize = 11.sp,
                    color = Color(0xFF424940),
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            items(challenges) { challenge ->
                val cardBg = if (challenge.isLocked) Color(0xFFF5F5F5) else Color(0xFFDCE7D0)
                val textCol = if (challenge.isLocked) Color.Gray else Color(0xFF191C19)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (challenge.isLocked) Color.LightGray else Color(0xFF9CD67D),
                            RoundedCornerShape(24.dp)
                        )
                        .clickable(enabled = !challenge.isLocked) {
                            viewModel.selectChallenge(challenge)
                        }
                        .testTag("challenge_card_${challenge.id}"),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = challenge.difficulty.uppercase() + " OSINT AUDIT",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = if (challenge.isLocked) Color.Gray else Color(0xFF386B1D)
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (challenge.isCompleted) Color(0xFF386B1D) else if (challenge.isLocked) Color.Gray else Color(0xFF191C19),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (challenge.isCompleted) "COMPLETED" else if (challenge.isLocked) "LOCKED" else "START MISSION",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        Text(
                            text = challenge.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Text(
                            text = challenge.description,
                            fontSize = 11.sp,
                            color = if (challenge.isLocked) Color.Gray else Color(0xFF424940),
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Location: ${challenge.location}",
                                fontSize = 9.sp,
                                color = if (challenge.isLocked) Color.Gray else Color(0xFF424940),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "+${challenge.pointsReward} Reward Points",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (challenge.isLocked) Color.Gray else Color(0xFF386B1D)
                            )
                        }
                    }
                }
            }
        }
    }
}

// OSINT Interactive Workstation Screen
@Composable
fun ChallengeWorkstation(challenge: InvestigationChallenge, viewModel: EcoPulseViewModel) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Back to Missions",
                fontSize = 11.sp,
                color = Color(0xFF386B1D),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { viewModel.selectChallenge(null) }
                    .testTag("back_to_missions")
            )
            Text(
                text = "Step ${challenge.currentStep} of 4 Complete",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C19)
            )
        }

        Text(
            text = challenge.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF191C19)
        )

        // Steps indicator progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 1..4) {
                val done = challenge.currentStep >= i
                val color = if (done) Color(0xFF386B1D) else Color(0xFFDCE7D0)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
            }
        }

        // STEP 1: Satellite Imagery Cross-Check
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF9CD67D), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STEP 1: SATELLITE IMAGERY ANALYSIS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF386B1D)
                    )
                    if (challenge.currentStep >= 1) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = Color(0xFF386B1D), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = challenge.satelliteTask,
                    fontSize = 11.sp,
                    color = Color(0xFF191C19),
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(6.dp))
                // Visual of mock satellite imagery with high-fidelity visual context
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = challenge.satelliteImageUrl,
                        contentDescription = "Satellite analysis view",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF386B1D).copy(alpha = 0.1f))
                    )
                    Text(
                        text = "RGB SATELLITE May 2026",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                if (challenge.currentStep == 0) {
                    Button(
                        onClick = { viewModel.advanceChallengeStep(challenge.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(36.dp)
                            .testTag("sat_verify_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386B1D)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Confirm Clearing Found (+20 pts)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // STEP 2: EXIF Geotag Verification
        if (challenge.currentStep >= 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF9CD67D), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STEP 2: EXIF METADATA MATCHING",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF386B1D)
                        )
                        if (challenge.currentStep >= 2) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = Color(0xFF386B1D), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = challenge.geotagTask,
                        fontSize = 11.sp,
                        color = Color(0xFF191C19),
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    // Metadata logs
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5EB)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = challenge.geotagData,
                            fontSize = 10.sp,
                            color = Color(0xFF424940),
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    if (challenge.currentStep == 1) {
                        Button(
                            onClick = { viewModel.advanceChallengeStep(challenge.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(36.dp)
                                .testTag("geo_verify_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386B1D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Validate Geotag Alignment (+20 pts)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // STEP 3: Public Land Registry concessions Audit
        if (challenge.currentStep >= 2) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF9CD67D), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STEP 3: CONCESSIONS REGISTRY AUDIT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF386B1D)
                        )
                        if (challenge.currentStep >= 3) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = Color(0xFF386B1D), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = challenge.recordsTask,
                        fontSize = 11.sp,
                        color = Color(0xFF191C19),
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFDF8)),
                        border = BorderStroke(1.dp, Color(0xFFDCE7D0)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = challenge.recordsData,
                            fontSize = 10.sp,
                            color = Color(0xFF191C19),
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    if (challenge.currentStep == 2) {
                        Button(
                            onClick = { viewModel.advanceChallengeStep(challenge.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(36.dp)
                                .testTag("registry_verify_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386B1D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Confirm Lack of Permit (+20 pts)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // STEP 4: Submit Final Report & Earn Main Reward
        if (challenge.currentStep >= 3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFF386B1D), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB8F396)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "OSINT EVIDENCE COMPILED SUCCESSFULLY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF102004)
                    )
                    Text(
                        text = "You've successfully cross-referenced physical satellite gaps, validated EXIF telemetry, and proved that no valid environmental concessions exist for this site.",
                        fontSize = 11.sp,
                        color = Color(0xFF102004).copy(alpha = 0.9f),
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    if (challenge.currentStep == 3) {
                        Button(
                            onClick = { viewModel.advanceChallengeStep(challenge.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(40.dp)
                                .testTag("finalize_audit_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF191C19)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Publish Verified Environmental Audit (+${challenge.pointsReward} pts)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF102004))
                            Text(
                                text = "Audit logged to public assemblies! +${challenge.pointsReward} Points Redeemed.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF102004)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// 4. LEARN TAB (Quizzes & Profiles)
// ==========================================
@Composable
fun LearnTab(
    questions: List<QuizQuestion>,
    userProfile: UserProfile?,
    activities: List<UserActivity>,
    viewModel: EcoPulseViewModel
) {
    var selectedQuestionIndex by remember { mutableStateOf(0) }
    val currentQuestion = questions.getOrNull(selectedQuestionIndex)

    var selectedAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var answerSubmitted by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Youth Profile & Badge progression HUD
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFDCE7D0), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5EB)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ECO-CITIZEN RESUME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF191C19)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF386B1D), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userProfile?.name?.take(2)?.uppercase() ?: "KA",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Column {
                            Text(
                                text = userProfile?.name ?: "Munashe Mwangi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF191C19)
                            )
                            Text(
                                text = "Citizen Inspector Level ${userProfile?.level ?: 1}",
                                fontSize = 11.sp,
                                color = Color(0xFF424940)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "EARNED BADGES:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF191C19)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val badgesList = userProfile?.badgesEarned?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    if (badgesList.isEmpty()) {
                        Text("No badges earned yet. Complete daily actions to unlock!", fontSize = 10.sp, color = Color.Gray)
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            badgesList.forEach { badge ->
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF386B1D), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFB8F396), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFB8F396), modifier = Modifier.size(12.dp))
                                        Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Climate OSINT Literacy Quizzes
        if (currentQuestion != null) {
            item {
                Text(
                    text = "CLIMATE & OSINT LITERACY QUIZ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF191C19)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF9CD67D), RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "QUESTION ${selectedQuestionIndex + 1} OF ${questions.size}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF386B1D)
                            )
                            Text(
                                text = "+${currentQuestion.pointsReward} Points",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF386B1D)
                            )
                        }

                        Text(
                            text = currentQuestion.questionText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF191C19),
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // 4 options
                        val options = listOf(
                            currentQuestion.optionA,
                            currentQuestion.optionB,
                            currentQuestion.optionC,
                            currentQuestion.optionD
                        )

                        options.forEachIndexed { index, option ->
                            val isSelected = selectedAnswerIndex == index
                            val itemBg = if (isSelected) Color(0xFFDCE7D0) else Color(0xFFFBFDF8)
                            val itemBorder = if (isSelected) Color(0xFF386B1D) else Color(0xFFDCE7D0)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(itemBg, RoundedCornerShape(12.dp))
                                    .border(1.dp, itemBorder, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !answerSubmitted) {
                                        selectedAnswerIndex = index
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(if (isSelected) Color(0xFF386B1D) else Color.White, CircleShape)
                                        .border(1.dp, Color(0xFF386B1D), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Text(
                                            text = (index + 65).toChar().toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = (index + 65).toChar().toString(),
                                            color = Color(0xFF386B1D),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = option,
                                    fontSize = 11.sp,
                                    color = Color(0xFF191C19),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Submit or Explanatory Block
                        if (!answerSubmitted) {
                            Button(
                                onClick = {
                                    if (selectedAnswerIndex != null) {
                                        answerSubmitted = true
                                        val isCorrect = selectedAnswerIndex == currentQuestion.correctAnswerIndex
                                        viewModel.submitQuizAnswer(currentQuestion.id, isCorrect, currentQuestion.pointsReward)
                                    }
                                },
                                enabled = selectedAnswerIndex != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .height(40.dp)
                                    .testTag("submit_quiz_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386B1D)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Check Answer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val isCorrect = selectedAnswerIndex == currentQuestion.correctAnswerIndex
                            val blockBg = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFDAD6)
                            val borderC = if (isCorrect) Color(0xFFC8E6C9) else Color(0xFFFFB4AB)
                            val textC = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .border(1.dp, borderC, RoundedCornerShape(16.dp)),
                                colors = CardDefaults.cardColors(containerColor = blockBg),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (isCorrect) "CORRECT! +${currentQuestion.pointsReward} Points Earned" else "INCORRECT",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = textC
                                    )
                                    Text(
                                        text = currentQuestion.explanationText,
                                        fontSize = 11.sp,
                                        color = Color(0xFF191C19),
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    Button(
                                        onClick = {
                                            selectedAnswerIndex = null
                                            answerSubmitted = false
                                            selectedQuestionIndex = (selectedQuestionIndex + 1) % questions.size
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF191C19)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Next Question", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Log history
        item {
            Text(
                text = "YOUR HISTORIC ACTION JOURNAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C19)
            )
        }

        if (activities.isEmpty()) {
            item {
                Text(
                    text = "No activities logged yet. Take physical action or review hazard reports to build your record!",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        } else {
            items(activities) { activity ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFDCE7D0), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activity.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF191C19))
                        Text(activity.description, fontSize = 10.sp, color = Color(0xFF424940))
                    }
                    Text(
                        text = "+${activity.pointsEarned} pts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF386B1D)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================
// 5. DIALOG FOR FILING INCIDENT REPORT
// ==========================================
@Composable
fun ReportIncidentDialog(
    onDismiss: () -> Unit,
    onSubmit: (title: String, category: String, location: String, description: String, lat: Double, lng: Double) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Illegal Dumping") }
    var location by remember { mutableStateOf("Ikorodu Marsh, Lagos") }
    var description by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(2.dp, Color(0xFF386B1D), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFDF8))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "FILE AN ENVIRONMENTAL AUDIT",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = Color(0xFF191C19)
                )

                Text(
                    text = "Log localized environmental dumping, logging, or mining in your community. We will cross-reference this with satellite grids for official OSINT validation.",
                    fontSize = 10.sp,
                    color = Color(0xFF424940),
                    lineHeight = 14.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Short Descriptive Title", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("report_title_field"),
                    singleLine = true
                )

                // Dropdown mock or Simple Row selection for Category
                Text("INCIDENT CATEGORY:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191C19))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Illegal Dumping", "Deforestation", "Unauthorized Mining").forEach { cat ->
                        val selected = category == cat
                        val bg = if (selected) Color(0xFF386B1D) else Color(0xFFF1F5EB)
                        val textC = if (selected) Color.White else Color(0xFF191C19)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(bg, RoundedCornerShape(6.dp))
                                .clickable { category = cat }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat.replace("Unauthorized ", "").replace("Illegal ", ""),
                                color = textC,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Local Neighborhood / City Name", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("report_location_field"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What did you observe? (Vehicles, times, exact spot)", fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .testTag("report_desc_field"),
                    maxLines = 4
                )

                // Coordinates - Auto simulated based on chosen location or random Lagos/Nairobi bounds
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCE7D0))
                ) {
                    Text(
                        text = "Auto-simulated camera location:\nLatitude: ${String.format(Locale.US, "%.4f", 6.5244 + Math.random() * 0.1)}\nLongitude: ${String.format(Locale.US, "%.4f", 3.3792 + Math.random() * 0.1)}",
                        fontSize = 9.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color(0xFF191C19),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank() && description.isNotBlank() && location.isNotBlank()) {
                                onSubmit(
                                    title,
                                    category,
                                    location,
                                    description,
                                    6.5244 + Math.random() * 0.1,
                                    3.3792 + Math.random() * 0.1
                                )
                            }
                        },
                        enabled = title.isNotBlank() && description.isNotBlank() && location.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_report_dialog_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386B1D)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Submit Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
