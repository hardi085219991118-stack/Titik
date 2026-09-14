package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = FirePrimaryDark,
  onPrimary = FireOnPrimaryDark,
  primaryContainer = FirePrimaryContainerDark,
  onPrimaryContainer = FireOnPrimaryContainerDark,
  secondary = FireSecondaryDark,
  onSecondary = FireOnSecondaryDark,
  secondaryContainer = FireSecondaryContainerDark,
  onSecondaryContainer = FireOnSecondaryContainerDark,
  tertiary = FireTertiaryDark,
  onTertiary = FireOnTertiaryDark,
  background = FireBackgroundDark,
  surface = FireSurfaceDark,
  surfaceVariant = FireSurfaceVariantDark,
  onBackground = FireOnSurfaceDark,
  onSurface = FireOnSurfaceDark,
  onSurfaceVariant = FireOnSurfaceVariantDark,
  outline = FireOutlineDark
)

private val LightColorScheme = lightColorScheme(
  primary = FirePrimaryLight,
  onPrimary = FireOnPrimaryLight,
  primaryContainer = FirePrimaryContainerLight,
  onPrimaryContainer = FireOnPrimaryContainerLight,
  secondary = FireSecondaryLight,
  onSecondary = FireOnSecondaryLight,
  secondaryContainer = FireSecondaryContainerLight,
  onSecondaryContainer = FireOnSecondaryContainerLight,
  tertiary = FireTertiaryLight,
  onTertiary = FireOnTertiaryLight,
  background = FireBackgroundLight,
  surface = FireSurfaceLight,
  surfaceVariant = FireSurfaceVariantLight,
  onBackground = FireOnSurfaceLight,
  onSurface = FireOnSurfaceLight,
  onSurfaceVariant = FireOnSurfaceVariantLight,
  outline = FireOutlineLight
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Keep high-contrast telemetry colors by default
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
