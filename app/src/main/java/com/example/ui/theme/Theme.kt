package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode {
  SYSTEM,
  LIGHT,
  DARK
}

private val RomanticDarkColorScheme =
  darkColorScheme(
    primary = RosePrimaryDark,
    onPrimary = RoseOnPrimaryDark,
    primaryContainer = RosePrimaryContainerDark,
    onPrimaryContainer = RoseOnPrimaryContainerDark,
    secondary = RoseSecondaryDark,
    onSecondary = RoseOnSecondaryDark,
    secondaryContainer = RoseSecondaryContainerDark,
    onSecondaryContainer = RoseOnSecondaryContainerDark,
    tertiary = GoldTertiaryDark,
    background = RoseBackgroundDark,
    surface = RoseSurfaceDark,
    surfaceVariant = RoseSurfaceVariantDark,
    onSurface = RoseOnSurfaceDark,
    onSurfaceVariant = RoseOnSurfaceVariantDark,
    outline = RoseOutlineDark
  )

private val RomanticLightColorScheme =
  lightColorScheme(
    primary = RosePrimaryLight,
    onPrimary = RoseOnPrimaryLight,
    primaryContainer = RosePrimaryContainerLight,
    onPrimaryContainer = RoseOnPrimaryContainerLight,
    secondary = RoseSecondaryLight,
    onSecondary = RoseOnSecondaryLight,
    secondaryContainer = RoseSecondaryContainerLight,
    onSecondaryContainer = RoseOnSecondaryContainerLight,
    tertiary = GoldTertiaryLight,
    background = RoseBackgroundLight,
    surface = RoseSurfaceLight,
    surfaceVariant = RoseSurfaceVariantLight,
    onSurface = RoseOnSurfaceLight,
    onSurfaceVariant = RoseOnSurfaceVariantLight,
    outline = RoseOutlineLight
  )

@Composable
fun MimuLoveAppTheme(
  themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val isDark = when (themeMode) {
    AppThemeMode.SYSTEM -> systemDark
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
  }

  val colorScheme = if (isDark) RomanticDarkColorScheme else RomanticLightColorScheme

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        // Light icons in dark mode, dark icons (clock, battery, wifi) in light mode!
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
