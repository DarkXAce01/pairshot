package com.greasepencil.pairshot.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.R
import com.greasepencil.pairshot.brand.StudioFonts

// Bundled fonts (SIL Open Font License, see docs/grease-pencil/fonts and assets/licences):
//  Instrument Serif for names and titles, Geist for interface text, Geist Mono for facts and eyebrows.

val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) = Font(
    res,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Sans = FontFamily(
    variable(R.font.geist, FontWeight.Normal),
    variable(R.font.geist, FontWeight.Medium),
    variable(R.font.geist, FontWeight.SemiBold),
)

val Mono = FontFamily(
    variable(R.font.geist_mono, FontWeight.Normal),
    variable(R.font.geist_mono, FontWeight.Medium),
)

val studioFonts = StudioFonts(serif = Serif, mono = Mono)

/** Material's colour slots filled from the tokens (spec 4.1). Nothing maps primary to red. */
fun PsColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = sunken,
        onPrimaryContainer = ink,
        inversePrimary = onInk,
        secondary = inkSoft,
        onSecondary = paper,
        secondaryContainer = sunken,
        onSecondaryContainer = ink,
        tertiary = inkSoft,
        onTertiary = paper,
        tertiaryContainer = sunken,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = sunken,
        onSurfaceVariant = inkSoft,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = onInk,
        error = pencilText,
        onError = paper,
        errorContainer = pencilWash,
        onErrorContainer = pencilText,
        outline = outline,
        outlineVariant = hairline,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = sunken,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = sunken,
    )
}

private val DefaultSpace = PsSpace()
private val DefaultShapes = PsShapes()
private val DefaultMotion = PsMotion()

private val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * Pairshot's look: warm paper, black ink, hairlines, and a pencil red kept for what matters.
 * [dark] is resolved by the caller from the Appearance setting (see `ThemeMode.isDark`).
 */
@Composable
fun PairshotTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = psColors(dark)
    CompositionLocalProvider(
        LocalPsColors provides colors,
        LocalPsType provides PsTypography,
        LocalPsSpace provides DefaultSpace,
        LocalPsShapes provides DefaultShapes,
        LocalPsMotion provides DefaultMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = PsTypography.toMaterial(),
            shapes = MaterialShapes,
            content = content,
        )
    }
}

/**
 * For always-dark screens (camera, crop editor): the dark token set inside, so focus rings and
 * sheets read on the dark chrome, and light status/navigation bar icons on [window] (the
 * activity's when null) until it leaves, whatever the app theme is.
 */
@Composable
fun AlwaysDark(window: Window? = null, content: @Composable () -> Unit) {
    val view = LocalView.current
    val target = window ?: view.context.findActivity()?.window
    if (target != null && !view.isInEditMode) {
        DisposableEffect(target) {
            val bars = WindowCompat.getInsetsController(target, view)
            val status = bars.isAppearanceLightStatusBars
            val nav = bars.isAppearanceLightNavigationBars
            bars.isAppearanceLightStatusBars = false
            bars.isAppearanceLightNavigationBars = false
            onDispose {
                bars.isAppearanceLightStatusBars = status
                bars.isAppearanceLightNavigationBars = nav
            }
        }
    }
    PairshotTheme(dark = true, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
