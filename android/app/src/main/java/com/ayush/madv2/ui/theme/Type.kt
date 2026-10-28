package com.ayush.madv2.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ayush.madv2.R

codezxsWIN/mad-foundation
val Typography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),

@OptIn(ExperimentalTextApi::class)
val AppFont = FontFamily(
    listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
    ).map { weight ->
        Font(
            R.font.plus_jakarta_sans,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }
)

private fun TextStyle.app() = copy(fontFamily = AppFont)

private val base = Typography()

val Typography = base.copy(
    displayLarge = base.displayLarge.app(),
    displayMedium = base.displayMedium.app(),
    displaySmall = base.displaySmall.app(),
    headlineLarge = base.headlineLarge.app().copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = base.headlineMedium.app().copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineSmall = base.headlineSmall.app().copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = base.titleLarge.app(),
    titleMedium = base.titleMedium.app(),
    titleSmall = base.titleSmall.app(),
    bodyLarge = base.bodyLarge.app().copy(letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.app().copy(letterSpacing = 0.sp),
    bodySmall = base.bodySmall.app(),
    labelLarge = base.labelLarge.app(),
    labelMedium = base.labelMedium.app(),
    labelSmall = base.labelSmall.app(),
main
)
