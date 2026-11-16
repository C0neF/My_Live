package com.mylive.app.ui.theme

import androidx.compose.ui.graphics.Color

// Neutral system for the V2 redesign. These are FIXED and do not derive from the
// user's seed color — only `primary` (the accent) follows the user's styleColor.
// Values computed from OKLCH; see docs/redesign/plan-ab-hybrid.md.

// Dark — primary identity, near-black content-forward shell.
val DarkBg = Color(0xFF0C0D0F)
val DarkSurface = Color(0xFF17181B)
val DarkElevated = Color(0xFF222427)
val DarkInk = Color(0xFFF0F2F4)
val DarkMuted = Color(0xFFA1A5A9)
val DarkHairline = Color(0xFF2A2C30)
val DarkHairlineStrong = Color(0xFF3A3D42)
val DarkAccent = Color(0xFF58A5E4)

// Light — first-class, non-pure-white neutral.
val LightBg = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFF2F2F2)
val LightInk = Color(0xFF1B1D1F)
val LightMuted = Color(0xFF606468)
val LightHairline = Color(0xFFE4E4E6)
val LightHairlineStrong = Color(0xFFD2D2D4)
val LightAccent = Color(0xFF1777B8)

// Status colors (kept semantic).
val DangerDark = Color(0xFFFFB4AB)
val OnDangerDark = Color(0xFF690005)
val DangerLight = Color(0xFFBA1A1A)
val OnDangerLight = Color(0xFFFFFFFF)
