package com.sdocument.template.vpn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sdocument.template.vpn.R

// D-DIN Font Family (Starlink's official font)
val DINFontFamily = FontFamily(
    Font(R.font.din_regular, FontWeight.Normal),
    Font(R.font.din_regular, FontWeight.Medium),
    Font(R.font.din_bold, FontWeight.SemiBold),
    Font(R.font.din_bold, FontWeight.Bold)
)

// Starlink Typography - минималистичный стиль
val Typography = Typography(
    // Заголовок
    displayLarge = TextStyle(
        fontFamily = DINFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 0.sp
    ),
    // Подзаголовки
    titleLarge = TextStyle(
        fontFamily = DINFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DINFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        letterSpacing = 0.15.sp
    ),
    // Основной текст
    bodyLarge = TextStyle(
        fontFamily = DINFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DINFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    // Кнопки
    labelLarge = TextStyle(
        fontFamily = DINFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    )
)