package ru.vp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object VendorpainTheme {
    // Fallback values mirror stable Atlassian Design System token roles used by Atlaskit.
    val brand = Color(0xFF0052CC)
    val brandBold = Color(0xFF0747A6)
    val background = Color(0xFFF4F5F7)
    val surface = Color(0xFFFFFFFF)
    val surfaceSunken = Color(0xFFFAFBFC)
    val surfaceHovered = Color(0xFFEBECF0)
    val border = Color(0xFFDFE1E6)
    val text = Color(0xFF172B4D)
    val textSubtle = Color(0xFF6B778C)
    val danger = Color(0xFFDE350B)
    val success = Color(0xFF00875A)

    val primary = brand
    val header = brandBold
    val subtleSurface = surfaceSunken
    val muted = textSubtle
    val error = danger

    val radius = 4.dp
    val space050 = 4.dp
    val space100 = 8.dp
    val space150 = 12.dp
    val space200 = 16.dp
    val space250 = 20.dp
    val space300 = 24.dp

    val frameWidth = 2.dp
    val sectionElevation = 1.dp
}
