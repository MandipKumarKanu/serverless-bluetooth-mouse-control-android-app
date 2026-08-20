package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Expressive M3 Primary & Accents
val IndigoPrimary = Color(0xFF6366F1) // Electric Indigo
val IndigoLight = Color(0xFF818CF8)  // Bright Indigo
val IndigoDark = Color(0xFF4F46E5)   // Deep Indigo

val CyanAccent = Color(0xFF06B6D4)   // Cyber Cyan
val CyanLight = Color(0xFF22D3EE)    // Neon Cyan
val CyanDark = Color(0xFF0891B2)     // Dark Cyan

val PinkAccent = Color(0xFFEC4899)   // Sunset Pink
val PinkLight = Color(0xFFF472B6)    // Neon Pink
val PinkDark = Color(0xFFDB2777)     // Deep Pink

// Dark Theme (Obsidian & Charcoal Slate)
val DarkBackground = Color(0xFF030712) // Deep Obsidian
val DarkSurface = Color(0xFF0B0F19)    // Midnight Space
val DarkSurfaceVariant = Color(0xFF111827) // Elevated Slate Card
val DarkOnBackground = Color(0xFFF9FAFB)
val DarkOnSurface = Color(0xFFF9FAFB)
val DarkOnSurfaceVariant = Color(0xFF9CA3AF) // Slate Text Muted
val DarkBorder = Color(0xFF1F2937)     // Clean Border Edge
val DarkError = Color(0xFFEF4444)
val DarkErrorContainer = Color(0xFF450A0A)
val DarkSuccess = Color(0xFF10B981)
val DarkSuccessContainer = Color(0xFF064E3B)

// Light Theme (Clean Premium Slate)
val LightBackground = Color(0xFFF8FAFC) // Slate 50
val LightSurface = Color(0xFFFFFFFF)    // Pure White Card
val LightSurfaceVariant = Color(0xFFF1F5F9) // Slate 100
val LightOnBackground = Color(0xFF0F172A)   // Slate 900
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceVariant = Color(0xFF475569) // Slate 600
val LightBorder = Color(0xFFE2E8F0)     // Slate 200
val LightError = Color(0xFFDC2626)
val LightErrorContainer = Color(0xFFFEE2E2)
val LightSuccess = Color(0xFF059669)
val LightSuccessContainer = Color(0xFFD1FAE5)

// ── Semantic status colors (used across screens) ────────────────────
// These replace the 43 hardcoded Color(0xFF...) values scattered
// across DashboardScreen, AirMouseScreen, StickyConnectionIndicator,
// GamepadScreen, TouchpadScreen, and PresentationScreen.

// Connected state (green)
val StatusConnected = Color(0xFF10B981)           // text / icon tint
val StatusConnectedContainer = Color(0xFF064E3B)  // card background (dark)

// Connecting state (amber)
val StatusConnecting = Color(0xFFF59E0B)           // text / icon tint
val StatusConnectingContainer = Color(0xFF451A03)  // card background (dark)

// Error / disconnected state (red)
val StatusError = Color(0xFFEF4444)

// Gamepad button colors
val GamepadA = Color(0xFF10B981)  // Green
val GamepadB = Color(0xFFEF4444)  // Red
val GamepadX = Color(0xFF3B82F6)  // Blue
val GamepadY = Color(0xFFF59E0B)  // Amber

// Control-mode tile accent colors
val TileTouchpad = Color(0xFF3B82F6)   // Blue
val TileAirMouse = Color(0xFF10B981)    // Green
val TileKeyboard = Color(0xFFF59E0B)    // Amber
val TileMediaRemote = Color(0xFFEF4444) // Red
val TilePresentation = Color(0xFF8B5CF6) // Purple
val TileShortcuts = Color(0xFFEC4899)    // Pink
val TileGamepad = Color(0xFF06B6D4)      // Cyan
val TileGestures = Color(0xFFFF6B35)     // Orange
