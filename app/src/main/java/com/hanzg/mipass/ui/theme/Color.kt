package com.hanzg.mipass.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════
//  Primary — Slate Navy
//  Desaturated cool navy for security/trust aesthetic.
//  Anchor hue ~210°, matched to app icon #0D47A1 family.
// ═══════════════════════════════════════════

// Light theme — deeper navy for contrast on light surfaces
val SlateNavyPrimary = Color(0xFF305680)
val SlateNavyOnPrimary = Color(0xFFFFFFFF)
val SlateNavyPrimaryContainer = Color(0xFFDAE6F2)
val SlateNavyOnPrimaryContainer = Color(0xFF142133)

// Dark theme — lighter navy for visibility on dark surfaces
val SlateNavyPrimaryDark = Color(0xFF8BAEC8)
val SlateNavyOnPrimaryDark = Color(0xFF08101A)
val SlateNavyPrimaryContainerDark = Color(0xFF1D3347)
val SlateNavyOnPrimaryContainerDark = Color(0xFFDAE6F2)

// ═══════════════════════════════════════════
//  Surface — Cool gray hierarchy (B > R channel for no pink cast)
//  6 distinct levels: Background → Surface → SurfaceVariant
//  + 4 SurfaceContainer tiers for M3 elevation mapping
// ═══════════════════════════════════════════

// ── Light Surface ──
val LightBackground = Color.White
val LightSurface = Color.White
val LightSurfaceVariant = Color(0xFFF0F1F5)
val LightSurfaceContainerLow = Color(0xFFEEF0F5)
val LightSurfaceContainer = Color(0xFFE8EAF2)
val LightSurfaceContainerHigh = Color.White
val LightSurfaceContainerHighest = Color.White

// ── Dark Surface ──
val DarkBackground = Color(0xFF0C0E12)
val DarkSurface = Color(0xFF14171D)
val DarkSurfaceVariant = Color(0xFF1C1F27)
val DarkSurfaceContainerLow = Color(0xFF161820)
val DarkSurfaceContainer = Color(0xFF1B1E26)
val DarkSurfaceContainerHigh = Color(0xFF1F222B)
val DarkSurfaceContainerHighest = Color(0xFF232730)

// ═══════════════════════════════════════════
//  Text — On-surface content colors
//  WCAG AA compliant: ≥ 4.5:1 primary, ≥ 3:1 secondary
// ═══════════════════════════════════════════

// ── Light Text ──
val TextPrimaryLight = Color(0xFF1A1C20)
val TextSecondaryLight = Color(0xFF5A5D66)
val TextTertiaryLight = Color(0xFF787B84)
val TextDisabledLight = Color(0xFFA0A4AF)

// ── Dark Text ──
val TextPrimaryDark = Color(0xFFE5E7EF)
val TextSecondaryDark = Color(0xFF9A9DA8)
val TextTertiaryDark = Color(0xFF6E717B)
val TextDisabledDark = Color(0xFF4A4D56)

// ═══════════════════════════════════════════
//  Outline — Subtle borders and dividers
// ═══════════════════════════════════════════

val OutlineLight = Color(0xFFC9CCD6)
val OutlineVariantLight = Color(0xFFDFE2EB)

val OutlineDark = Color(0xFF2F323A)
val OutlineVariantDark = Color(0xFF22252D)

// ═══════════════════════════════════════════
//  Semantic — Error, Warning
// ═══════════════════════════════════════════

val CoralRed = Color(0xFFD94A3A)
val CoralRedContainer = Color(0xFFFCE4E1)
val CoralRedContainerDark = Color(0xFF4A1A15)

val WarningAmber = Color(0xFFC8910A)
val WarningAmberContainer = Color(0xFFFFF3CD)
val WarningAmberContainerDark = Color(0xFF3D2C00)

val WarningOrange = Color(0xFFC8700A)
val WarningOrangeContainer = Color(0xFFFFE8D0)
val WarningOrangeContainerDark = Color(0xFF3D2000)

// ═══════════════════════════════════════════
//  Scrim — Modal/sheet backdrop
// ═══════════════════════════════════════════

val ScrimLight = Color(0x99000000)
val ScrimDark = Color(0xCC000000)
