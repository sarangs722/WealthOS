package com.wealthos.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF236454), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EDDF), onPrimaryContainer = Color(0xFF123F32),
    secondary = Color(0xFF58634B), secondaryContainer = Color(0xFFE4EBDA), onSecondaryContainer=Color(0xFF203C2F),
    background = Color(0xFFF7F8F5), surface = Color(0xFFF7F8F5),
    surfaceContainer = Color(0xFFEEF1EA), surfaceContainerLow = Color(0xFFFFFFFF),
    onSurface = Color(0xFF192B24), onSurfaceVariant = Color(0xFF56665E), outlineVariant = Color(0xFFDCE3DA),
    outline=Color(0xFF6E7C74),surfaceContainerHighest=Color(0xFFE0E7DD),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA5D6BB), onPrimary = Color(0xFF073829),
    primaryContainer = Color(0xFF254D3E), onPrimaryContainer = Color(0xFFCCEFDA),
    secondary=Color(0xFFB4CCAA),secondaryContainer=Color(0xFF344B3B),onSecondaryContainer=Color(0xFFE1EFDB),
    background = Color(0xFF101A16), surface = Color(0xFF101A16),
    surfaceContainer = Color(0xFF202C25), surfaceContainerLow = Color(0xFF18231D),
    onSurface = Color(0xFFE1EAE2), onSurfaceVariant = Color(0xFFB4C3B8), outlineVariant = Color(0xFF35473B),
    outline=Color(0xFF859A8A),surfaceContainerHighest=Color(0xFF304336),
)
private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
)
internal data class ForecastChartPalette(val nominal:Color,val real:Color)
@Composable internal fun forecastChartPalette() = ForecastChartPalette(
    nominal=MaterialTheme.colorScheme.primary,
    real=if(MaterialTheme.colorScheme.surface.luminance()<0.5f) Color(0xFF9BC4FF) else Color(0xFF4269AB),
)
@Composable fun WealthTheme(darkTheme:Boolean=isSystemInDarkTheme(),content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Type, content = content)
}
