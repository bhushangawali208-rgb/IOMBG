package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

private val DarkColorScheme = darkColorScheme(
  primary = IombgRed,
  onPrimary = Color.White,
  primaryContainer = IombgRedDark,
  onPrimaryContainer = Color.White,
  secondary = IombgCoral,
  onSecondary = Color.White,
  secondaryContainer = DarkSurfaceElevated,
  onSecondaryContainer = DarkTextPrimary,
  tertiary = IombgGold,
  background = DarkBackground,
  onBackground = DarkTextPrimary,
  surface = DarkBackground,
  onSurface = DarkTextPrimary,
  surfaceVariant = DarkSurfaceElevated,
  onSurfaceVariant = DarkTextSecondary,
  outline = DarkBorder,
  outlineVariant = DarkBorderSubtle,
  error = StatusError
)

private val LightColorScheme = lightColorScheme(
  primary = IombgRed,
  onPrimary = LightSurface,
  primaryContainer = IombgCoral.copy(alpha = 0.2f),
  onPrimaryContainer = IombgRedDark,
  secondary = IombgRedDark,
  onSecondary = LightSurface,
  secondaryContainer = LightSurfaceVariant,
  onSecondaryContainer = LightTextPrimary,
  tertiary = IombgGold,
  background = LightBackground,
  onBackground = LightTextPrimary,
  surface = LightSurface,
  onSurface = LightTextPrimary,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightTextSecondary,
  outline = LightBorder,
  error = StatusError
)

@Composable
fun IombgTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  MyApplicationTheme(darkTheme = darkTheme, content = content)
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = view.context.findActivity()?.window
      if (window != null) {
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
