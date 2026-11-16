package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Mode Cobalt-Blue Productivity Palette
val CobaltPrimaryLight = Color(0xFF1D4ED8)
val OnCobaltPrimaryLight = Color(0xFFFFFFFF)
val CobaltPrimaryContainerLight = Color(0xFFDBEAFE)
val OnCobaltPrimaryContainerLight = Color(0xFF1E3A8A)

val SlateSecondaryLight = Color(0xFF334155)
val OnSlateSecondaryLight = Color(0xFFFFFFFF)
val SlateSecondaryContainerLight = Color(0xFFE2E8F0)
val OnSlateSecondaryContainerLight = Color(0xFF0F172A)

val EmeraldTertiaryLight = Color(0xFF059669)
val OnEmeraldTertiaryLight = Color(0xFFFFFFFF)
val EmeraldTertiaryContainerLight = Color(0xFFD1FAE5)
val OnEmeraldTertiaryContainerLight = Color(0xFF064E3B)

val BackgroundLight = Color(0xFFF8FAFC)
val OnBackgroundLight = Color(0xFF0F172A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val OnSurfaceVariantLight = Color(0xFF475569)
val OutlineLight = Color(0xFFCBD5E1)
val OutlineVariantLight = Color(0xFFE2E8F0)
val ErrorLight = Color(0xFFDC2626)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF7F1D1D)

// Dark Mode Cobalt-Blue Productivity Palette
val CobaltPrimaryDark = Color(0xFF60A5FA)
val OnCobaltPrimaryDark = Color(0xFF0A1E4D)
val CobaltPrimaryContainerDark = Color(0xFF1E3A8A)
val OnCobaltPrimaryContainerDark = Color(0xFFDBEAFE)

val SlateSecondaryDark = Color(0xFF94A3B8)
val OnSlateSecondaryDark = Color(0xFF0F172A)
val SlateSecondaryContainerDark = Color(0xFF1E293B)
val OnSlateSecondaryContainerDark = Color(0xFFE2E8F0)

val EmeraldTertiaryDark = Color(0xFF34D399)
val OnEmeraldTertiaryDark = Color(0xFF022C22)
val EmeraldTertiaryContainerDark = Color(0xFF064E3B)
val OnEmeraldTertiaryContainerDark = Color(0xFFD1FAE5)

val BackgroundDark = Color(0xFF0B1120)
val OnBackgroundDark = Color(0xFFF1F5F9)
val SurfaceDark = Color(0xFF111827)
val OnSurfaceDark = Color(0xFFF1F5F9)
val SurfaceVariantDark = Color(0xFF1E293B)
val OnSurfaceVariantDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF334155)
val OutlineVariantDark = Color(0xFF1E293B)
val ErrorDark = Color(0xFFF87171)
val ErrorContainerDark = Color(0xFF7F1D1D)
val OnErrorContainerDark = Color(0xFFFEE2E2)

@Immutable
data class ReachHubStatusColors(
  val simulationBannerBg: Color,
  val simulationBannerFg: Color,
  val warningBg: Color,
  val warningFg: Color,
  val deliveredBg: Color,
  val deliveredFg: Color,
  val sentBg: Color,
  val sentFg: Color,
)

val LightStatusColors =
  ReachHubStatusColors(
    simulationBannerBg = Color(0xFFFEF3C7),
    simulationBannerFg = Color(0xFF92400E),
    warningBg = Color(0xFFFFEDD5),
    warningFg = Color(0xFF9A3412),
    deliveredBg = Color(0xFFD1FAE5),
    deliveredFg = Color(0xFF065F46),
    sentBg = Color(0xFFE0F2FE),
    sentFg = Color(0xFF075985),
  )

val DarkStatusColors =
  ReachHubStatusColors(
    simulationBannerBg = Color(0xFF451A03),
    simulationBannerFg = Color(0xFFFDE68A),
    warningBg = Color(0xFF431407),
    warningFg = Color(0xFFFDBA74),
    deliveredBg = Color(0xFF064E3B),
    deliveredFg = Color(0xFF6EE7B7),
    sentBg = Color(0xFF0C4A6E),
    sentFg = Color(0xFF7DD3FC),
  )

val LocalReachHubStatusColors = staticCompositionLocalOf { LightStatusColors }
