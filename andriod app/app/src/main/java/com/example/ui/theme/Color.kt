package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// EcoPulse brand palette — taken from the "Design System" swatches in the
// mascot mockup (green / dark-navy / red / amber / teal / purple).
// Replaces the unused Material template Purple/Pink scheme below (kept only
// so nothing else in the project that still references Purple40 etc. breaks
// at compile time — safe to delete once you've confirmed nothing uses them).
// ---------------------------------------------------------------------------

// Greens
val EcoGreenDark = Color(0xFF0F3D1E)      // deep forest — dark card headers (Eco Score, hero cards)
val EcoGreenPrimary = Color(0xFF1E7D32)   // primary brand green — buttons, active nav icon
val EcoGreenMedium = Color(0xFF2E7D32)
val EcoGreenLight = Color(0xFFB7F397)     // accent chip / progress-bar fill
val EcoGreenSurface = Color(0xFFE8F5E9)   // light green card background
val EcoGreenPale = Color(0xFFF3F8EF)      // screen background

// Navy/dark surfaces (Investigator, Profile, Streak cards)
val EcoNavyDark = Color(0xFF122A22)
val EcoNavyDarker = Color(0xFF0B1F19)

// Status / accent colors
val EcoRed = Color(0xFFE5433D)
val EcoRedSurface = Color(0xFFFCE3E1)
val EcoOrange = Color(0xFFFFA733)
val EcoAmber = Color(0xFFFFC94A)
val EcoBlue = Color(0xFF2FA9E8)
val EcoBlueSurface = Color(0xFFE3F3FC)
val EcoTeal = Color(0xFF2AC0CE)
val EcoPurple = Color(0xFF8E6ADE)

// Neutrals
val EcoTextPrimary = Color(0xFF191C19)
val EcoTextSecondary = Color(0xFF5B6B5E)
val EcoTextOnDark = Color(0xFFE7F0E9)
val EcoTextOnDarkMuted = Color(0xFFAEC2B3)
val EcoDivider = Color(0xFFDCE7D0)
val EcoWhite = Color(0xFFFFFFFF)

// --- legacy (unused, retained only for compile-safety) ---
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
