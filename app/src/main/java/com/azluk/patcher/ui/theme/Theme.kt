package com.azluk.patcher.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

// AzlukPatcher V10 — Deep Void palette
val AzlukBlue        = Color(0xFF4D8BFF)
val AzlukBlueDim     = Color(0xFF1A3A8F)
val AzlukBlueGlow    = Color(0xFF82AEFF)
val AzlukBlueDark    = Color(0xFF0D1F5C)
val AzlukCyan        = Color(0xFF00E5FF)
val AzlukCyanDim     = Color(0xFF0097A7)
val AzlukPurple      = Color(0xFF7C4DFF)
val AzlukPurpleDim   = Color(0xFF4A148C)
val AzlukBg          = Color(0xFF03050D)
val AzlukBgAlt       = Color(0xFF050810)
val AzlukSurface     = Color(0xFF08101E)
val AzlukSurface2    = Color(0xFF0D1829)
val AzlukSurface3    = Color(0xFF111E32)
val AzlukSurfaceVar  = Color(0xFF162035)
val AzlukSurfaceHigh = Color(0xFF1C2A42)
val AzlukOnBg        = Color(0xFFF0F5FF)
val AzlukOnBgDim     = Color(0xFFCDD5E8)
val AzlukOnSurface   = Color(0xFF7A8EB0)
val AzlukOnSurfaceDim= Color(0xFF4A5872)
val AzlukError       = Color(0xFFFF3D5A)
val AzlukErrorDim    = Color(0xFF8B1A2E)
val AzlukSuccess     = Color(0xFF00E5A0)
val AzlukSuccessDim  = Color(0xFF006B4A)
val AzlukWarning     = Color(0xFFFFAB00)
val AzlukWarningDim  = Color(0xFF7A5100)
val AzlukGold        = Color(0xFFFFD700)

private val DarkColorScheme = darkColorScheme(
    primary           = AzlukBlue,
    onPrimary         = Color.White,
    primaryContainer  = AzlukBlueDim,
    onPrimaryContainer= AzlukBlueGlow,
    secondary         = AzlukCyan,
    onSecondary       = Color(0xFF003040),
    secondaryContainer= AzlukCyanDim,
    tertiary          = AzlukPurple,
    onTertiary        = Color.White,
    background        = AzlukBg,
    onBackground      = AzlukOnBg,
    surface           = AzlukSurface,
    surfaceVariant    = AzlukSurfaceVar,
    onSurface         = AzlukOnSurface,
    onSurfaceVariant  = AzlukOnBgDim,
    error             = AzlukError,
    onError           = Color.White,
    errorContainer    = AzlukErrorDim,
    outline           = AzlukSurfaceVar,
    outlineVariant    = AzlukSurfaceHigh
)

@Composable
fun AzlukTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography(
            displayLarge  = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
            displayMedium = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
            titleLarge    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium   = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            labelLarge    = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            labelMedium   = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        ),
        content = content
    )
}
