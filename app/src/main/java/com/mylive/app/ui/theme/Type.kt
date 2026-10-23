package com.mylive.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

// Keep sp units and system font scaling; larger windows need readable supporting text too.
val TabletTypography = AppTypography.copy(
    titleLarge = AppTypography.titleLarge.copy(fontSize = 22.sp, lineHeight = 30.sp),
    bodyLarge = AppTypography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = AppTypography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = AppTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
    labelSmall = AppTypography.labelSmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
)