package com.greasepencil.pairshot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.greasepencil.pairshot.R
import com.greasepencil.pairshot.brand.GreasePencil
import com.greasepencil.pairshot.brand.StudioFonts

// A placeholder look: warm paper, ink, pencil red. The real UI pass comes later.
private val Light = lightColorScheme(
    primary = GreasePencil.Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6D9D2),
    onPrimaryContainer = Color(0xFF5A1508),
    secondary = Color(0xFF55524C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E1D8),
    onSecondaryContainer = Color(0xFF1C1B19),
    background = Color(0xFFF2EFE9),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFF2EFE9),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE6E1D8),
    onSurfaceVariant = Color(0xFF55524C),
    surfaceContainer = Color(0xFFEAE6DE),
    surfaceContainerHigh = Color(0xFFE4DFD6),
    surfaceContainerLow = Color(0xFFF7F4EF),
    outline = Color(0xFF8C877E),
    outlineVariant = Color(0xFFCFC9BE),
)

private val Dark = darkColorScheme(
    primary = GreasePencil.RedOnDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6B2213),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFBDB8AF),
    onSecondary = Color(0xFF1C1B19),
    secondaryContainer = Color(0xFF34322E),
    onSecondaryContainer = Color(0xFFE9E5DD),
    background = Color(0xFF161514),
    onBackground = Color(0xFFEDE9E2),
    surface = Color(0xFF161514),
    onSurface = Color(0xFFEDE9E2),
    surfaceVariant = Color(0xFF2B2926),
    onSurfaceVariant = Color(0xFFBDB8AF),
    surfaceContainer = Color(0xFF201F1D),
    surfaceContainerHigh = Color(0xFF2B2926),
    surfaceContainerLow = Color(0xFF1B1A18),
    outline = Color(0xFF8C877E),
    outlineVariant = Color(0xFF45423D),
)

val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
val Mono = FontFamily(Font(R.font.geist_mono))
val studioFonts = StudioFonts(serif = Serif, mono = Mono)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = Serif),
    headlineLarge = base.headlineLarge.copy(fontFamily = Serif),
    headlineMedium = base.headlineMedium.copy(fontFamily = Serif),
    headlineSmall = base.headlineSmall.copy(fontFamily = Serif, fontSize = 28.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun PairshotTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
