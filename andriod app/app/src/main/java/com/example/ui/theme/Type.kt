package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Poppins — matches the "Aa Poppins / Bold / Medium / Regular / Light" panel
// in the design system mockup.
//
// SETUP (one-time): download the 4 weights from
//   https://fonts.google.com/specimen/Poppins
// and drop the .ttf files into app/src/main/res/font/ named exactly:
//   poppins_light.ttf, poppins_regular.ttf, poppins_medium.ttf,
//   poppins_semibold.ttf, poppins_bold.ttf
// then uncomment the Font(...) block below and swap `Poppins` for
// `PoppinsLoaded` in Typography.
//
// Shipping with FontFamily.Default for now so this compiles out of the box
// without requiring the font files to be present.
// ---------------------------------------------------------------------------

val Poppins = FontFamily.Default

// Once font files are added, replace the line above with:
//
// import androidx.compose.ui.text.font.Font
// import com.example.R
//
// val Poppins = FontFamily(
//     Font(R.font.poppins_light, FontWeight.Light),
//     Font(R.font.poppins_regular, FontWeight.Normal),
//     Font(R.font.poppins_medium, FontWeight.Medium),
//     Font(R.font.poppins_semibold, FontWeight.SemiBold),
//     Font(R.font.poppins_bold, FontWeight.Bold),
// )

val Typography = Typography(
    displaySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Light, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 13.sp),
)
