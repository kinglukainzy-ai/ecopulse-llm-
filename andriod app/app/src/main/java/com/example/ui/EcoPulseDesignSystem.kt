package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

// =============================================================================
// TOKENS — the shared vocabulary. Screens should reach for these instead of
// re-typing `RoundedCornerShape(20.dp)` / `BorderStroke(1.dp, EcoDivider)` in
// every composable, which is what made the first pass feel like a retrofit
// (correct shapes, but no single system tying them together).
// =============================================================================

object EcoSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object EcoShape {
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(999.dp)
}

/**
 * Three-level elevation system so hierarchy reads at a glance instead of
 * every card sitting at the same flat plane:
 *   Flat   — list rows, secondary content. Border only, no shadow.
 *   Raised — standard section cards (mission cards, breakdown rows' parent).
 *   Hero   — the one "headline" card per screen (Eco Score, Impact globe,
 *            the red alert card, the streak card). Bigger, green-tinted
 *            shadow so it visually leads the screen instead of competing
 *            with everything else at the same weight.
 */
enum class EcoElevation { Flat, Raised, Hero }

fun Modifier.ecoElevation(level: EcoElevation, shape: Shape): Modifier = when (level) {
    EcoElevation.Flat -> this
    EcoElevation.Raised -> this.shadow(
        elevation = 3.dp, shape = shape,
        ambientColor = EcoGreenDark.copy(alpha = 0.10f), spotColor = EcoGreenDark.copy(alpha = 0.14f),
    )
    EcoElevation.Hero -> this.shadow(
        elevation = 10.dp, shape = shape,
        ambientColor = EcoGreenDark.copy(alpha = 0.18f), spotColor = EcoGreenDark.copy(alpha = 0.28f),
    )
}

/**
 * The one card primitive every screen should use. Replaces the old pattern
 * of hand-rolling `Card(shape=..., colors=..., border=BorderStroke(...))`
 * at every call site — that repetition is exactly what made the UI read as
 * patched-together rather than systematized.
 */
@Composable
fun EcoCard(
    modifier: Modifier = Modifier,
    elevation: EcoElevation = EcoElevation.Raised,
    shape: Shape = EcoShape.lg,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .ecoElevation(elevation, shape)
        .clip(shape)
        .background(containerColor)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Column(modifier = base, content = content)
}

// =============================================================================
// MOTION — staggered entrance for list content, used instead of content just
// appearing instantly. Small, cheap, and it's the single biggest thing that
// makes a screen feel considered rather than a raw data dump.
// =============================================================================

object EcoMotion {
    const val staggerStepMs = 45
    val enterSpec = tween<Float>(durationMillis = 260)
}

@Composable
fun EcoStaggeredItem(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * EcoMotion.staggerStepMs).toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = EcoMotion.enterSpec) +
            slideInVertically(animationSpec = tween(260)) { it / 3 },
    ) { content() }
}

// =============================================================================
// EMPTY STATES — the mascot's voice (warm older-sibling, per the Modelfile
// persona) should show up here instead of a blank list. Every screen with a
// list that can legitimately be empty should route through this.
// =============================================================================

@Composable
fun EcoEmptyState(
    title: String,
    subtitle: String,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = EcoSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MascotAvatar(size = 56.dp)
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = EcoTextPrimary,
            modifier = Modifier.padding(top = EcoSpacing.sm),
        )
        Text(
            subtitle, style = MaterialTheme.typography.bodySmall, color = EcoTextSecondary,
            modifier = Modifier.padding(top = 2.dp, start = EcoSpacing.lg, end = EcoSpacing.lg),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (ctaLabel != null && onCta != null) {
            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = EcoShape.pill,
                modifier = Modifier.padding(top = EcoSpacing.md),
            ) {
                Icon(Icons.Filled.Eco, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(ctaLabel, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
