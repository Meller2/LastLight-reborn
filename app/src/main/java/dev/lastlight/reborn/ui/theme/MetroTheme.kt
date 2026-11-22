package dev.lastlight.reborn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object MetroColors {
    val BgDeep = Color(0xFF0A0C0F)
    val BgTunnel = Color(0xFF12161B)
    val SurfaceGlass = Color(0xCC151A20)
    val SurfaceDark = Color(0xFF14191F)
    val SurfaceElevated = Color(0xFF1C232B)
    val BorderSubtle = Color(0xFF28313B)
    val BorderSteel = Color(0xFF34404D)
    val Amber = Color(0xFFE5953A)          // Warm Nixie / watch amber
    val AmberBright = Color(0xFFF3B05A)
    val AmberDim = Color(0xFF7A4915)
    val PhosphorGreen = Color(0xFF4CD672)  // Subtle ready indicator
    val DangerRed = Color(0xFFE05246)
    val TextPrimary = Color(0xFFF2EFE9)
    val TextSecondary = Color(0xFFA2ACB6)
    val TextMuted = Color(0xFF6B7682)
}

private val MetroDarkScheme = darkColorScheme(
    primary = MetroColors.Amber,
    onPrimary = Color(0xFF180F05),
    secondary = MetroColors.PhosphorGreen,
    onSecondary = Color(0xFF061608),
    background = MetroColors.BgDeep,
    onBackground = MetroColors.TextPrimary,
    surface = MetroColors.SurfaceDark,
    onSurface = MetroColors.TextPrimary,
    surfaceVariant = MetroColors.SurfaceElevated,
    onSurfaceVariant = MetroColors.TextSecondary,
    outline = MetroColors.BorderSubtle,
    error = MetroColors.DangerRed
)

private val MetroTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun MetroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MetroDarkScheme,
        typography = MetroTypography,
        content = content
    )
}
