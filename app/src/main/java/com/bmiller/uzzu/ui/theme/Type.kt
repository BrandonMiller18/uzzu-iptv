package com.bmiller.uzzu.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bmiller.uzzu.R

// Requires res/font/roboto_variable.ttf (wdth 75–100, wght 100–900)
// and res/font/roboto_mono_variable.ttf (wght 100–700), both from Google Fonts.
// Sizes are canvas px ÷ 2. Titles, names, tabs and buttons render UPPERCASE: apply .uppercase() to the string.

@OptIn(ExperimentalTextApi::class)
private fun roboto(weight: Int, width: Float) = FontFamily(
    Font(
        R.font.roboto_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
        ),
    )
)

@OptIn(ExperimentalTextApi::class)
private fun robotoMono(weight: Int) = FontFamily(
    Font(
        R.font.roboto_mono_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
)

object UzzuType {
    // Screen and tile titles. 40/48 px, 800, width 75, +4 px tracking.
    val Display = TextStyle(fontFamily = roboto(800, 75f), fontWeight = FontWeight(800), fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 2.sp)

    // Dialog title, tune banner title. 32/40 px, 700, width 80, +2 px.
    val Title = TextStyle(fontFamily = roboto(700, 80f), fontWeight = FontWeight(700), fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 1.sp)

    // Error summary line (sentence case). 32/40 px, 500.
    val Summary = TextStyle(fontFamily = roboto(500, 100f), fontWeight = FontWeight(500), fontSize = 16.sp, lineHeight = 20.sp)

    // Team and channel names. 28/36 px, 600, width 85, +1 px.
    val BodyLarge = TextStyle(fontFamily = roboto(600, 85f), fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp)

    // Sentences, subtitles. 24/32 px, 400.
    val Body = TextStyle(fontFamily = roboto(400, 100f), fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp)

    // Tabs, buttons. 24/32 px, 700, width 80, +2 px.
    val Label = TextStyle(fontFamily = roboto(700, 80f), fontWeight = FontWeight(700), fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp)

    // LIVE and PLAYING chips. 24/32 px, 800, width 80, +2 px.
    val Chip = TextStyle(fontFamily = roboto(800, 80f), fontWeight = FontWeight(800), fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp)

    // Section tags (// GAMES), times, key hints. 24/32 px mono.
    val Mono = TextStyle(fontFamily = robotoMono(400), fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp)

    // Time / period on game cards. 28/36 px mono 500.
    val MonoLarge = TextStyle(fontFamily = robotoMono(500), fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 18.sp)

    // Error diagnostics block. 24/40 px mono.
    val Diagnostic = TextStyle(fontFamily = robotoMono(400), fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 20.sp)
}
