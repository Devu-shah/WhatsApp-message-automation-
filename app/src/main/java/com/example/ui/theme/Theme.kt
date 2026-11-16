package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val ReachHubLightColorScheme =
  lightColorScheme(
    primary = CobaltPrimaryLight,
    onPrimary = OnCobaltPrimaryLight,
    primaryContainer = CobaltPrimaryContainerLight,
    onPrimaryContainer = OnCobaltPrimaryContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = OnSlateSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = OnSlateSecondaryContainerLight,
    tertiary = EmeraldTertiaryLight,
    onTertiary = OnEmeraldTertiaryLight,
    tertiaryContainer = EmeraldTertiaryContainerLight,
    onTertiaryContainer = OnEmeraldTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
  )

private val ReachHubDarkColorScheme =
  darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = OnCobaltPrimaryDark,
    primaryContainer = CobaltPrimaryContainerDark,
    onPrimaryContainer = OnCobaltPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = OnSlateSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = OnSlateSecondaryContainerDark,
    tertiary = EmeraldTertiaryDark,
    onTertiary = OnEmeraldTertiaryDark,
    tertiaryContainer = EmeraldTertiaryContainerDark,
    onTertiaryContainer = OnEmeraldTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
  )

val ReachHubShapes =
  Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

@Composable
fun ReachHubTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) ReachHubDarkColorScheme else ReachHubLightColorScheme
  val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

  CompositionLocalProvider(LocalReachHubStatusColors provides statusColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = ReachHubTypography,
      shapes = ReachHubShapes,
      content = content,
    )
  }
}
