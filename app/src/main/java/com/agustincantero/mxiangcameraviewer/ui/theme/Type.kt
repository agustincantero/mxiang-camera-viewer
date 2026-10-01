package com.agustincantero.mxiangcameraviewer.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

// The Material 3 type scale in Roboto; nothing smaller than 14 sp is used on screen, since a TV is read from across the room.
@OptIn(ExperimentalTvMaterial3Api::class)
val Typography = Typography(
    headlineMedium = style(28, 36),
    headlineSmall = style(24, 32),
    titleLarge = style(22, 28),
    titleMedium = style(16, 24, FontWeight.Medium, 0.15),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1),
    bodyLarge = style(16, 24, spacing = 0.5),
    bodyMedium = style(14, 20, spacing = 0.25),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
)
