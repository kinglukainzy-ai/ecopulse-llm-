package com.example.ui

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.*
import com.example.ui.theme.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

// =============================================================================
// SHARED CHROME: mascot avatar + bottom nav
// =============================================================================

/**
 * The mascot avatar. Uses the existing ecopulse_logo drawable so this compiles
 * out of the box. For full fidelity to the mockup, export the mascot
 * illustration as app/src/main/res/drawable/mascot.png and swap the
 * painterResource id below to R.drawable.mascot — every mascot appearance in
 * the app (nav bar + chat bubble avatar) reads from this one composable.
 */
@Composable
fun MascotAvatar(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(EcoGreenSurface),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ecopulse_logo),
            contentDescription = "Eco mascot",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size),
        )
    }
}

private data class NavItem(val tab: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("feed", "Home", Icons.Filled.Home),
    NavItem("investigate", "Investigator", Icons.Filled.TravelExplore),
    NavItem("learn", "Challenges", Icons.Filled.EmojiEvents),
    NavItem("map", "Profile", Icons.Filled.Person),
)

/**
 * Bottom nav bar with the mascot raised in the center, matching the mockup.
 * Tapping the mascot opens the AI Guide sheet instead of navigating a tab.
 */
@Composable
fun MascotBottomNavBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    onMascotClick: () -> Unit,
) {
    val left = NAV_ITEMS.take(2)
    val right = NAV_ITEMS.drop(2)

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                left.forEach { NavIcon(it, currentTab == it.tab) { onTabSelected(it.tab) } }
                Spacer(modifier = Modifier.width(56.dp)) // room for the raised mascot
                right.forEach { NavIcon(it, currentTab == it.tab) { onTabSelected(it.tab) } }
            }
        }

        // Raised mascot button
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .size(58.dp)
                .clip(CircleShape)
                .background(EcoGreenPrimary)
                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .clickable { onMascotClick() },
            contentAlignment = Alignment.Center,
        ) {
            MascotAvatar(size = 46.dp)
        }
    }
}

@Composable
private fun NavIcon(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) EcoGreenPrimary else EcoTextSecondary
    Column(
        modifier = Modifier
            .width(56.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(item.icon, contentDescription = item.label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(item.label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

// =============================================================================
// HOME SCREEN — greeting header, Eco Score, weather alert, daily challenge
// =============================================================================

@Composable
fun HomeScreen(viewModel: EcoPulseViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val hazardAlerts by viewModel.hazardAlerts.collectAsState()
    val activities by viewModel.activities.collectAsState()
    val reportedIncidents by viewModel.reportedIncidents.collectAsState()

    val topAlert = hazardAlerts.firstOrNull { it.severity == "Critical" || it.severity == "High" }
        ?: hazardAlerts.firstOrNull()

    // Eco Score: no dedicated backend field yet — derived from profile points
    // as a 0-100 display value. TODO: replace with a real score once the
    // server tracks one (see region_config / server.py for where to add it).
    val ecoScore = ((userProfile?.points ?: 0).coerceAtMost(1000) / 10).coerceIn(0, 100)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Good Morning, ${userProfile?.name?.split(" ")?.firstOrNull() ?: "Eco Warrior"} 👋",
                        style = MaterialTheme.typography.titleLarge, color = EcoTextPrimary)
                    Text(userProfile?.city ?: "", style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(EcoGreenSurface)
                        .clickable { viewModel.askAiGuide("Any new alerts I should know about right now?") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = "Alerts", tint = EcoGreenPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }

        item {
            // Eco Score card — the one "headline" element on Home, so it
            // gets Hero elevation (bigger, green-tinted shadow) instead of
            // sitting flush with every other card at the same visual weight.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .ecoElevation(EcoElevation.Hero, EcoShape.xl)
                    .clip(EcoShape.xl)
                    .background(EcoGreenDark),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Today's Eco Score", color = EcoTextOnDarkMuted, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$ecoScore", color = EcoWhite, style = MaterialTheme.typography.displaySmall)
                            Text("/100", color = EcoTextOnDarkMuted, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text(
                            if (ecoScore >= 70) "Great job! Let's improve even more today." else "Let's build up your eco score today.",
                            color = EcoTextOnDark, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(EcoGreenLight.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Eco, contentDescription = null, tint = EcoGreenLight, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        if (topAlert != null) {
            item {
                val isCritical = topAlert.severity == "Critical" || topAlert.severity == "High"
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isCritical) EcoRed else EcoBlueSurface),
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.readAlert(topAlert.id)
                        viewModel.askAiGuide("Give me localized expert context for ${topAlert.title}")
                    },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                Icons.Filled.WarningAmber, contentDescription = null,
                                tint = if (isCritical) EcoWhite else EcoBlue, modifier = Modifier.size(22.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    topAlert.hazardType, style = MaterialTheme.typography.titleMedium,
                                    color = if (isCritical) EcoWhite else EcoTextPrimary,
                                )
                                Text(
                                    "for ${topAlert.city}", style = MaterialTheme.typography.bodySmall,
                                    color = if (isCritical) EcoTextOnDark else EcoTextSecondary,
                                )
                            }
                            Text(
                                "${topAlert.severity} Risk", style = MaterialTheme.typography.labelSmall,
                                color = if (isCritical) EcoWhite else EcoBlue,
                            )
                        }
                        Text(
                            topAlert.plainLanguageGuidance.substringBefore("\n").ifBlank { topAlert.plainLanguageGuidance },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCritical) EcoTextOnDark else EcoTextSecondary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            "View Full Guide →", style = MaterialTheme.typography.labelMedium,
                            color = if (isCritical) EcoWhite else EcoBlue, modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        item {
            // Daily challenge: today's litter-report progress, derived from
            // today's logged "Report" activities (real data, not invented).
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            val todaysReports = activities.count { it.category == "Report" && it.timestamp >= startOfDay }
            val goal = 5
            ProgressRow(
                icon = Icons.Filled.DeleteSweep,
                iconBg = EcoOrange,
                title = "Daily Challenge",
                subtitle = "Collect $goal litter reports",
                progress = todaysReports.coerceAtMost(goal) to goal,
                trailingLabel = "+25 XP",
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, EcoDivider),
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectTab("investigate") },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Nearby Community Activity", style = MaterialTheme.typography.titleSmall, color = EcoTextPrimary)
                        Text("See what's happening around you", style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary)
                    }
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(EcoGreenDark),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${reportedIncidents.size}", color = EcoWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            val badges = userProfile?.badgesEarned?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, EcoDivider),
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectTab("map") },
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Recent Rewards", style = MaterialTheme.typography.titleSmall, color = EcoTextPrimary)
                    Text(
                        if (badges.isEmpty()) "Complete actions to earn your first badge" else "You've earned ${badges.size} badges",
                        style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary,
                    )
                    if (badges.isNotEmpty()) {
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            badges.take(4).forEach { BadgeDot(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeDot(name: String) {
    val colors = listOf(EcoGreenPrimary, EcoBlue, EcoOrange, EcoPurple)
    val color = colors[name.hashCode().mod(colors.size)]
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Star, contentDescription = name, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProgressRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    progress: Pair<Int, Int>,
    trailingLabel: String,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, EcoDivider),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconBg.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconBg, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = EcoTextPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary)
                }
                Box(
                    modifier = Modifier
                        .background(EcoGreenSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(trailingLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EcoGreenPrimary)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.first.toFloat() / progress.second.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = EcoGreenPrimary, trackColor = EcoDivider,
            )
            Text(
                "${progress.first}/${progress.second}", style = MaterialTheme.typography.labelSmall,
                color = EcoTextSecondary, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// =============================================================================
// INVESTIGATOR SCREEN — "Choose a Mission" + "Your Missions"
// =============================================================================

@Composable
fun InvestigatorScreen(viewModel: EcoPulseViewModel) {
    val challenges by viewModel.challenges.collectAsState()
    val selectedChallenge by viewModel.selectedChallenge.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    androidx.compose.animation.AnimatedContent(
        targetState = selectedChallenge?.id,
        transitionSpec = {
            (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) +
                androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(220)) { it / 10 })
                .togetherWith(androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(120)))
        },
        label = "mission-detail-transition",
    ) { selectedId ->
        val challenge = challenges.firstOrNull { it.id == selectedId }
        if (challenge != null) {
            ChallengeWorkstation(challenge = challenge, viewModel = viewModel)
        } else {
            MissionListContent(viewModel, challenges, userProfile)
        }
    }
}

@Composable
private fun MissionListContent(
    viewModel: EcoPulseViewModel,
    challenges: List<InvestigationChallenge>,
    userProfile: UserProfile?,
) {
    val available = challenges.filter { !it.isLocked && !it.isCompleted }
    val inProgress = challenges.filter { it.currentStep in 1..3 }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Choose a Mission", style = MaterialTheme.typography.headlineSmall, color = EcoTextPrimary)
            Box(
                modifier = Modifier.background(EcoAmber, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("⭐ ${userProfile?.points ?: 0} XP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoNavyDarker)
            }
        }
        Text(
            "Be a real life eco-detective!", style = MaterialTheme.typography.bodySmall,
            color = EcoTextSecondary, modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(available) { challenge -> MissionCard(challenge) { viewModel.selectChallenge(challenge) } }

            item {
                Text(
                    "Your Missions", style = MaterialTheme.typography.titleMedium,
                    color = EcoTextPrimary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            if (inProgress.isNotEmpty()) {
                items(inProgress) { challenge -> InProgressMissionCard(challenge) { viewModel.selectChallenge(challenge) } }
            } else {
                item {
                    EcoEmptyState(
                        title = "No active missions yet",
                        subtitle = "Pick a mission above to start your first OSINT audit — Eco will walk you through each step.",
                    )
                }
            }
        }
    }
}

private fun missionIcon(type: String): ImageVector = when (type) {
    "Illegal Dumping" -> Icons.Filled.Delete
    "Deforestation" -> Icons.Filled.Forest
    "Unauthorized Mining" -> Icons.Filled.Terrain
    else -> Icons.Filled.Warning
}

private fun missionColor(type: String): Color = when (type) {
    "Illegal Dumping" -> EcoOrange
    "Deforestation" -> EcoGreenPrimary
    "Unauthorized Mining" -> EcoRed
    else -> EcoBlue
}

@Composable
private fun MissionCard(challenge: InvestigationChallenge, onClick: () -> Unit) {
    val color = missionColor(challenge.type)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EcoNavyDark),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(missionIcon(challenge.type), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(challenge.type, style = MaterialTheme.typography.titleSmall, color = EcoWhite)
                Text(
                    challenge.description, style = MaterialTheme.typography.bodySmall,
                    color = EcoTextOnDarkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("+${challenge.pointsReward} XP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoGreenLight)
        }
    }
}

@Composable
private fun InProgressMissionCard(challenge: InvestigationChallenge, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, EcoDivider),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = challenge.satelliteImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(challenge.type, style = MaterialTheme.typography.titleSmall, color = EcoTextPrimary)
                Text(challenge.location, style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { challenge.currentStep / 4f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = EcoGreenPrimary, trackColor = EcoDivider,
                )
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Continue", fontSize = 10.sp) }
        }
    }
}

// =============================================================================
// CHALLENGES SCREEN — Daily/Weekly/Monthly action + quiz progress
// =============================================================================

private data class DailyAction(val title: String, val points: Int, val icon: ImageVector, val goal: Int)

private val DAILY_ACTIONS = listOf(
    DailyAction("Collect 5 litter reports", 25, Icons.Filled.DeleteSweep, 5),
    DailyAction("Save 10L of water", 20, Icons.Filled.WaterDrop, 10),
    DailyAction("Use public transport", 15, Icons.Filled.DirectionsBus, 1),
    DailyAction("Plant a tree", 30, Icons.Filled.Park, 1),
    DailyAction("Share an eco tip", 10, Icons.Filled.Share, 1),
)

@Composable
fun ChallengesScreen(viewModel: EcoPulseViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val activities by viewModel.activities.collectAsState()
    var period by remember { mutableStateOf("Daily") }

    val windowStart = remember(period) {
        val cal = Calendar.getInstance()
        when (period) {
            "Weekly" -> cal.add(Calendar.DAY_OF_YEAR, -7)
            "Monthly" -> cal.add(Calendar.MONTH, -1)
            else -> { cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0) }
        }
        cal.timeInMillis
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Challenges", style = MaterialTheme.typography.headlineSmall, color = EcoTextPrimary)
            Box(modifier = Modifier.background(EcoAmber, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("⭐ ${userProfile?.points ?: 0} XP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoNavyDarker)
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Daily", "Weekly", "Monthly").forEach { p ->
                val selected = period == p
                Box(
                    modifier = Modifier
                        .background(if (selected) EcoGreenPrimary else EcoGreenSurface, RoundedCornerShape(20.dp))
                        .clickable { period = p }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(p, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) EcoWhite else EcoGreenPrimary)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = EcoBlue),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Keep it up!", style = MaterialTheme.typography.titleMedium, color = EcoWhite)
                            Text("Complete challenges and earn amazing rewards.", style = MaterialTheme.typography.bodySmall, color = EcoWhite.copy(alpha = 0.9f))
                        }
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = EcoAmber, modifier = Modifier.size(32.dp))
                    }
                }
            }

            items(DAILY_ACTIONS) { action ->
                val done = activities.count {
                    it.title.contains(action.title.split(" ").last(), ignoreCase = true) && it.timestamp >= windowStart
                }.coerceAtMost(action.goal)
                ChallengeRow(action, done) {
                    if (done < action.goal) viewModel.logDailyAction(action.title, action.points)
                }
            }
        }
    }
}

@Composable
private fun ChallengeRow(action: DailyAction, done: Int, onLog: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, EcoDivider),
        modifier = Modifier.fillMaxWidth().clickable(enabled = done < action.goal) { onLog() },
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(EcoGreenSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, contentDescription = null, tint = EcoGreenPrimary, modifier = Modifier.size(16.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(action.title, style = MaterialTheme.typography.bodyMedium, color = EcoTextPrimary)
                LinearProgressIndicator(
                    progress = { done.toFloat() / action.goal.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = EcoGreenPrimary, trackColor = EcoDivider,
                )
                Text("$done/${action.goal}", fontSize = 9.sp, color = EcoTextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
            if (done >= action.goal) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = EcoGreenPrimary, modifier = Modifier.size(18.dp))
            } else {
                Text("+${action.points} XP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EcoOrange)
            }
        }
    }
}

// =============================================================================
// IMPACT SCREEN
// =============================================================================

@Composable
fun ImpactScreen(viewModel: EcoPulseViewModel) {
    val activities by viewModel.activities.collectAsState()

    // Impact totals are demo heuristics derived from real logged activities
    // (no dedicated CO2/water/tree fields exist server-side yet).
    // TODO: once server.py tracks real environmental-impact units per
    // activity category, replace these derived counts with the real sums.
    val investigations = activities.count { it.category == "Investigation" }
    val litterReports = activities.count { it.category == "Report" }
    val waterLogged = activities.count { it.title.contains("water", ignoreCase = true) }
    val publicTransport = activities.count { it.title.contains("transport", ignoreCase = true) }
    val treesProtected = activities.count { it.category == "Investigation" && it.title.contains("Finalized") }

    val co2Kg = investigations * 3 + litterReports * 1 + publicTransport * 2
    val waterLitres = waterLogged * 10

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("My Impact", style = MaterialTheme.typography.headlineSmall, color = EcoTextPrimary)
                Text("This Month ▾", style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary)
            }
            Text("This is your positive impact", style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary)
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = EcoGreenDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ImpactStat("${co2Kg}kg", "CO₂ Reduced")
                    ImpactStat("${waterLitres}L", "Water Saved")
                    ImpactStat("$treesProtected", "Trees Protected")
                }
            }
        }

        item {
            Text("Impact Breakdown", style = MaterialTheme.typography.titleMedium, color = EcoTextPrimary)
        }

        val breakdown = listOf(
            Triple("Investigations", investigations, "+${investigations * 2}kg CO₂"),
            Triple("Litter Reports", litterReports, "+${litterReports}kg CO₂"),
            Triple("Water Saved", waterLogged, "+${waterLogged * 2}kg CO₂"),
            Triple("Public Transport", publicTransport, "+${publicTransport}kg CO₂"),
        )
        items(breakdown) { (label, count, co2) ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, EcoDivider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.BarChart, contentDescription = null, tint = EcoGreenPrimary, modifier = Modifier.size(16.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = EcoTextPrimary)
                            Text("$count logged", style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary)
                        }
                    }
                    Text(co2, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoGreenPrimary)
                }
            }
        }
    }
}

@Composable
private fun ImpactStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Public, contentDescription = null, tint = EcoGreenLight, modifier = Modifier.size(20.dp))
        Text(value, color = EcoWhite, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        Text(label, color = EcoTextOnDarkMuted, style = MaterialTheme.typography.labelSmall)
    }
}

// =============================================================================
// PROFILE SCREEN — level/XP, stats, achievements, streak
// =============================================================================

@Composable
fun ProfileScreen(viewModel: EcoPulseViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val activities by viewModel.activities.collectAsState()
    val quizQuestions by viewModel.quizQuestions.collectAsState()
    val leaderboard by viewModel.leaderboard.collectAsState()

    // Streak: consecutive days (ending today or yesterday) with >=1 logged
    // activity — a genuine derived metric from real timestamps, not invented.
    val (currentStreak, longestStreak) = remember(activities) { computeStreaks(activities) }
    val rank = leaderboard.firstOrNull { it.isCurrentUser }?.rank

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = EcoNavyDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(EcoGreenPrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(userProfile?.name?.take(2)?.uppercase() ?: "EW", color = EcoWhite, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userProfile?.name ?: "Eco Warrior", style = MaterialTheme.typography.titleLarge, color = EcoWhite)
                            Box(modifier = Modifier.background(EcoGreenPrimary, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("Eco Ranger", fontSize = 9.sp, color = EcoWhite, fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = EcoTextOnDarkMuted)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Level ${userProfile?.level ?: 1}", color = EcoWhite, style = MaterialTheme.typography.titleMedium)
                    val xpIntoLevel = (userProfile?.points ?: 0) % 300
                    Text("$xpIntoLevel / 300 XP", color = EcoTextOnDarkMuted, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { xpIntoLevel / 300f },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = EcoGreenLight, trackColor = EcoNavyDarker,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ProfileStat("${activities.count { it.category == "Investigation" }}", "Investigations")
                        ProfileStat("${(userProfile?.badgesEarned ?: "").split(",").filter { it.isNotBlank() }.size}", "Badges")
                        ProfileStat(rank?.let { "#$it" } ?: "—", "Leaderboard")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Achievements", style = MaterialTheme.typography.titleMedium, color = EcoTextPrimary)
                Text("View All", style = MaterialTheme.typography.bodySmall, color = EcoGreenPrimary)
            }
        }
        item {
            val badges = (userProfile?.badgesEarned ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(badges.ifEmpty { listOf("Start earning badges") }) { badge ->
                    Column(
                        modifier = Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BadgeDot(badge)
                        Text(badge, fontSize = 9.sp, color = EcoTextSecondary, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = EcoNavyDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = EcoOrange, modifier = Modifier.size(18.dp))
                        Text("Your Streak", style = MaterialTheme.typography.titleSmall, color = EcoWhite)
                    }
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                        Text("$currentStreak", color = EcoWhite, style = MaterialTheme.typography.displaySmall)
                        Text(" Days", color = EcoTextOnDarkMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    StreakWeekRow(activities)
                    Text(
                        "Longest Streak: $longestStreak Days", style = MaterialTheme.typography.bodySmall,
                        color = EcoTextOnDarkMuted, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item {
            listOf(
                "My Impact" to Icons.Filled.Public,
                "Leaderboard" to Icons.Filled.Leaderboard,
                "My Activity" to Icons.Filled.History,
                "Saved Places" to Icons.Filled.Place,
                "Settings & Privacy" to Icons.Filled.Settings,
            ).forEach { (label, icon) ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { if (label == "My Impact") viewModel.selectTab("impact") }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = EcoTextSecondary, modifier = Modifier.size(18.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = EcoTextPrimary)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EcoTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = EcoWhite, style = MaterialTheme.typography.titleMedium)
        Text(label, color = EcoTextOnDarkMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StreakWeekRow(activities: List<UserActivity>) {
    val dayMillis = TimeUnit.DAYS.toMillis(1)
    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    val startOfWeek = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY).let { if (it < 0) it + 7 else it }) }
    val activeDays = activities.map { it.timestamp / dayMillis }.toSet()
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")

    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) {
            val dayStart = (startOfWeek.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }.timeInMillis
            val active = (dayStart / dayMillis) in activeDays
            val isFuture = dayStart > System.currentTimeMillis()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(labels[i], fontSize = 9.sp, color = EcoTextOnDarkMuted)
                Box(
                    modifier = Modifier.padding(top = 4.dp).size(22.dp).clip(CircleShape)
                        .background(if (active) EcoGreenPrimary else EcoNavyDarker),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = EcoWhite, modifier = Modifier.size(12.dp))
                    else if (!isFuture) Icon(Icons.Filled.Close, contentDescription = null, tint = EcoTextOnDarkMuted, modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

private fun computeStreaks(activities: List<UserActivity>): Pair<Int, Int> {
    if (activities.isEmpty()) return 0 to 0
    val dayMillis = TimeUnit.DAYS.toMillis(1)
    val activeDays = activities.map { it.timestamp / dayMillis }.toSortedSet()
    val todayDay = System.currentTimeMillis() / dayMillis

    var current = 0
    var day = todayDay
    // allow the streak to still count if today has no activity yet but yesterday does
    if (!activeDays.contains(day)) day -= 1
    while (activeDays.contains(day)) { current++; day-- }

    var longest = 0
    var run = 0
    var prev: Long? = null
    for (d in activeDays) {
        run = if (prev != null && d == prev + 1) run + 1 else 1
        longest = maxOf(longest, run)
        prev = d
    }
    return current to longest
}

// =============================================================================
// ALERTS SCREEN — Climate Alerts (reached from the Home bell icon)
// =============================================================================

@Composable
fun AlertsScreen(viewModel: EcoPulseViewModel, onBack: () -> Unit) {
    val hazardAlerts by viewModel.hazardAlerts.collectAsState()
    var filter by remember { mutableStateOf("Active") }

    val filtered = when (filter) {
        "Active" -> hazardAlerts.filter { it.severity == "Critical" || it.severity == "High" }
        else -> hazardAlerts
    }
    val hero = filtered.firstOrNull() ?: hazardAlerts.firstOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Back", tint = EcoTextPrimary,
                modifier = Modifier.size(18.dp).clickable { onBack() })
            Text("Climate Alerts", style = MaterialTheme.typography.titleLarge, color = EcoTextPrimary)
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = EcoTextSecondary, modifier = Modifier.size(20.dp))
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Active", "Today", "Upcoming").forEach { f ->
                val selected = filter == f
                Box(
                    modifier = Modifier.background(if (selected) EcoGreenPrimary else EcoGreenSurface, RoundedCornerShape(20.dp))
                        .clickable { filter = f }.padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(f, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) EcoWhite else EcoGreenPrimary)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hero != null) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = EcoRed)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Cloud, contentDescription = null, tint = EcoWhite, modifier = Modifier.size(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(hero.title, style = MaterialTheme.typography.titleMedium, color = EcoWhite)
                                    Row {
                                        Text("${hero.severity} Risk", fontSize = 10.sp, color = EcoWhite, fontWeight = FontWeight.Bold)
                                        Text("   Today", fontSize = 10.sp, color = EcoWhite.copy(alpha = 0.8f))
                                    }
                                }
                            }
                            Text("What to do", style = MaterialTheme.typography.titleSmall, color = EcoWhite, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                            hero.plainLanguageGuidance.split("•", "\n").map { it.trim() }.filter { it.isNotEmpty() }.take(3).forEach { line ->
                                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EcoWhite, modifier = Modifier.size(14.dp))
                                    Text(line, fontSize = 12.sp, color = EcoWhite, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            Button(
                                onClick = { viewModel.readAlert(hero.id); viewModel.askAiGuide("Give me the full guide for ${hero.title}") },
                                colors = ButtonDefaults.buttonColors(containerColor = EcoWhite),
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("View Full Guide", color = EcoRed, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            item {
                // TODO: UV index / air quality aren't wired into weather.py yet
                // (only Open-Meteo forecast + flood are). Placeholder cards
                // until that endpoint exists.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniInfoCard(Icons.Filled.WbSunny, EcoOrange, "High UV Index", "Moderate Risk", Modifier.weight(1f))
                    MiniInfoCard(Icons.Filled.Air, EcoGreenPrimary, "Air Quality", "Good · AQI 42", Modifier.weight(1f))
                }
            }

            items(hazardAlerts.filter { it.id != hero?.id }) { alert ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, EcoDivider),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.readAlert(alert.id) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = EcoOrange, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(alert.title, style = MaterialTheme.typography.bodyMedium, color = EcoTextPrimary)
                            Text(alert.city, style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary)
                        }
                        Text(alert.severity, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EcoOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniInfoCard(icon: ImageVector, color: Color, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, EcoDivider),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = EcoTextPrimary, modifier = Modifier.padding(top = 6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary)
        }
    }
}
