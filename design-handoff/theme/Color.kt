package com.example.uzzu.ui.theme

import androidx.compose.ui.graphics.Color

// Uzzu design tokens. Source: design/Tokens.dc.html
object UzzuColors {
    val Background = Color(0xFF000000)    // screen ground, logo slot, text on accent
    val Surface = Color(0xFF0E0E11)       // cards, rows, buttons, dialog
    val Outline = Color(0xFF3A1E4F)       // violet: unfocused frames, rules, key chips
    val Scrim = Color(0xE0000000)         // 88%: tune banner, dialog backdrop
    val OverlayScrim = Color(0xBF000000)  // 75%: player overlay panel (O1)
    val TextPrimary = Color(0xFFE6F0F2)   // names, titles, values
    val TextSecondary = Color(0xFFA99BBE) // mono tags, times, hints, logo icon
    val Active = Color(0xFFB0186E)        // pink fill only: selected tab, active feed, PLAYING, tuned bar
    val Accent = Color(0xFFF5E50A)        // yellow: focus border + LIVE marker ONLY
    val OnAccent = Background
    val Error = Color(0xFFFF5A36)         // error tag, icon, failing values

    const val FinalContentAlpha = 0.4f
}
