package com.example.ui

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.theme.*
import java.util.Locale

/**
 * App shell — Scaffold + mascot bottom nav + per-tab screens.
 *
 * Tab id mapping onto the existing ViewModel.currentTab values (kept as-is
 * so syncIncidents()/refreshLeaderboard() side effects in selectTab() still
 * fire correctly):
 *   "feed"       -> HomeScreen
 *   "investigate"-> InvestigatorScreen (missions)
 *   "learn"      -> ChallengesScreen (daily/weekly/monthly actions)
 *   "impact"     -> ImpactScreen        (new — not in old 4-tab set)
 *   "map"        -> ProfileScreen       (Audit Map dropped from nav per the
 *                                         new design; incident reporting is
 *                                         still reachable via the dialog below)
 *
 * The mascot button opens the AI Guide as a modal sheet instead of a
 * permanent always-visible card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoPulseApp(viewModel: EcoPulseViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()

    var showAlerts by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = EcoGreenPale,
        bottomBar = {
            MascotBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    showAlerts = false
                    viewModel.selectTab(tab)
                },
                onMascotClick = { showAiSheet = true },
            )
        },
        floatingActionButton = {
            if (currentTab == "investigate") {
                FloatingActionButton(
                    onClick = { showReportDialog = true },
                    containerColor = EcoGreenPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = "File a report", tint = EcoWhite) }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val screenKey = if (showAlerts) "alerts" else currentTab
            androidx.compose.animation.AnimatedContent(
                targetState = screenKey,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) +
                        androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(220)) { it / 12 })
                        .togetherWith(androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(120)))
                },
                label = "tab-transition",
            ) { key ->
                when {
                    key == "alerts" -> AlertsScreen(viewModel = viewModel, onBack = { showAlerts = false })
                    key == "feed" -> HomeScreenWithBell(viewModel) { showAlerts = true }
                    key == "investigate" -> InvestigatorScreen(viewModel)
                    key == "learn" -> ChallengesScreen(viewModel)
                    key == "impact" -> ImpactScreen(viewModel)
                    key == "map" -> ProfileScreen(viewModel)
                    else -> HomeScreenWithBell(viewModel) { showAlerts = true }
                }
            }
        }
    }

    if (showAiSheet) {
        AiGuideSheet(viewModel = viewModel, onDismiss = { showAiSheet = false })
    }

    if (showReportDialog) {
        ReportIncidentDialog(
            onDismiss = { showReportDialog = false },
            onSubmit = { title, category, location, description, lat, lng ->
                viewModel.submitReport(title, category, location, description, lat, lng)
                showReportDialog = false
            },
        )
    }
}

/**
 * HomeScreen doesn't own navigation to Alerts itself (it's a pure display
 * composable reused elsewhere), so this thin wrapper swaps in a bell icon
 * that actually opens the Alerts screen. Simplest way to do this without
 * threading an extra callback through HomeScreen's signature is to just
 * intercept the tap target here — HomeScreen's own bell already exists
 * but triggers the AI guide; this wrapper's Box sits invisibly on top of it.
 * (See EcoPulseScreens.kt HomeScreen — top-right corner, 40dp circle.)
 */
@Composable
private fun HomeScreenWithBell(viewModel: EcoPulseViewModel, onOpenAlerts: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(viewModel)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onOpenAlerts() },
        )
    }
}

// =============================================================================
// AI GUIDE MODAL SHEET (mascot tap target)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiGuideSheet(viewModel: EcoPulseViewModel, onDismiss: () -> Unit) {
    val aiResponse by viewModel.aiGuideResponse.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val aiServerOnline by viewModel.aiServerOnline.collectAsState()
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = EcoNavyDark) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MascotAvatar(size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Eco · EcoPulse AI Guide", color = EcoWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier.size(7.dp).clip(CircleShape).background(
                                when (aiServerOnline) { true -> EcoGreenLight; false -> EcoRed; null -> EcoAmber }
                            ),
                        )
                        Text(
                            when (aiServerOnline) { true -> "Online"; false -> "Offline"; null -> "..." },
                            color = EcoTextOnDarkMuted, fontSize = 10.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isAiLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = EcoGreenLight, strokeWidth = 2.dp)
                    Text("Eco is thinking...", color = EcoGreenLight, fontSize = 12.sp)
                }
            } else {
                Text(aiResponse, color = EcoTextOnDark, fontSize = 13.sp, lineHeight = 19.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "What's my nearest hazard?" to "Tell me about hazards near my city right now.",
                    "OSINT tips" to "Explain OSINT forest density checks.",
                ).forEach { (label, prompt) ->
                    OutlinedButton(
                        onClick = { viewModel.askAiGuide(prompt) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EcoGreenLight),
                        modifier = Modifier.weight(1f),
                    ) { Text(label, fontSize = 10.sp) }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Ask about hazards, OSINT, or action...", color = EcoTextOnDarkMuted, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EcoGreenLight,
                    unfocusedBorderColor = EcoTextOnDarkMuted,
                    focusedTextColor = EcoWhite,
                    unfocusedTextColor = EcoWhite,
                    cursorColor = EcoGreenLight,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (query.isNotBlank()) { viewModel.askAiGuide(query); query = "" }
                }),
                trailingIcon = {
                    IconButton(onClick = { if (query.isNotBlank()) { viewModel.askAiGuide(query); query = "" } }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = EcoGreenLight)
                    }
                },
            )
        }
    }
}

// =============================================================================
// OSINT INVESTIGATION WORKSTATION — unchanged from the previous implementation
// (already solid: satellite → EXIF → registry → publish flow). Kept as-is so
// InvestigatorScreen in EcoPulseScreens.kt can push into it directly.
// =============================================================================

@Composable
fun ChallengeWorkstation(challenge: InvestigationChallenge, viewModel: EcoPulseViewModel) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "← Back to Missions", fontSize = 11.sp, color = EcoGreenPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.selectChallenge(null) },
            )
            Text("Step ${challenge.currentStep} of 4 Complete", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoTextPrimary)
        }

        Text(challenge.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = EcoTextPrimary)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..4) {
                val done = challenge.currentStep >= i
                Box(
                    modifier = Modifier.weight(1f).height(6.dp)
                        .background(if (done) EcoGreenPrimary else EcoDivider, RoundedCornerShape(3.dp)),
                )
            }
        }

        StepCard(
            stepLabel = "STEP 1: SATELLITE IMAGERY ANALYSIS",
            done = challenge.currentStep >= 1,
            task = challenge.satelliteTask,
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = challenge.satelliteImageUrl, contentDescription = "Satellite analysis view",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
            if (challenge.currentStep == 0) {
                Button(
                    onClick = { viewModel.advanceChallengeStep(challenge.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Confirm Clearing Found (+20 pts)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }

        if (challenge.currentStep >= 1) {
            StepCard(
                stepLabel = "STEP 2: EXIF METADATA MATCHING",
                done = challenge.currentStep >= 2,
                task = challenge.geotagTask,
            ) {
                MonoDataBox(challenge.geotagData)
                if (challenge.currentStep == 1) {
                    Button(
                        onClick = { viewModel.advanceChallengeStep(challenge.id) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Validate Geotag Alignment (+20 pts)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (challenge.currentStep >= 2) {
            StepCard(
                stepLabel = "STEP 3: CONCESSIONS REGISTRY AUDIT",
                done = challenge.currentStep >= 3,
                task = challenge.recordsTask,
            ) {
                MonoDataBox(challenge.recordsData)
                if (challenge.currentStep == 2) {
                    Button(
                        onClick = { viewModel.advanceChallengeStep(challenge.id) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Confirm Lack of Permit (+20 pts)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (challenge.currentStep >= 3) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = EcoGreenLight),
                border = BorderStroke(2.dp, EcoGreenPrimary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("OSINT EVIDENCE COMPILED SUCCESSFULLY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = EcoGreenDark)
                    Text(
                        "You've cross-referenced satellite gaps, validated EXIF telemetry, and proved no valid " +
                            "environmental concessions exist for this site.",
                        fontSize = 11.sp, color = EcoGreenDark.copy(alpha = 0.9f), lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    if (challenge.currentStep == 3) {
                        Button(
                            onClick = { viewModel.advanceChallengeStep(challenge.id) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EcoGreenDark),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "Publish Verified Environmental Audit (+${challenge.pointsReward} pts)",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoWhite,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EcoGreenDark)
                            Text(
                                "Audit logged! +${challenge.pointsReward} Points Redeemed.",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoGreenDark,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun StepCard(stepLabel: String, done: Boolean, task: String, extra: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, EcoDivider),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stepLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EcoGreenPrimary)
                if (done) Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = EcoGreenPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(task, fontSize = 11.sp, color = EcoTextPrimary, lineHeight = 15.sp)
            Spacer(modifier = Modifier.height(6.dp))
            extra()
        }
    }
}

@Composable
private fun MonoDataBox(data: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EcoGreenSurface),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            data, fontSize = 10.sp, color = EcoTextSecondary, fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(8.dp),
        )
    }
}

// =============================================================================
// REPORT INCIDENT DIALOG — unchanged from the previous implementation
// =============================================================================

@Composable
fun ReportIncidentDialog(
    onDismiss: () -> Unit,
    onSubmit: (title: String, category: String, location: String, description: String, lat: Double, lng: Double) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Illegal Dumping") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp).border(2.dp, EcoGreenPrimary, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = EcoGreenPale),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("FILE AN ENVIRONMENTAL AUDIT", fontWeight = FontWeight.Black, fontSize = 12.sp, color = EcoTextPrimary)
                Text(
                    "Log localized environmental dumping, logging, or mining in your community. " +
                        "We'll cross-reference this with satellite grids for OSINT validation.",
                    fontSize = 10.sp, color = EcoTextSecondary, lineHeight = 14.sp,
                )

                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Short Descriptive Title", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )

                Text("INCIDENT CATEGORY:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EcoTextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Illegal Dumping", "Deforestation", "Unauthorized Mining").forEach { cat ->
                        val selected = category == cat
                        Box(
                            modifier = Modifier.weight(1f)
                                .background(if (selected) EcoGreenPrimary else EcoGreenSurface, RoundedCornerShape(6.dp))
                                .clickable { category = cat }.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cat.replace("Unauthorized ", "").replace("Illegal ", ""),
                                color = if (selected) EcoWhite else EcoTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("Local Neighborhood / City Name", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )

                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("What did you observe? (Vehicles, times, exact spot)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().height(80.dp), maxLines = 4,
                )

                val simulatedLat = remember { 5.6037 + Math.random() * 0.1 }
                val simulatedLng = remember { -0.1870 + Math.random() * 0.1 }
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EcoGreenSurface)) {
                    Text(
                        "Auto-simulated camera location:\n" +
                            "Latitude: ${String.format(Locale.US, "%.4f", simulatedLat)}\n" +
                            "Longitude: ${String.format(Locale.US, "%.4f", simulatedLng)}",
                        fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = EcoTextPrimary, modifier = Modifier.padding(8.dp),
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel", fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank() && description.isNotBlank() && location.isNotBlank()) {
                                onSubmit(title, category, location, description, simulatedLat, simulatedLng)
                            }
                        },
                        enabled = title.isNotBlank() && description.isNotBlank() && location.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Submit Report", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
