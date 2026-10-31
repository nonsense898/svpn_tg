package com.wgvpn.core.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class AppThemeStyle {
    TELEGRAM, MILITARY, REGULATORY
}

val LocalAppThemeStyle = staticCompositionLocalOf<AppThemeStyle> { AppThemeStyle.TELEGRAM }

@Composable
fun adaptiveShape(radius: Int): RoundedCornerShape {
    val style = LocalAppThemeStyle.current
    return when (style) {
        AppThemeStyle.TELEGRAM -> RoundedCornerShape(radius.dp)
        AppThemeStyle.MILITARY -> RoundedCornerShape(2.dp)
        AppThemeStyle.REGULATORY -> RoundedCornerShape(0.dp)
    }
}


fun getAppThemeFromManifest(context: Context): AppThemeStyle {
    return try {
        val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val bundle = ai.metaData
        val themeString = bundle?.getString("app_theme") ?: "telegram"
        when (themeString.lowercase()) {
            "military" -> AppThemeStyle.MILITARY
            "regulatory" -> AppThemeStyle.REGULATORY
            else -> AppThemeStyle.TELEGRAM
        }
    } catch (e: Exception) {
        AppThemeStyle.TELEGRAM
    }
}

// 1. Telegram Theme (Light, airy, blue accents, rounded)
private val TelegramLightColorScheme = lightColorScheme(
    primary = Color(0xFF2AABEE),
    onPrimary = Color.White,
    background = Color(0xFFF4F4F5),
    surface = Color.White,
    onBackground = Color(0xFF222222),
    onSurface = Color(0xFF222222),
    secondaryContainer = Color(0xFFE5F6FF),
    onSecondaryContainer = Color(0xFF2AABEE)
)
private val TelegramTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

// 2. Military Theme (Dark, olive, rigid, tactical)
private val MilitaryColorScheme = darkColorScheme(
    primary = Color(0xFF8B9B74), // Olive accent
    onPrimary = Color.Black,
    background = Color(0xFF1C1E18), // Dark tactical
    surface = Color(0xFF252821),
    onBackground = Color(0xFFE2E8D5),
    onSurface = Color(0xFFE2E8D5),
    secondaryContainer = Color(0xFF32362C),
    onSecondaryContainer = Color(0xFF8B9B74)
)
private val MilitaryTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
)

// 3. Regulatory Theme (Strict, formal, white/gray, minimal curves)
private val RegulatoryLightColorScheme = lightColorScheme(
    primary = Color(0xFF0033A0), // Deep formal blue
    onPrimary = Color.White,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF9F9F9),
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
    secondaryContainer = Color(0xFFEAEAEA),
    onSecondaryContainer = Color(0xFF0033A0),
    error = Color(0xFFDA291C) // Formal red
)
private val RegulatoryTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

@Composable
fun VPNAppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeStyle = remember { getAppThemeFromManifest(context) }

    val colorScheme = when (themeStyle) {
        AppThemeStyle.TELEGRAM -> TelegramLightColorScheme
        AppThemeStyle.MILITARY -> MilitaryColorScheme
        AppThemeStyle.REGULATORY -> RegulatoryLightColorScheme
    }
    
    val typography = when (themeStyle) {
        AppThemeStyle.TELEGRAM -> TelegramTypography
        AppThemeStyle.MILITARY -> MilitaryTypography
        AppThemeStyle.REGULATORY -> RegulatoryTypography
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val isLight = themeStyle == AppThemeStyle.TELEGRAM || themeStyle == AppThemeStyle.REGULATORY
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
        }
    }

    CompositionLocalProvider(LocalAppThemeStyle provides themeStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
