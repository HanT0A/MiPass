package com.hanzg.mipass.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

// ═══════════════════════════════════════════
//  Duration Tokens (milliseconds)
// ═══════════════════════════════════════════

/** Micro-interaction: icon toggle, ripple, checkbox — 150ms */
const val DurationMicro = 150

/** Standard transition: fade, color change, small layout — 200ms */
const val DurationShort = 200

/** Emphasis transition: page enter, modal show, shared-axis — 300ms */
const val DurationMedium = 300

/** Complex transition: multi-element stagger, hero — 400ms */
const val DurationLong = 400

/** Stagger cascade delay per list item — 50ms */
const val StaggerDelay = 50

// ═══════════════════════════════════════════
//  Custom Easing Curves
// ═══════════════════════════════════════════

/** Enter curve — gentle deceleration for natural arrival.
 *  Based on Material "Emphasized Decelerate". */
val MiPassEaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** In-out curve — symmetric acceleration for within-screen transitions. */
val MiPassEaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Exit curve — faster exit so UI feels responsive.
 *  Exit duration should be ~60-70% of enter duration. */
val MiPassEaseIn = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// ═══════════════════════════════════════════
//  Spring Presets (natural feel, respect reduced-motion)
// ═══════════════════════════════════════════

/** Gentle spring for small scale/bounce feedback (buttons, icons). */
val MiPassSpringGentle = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

/** Snappy spring for transitions (modals, sheets). */
val MiPassSpringSnappy = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium
)

/** Stiff spring for precise movements (progress bars, sliders). */
val MiPassSpringStiff = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)
