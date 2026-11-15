package com.greasepencil.pairshot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale (spec 4.2): Instrument Serif for titles (never bold, never below 22 sp), Geist for
 * everything you read, Geist Mono for facts and the small uppercase eyebrows.
 */
@Immutable
data class PsType(
    val display: TextStyle,
    val titleXL: TextStyle,
    val titleL: TextStyle,
    val titleM: TextStyle,
    val heading: TextStyle,
    val rowTitle: TextStyle,
    val rowTitleS: TextStyle,
    val body: TextStyle,
    val bodyS: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
    val chip: TextStyle,
    /** Shown uppercase; the components apply the case. */
    val eyebrow: TextStyle,
    val mono: TextStyle,
)

private fun style(
    family: FontFamily,
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
    features: String? = null,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = features,
)

val PsTypography = PsType(
    display = style(Serif, 40, 44, tracking = -0.015),
    titleXL = style(Serif, 32, 36, tracking = -0.01),
    titleL = style(Serif, 26, 30, tracking = -0.01),
    titleM = style(Serif, 22, 28, tracking = -0.005),
    heading = style(Sans, 18, 24, FontWeight.SemiBold, -0.01),
    rowTitle = style(Sans, 16, 22, FontWeight.Medium, -0.005),
    rowTitleS = style(Sans, 14, 20, FontWeight.Medium),
    body = style(Sans, 16, 24),
    bodyS = style(Sans, 14, 20),
    caption = style(Sans, 13, 18),
    button = style(Sans, 15, 20, FontWeight.SemiBold, -0.005),
    chip = style(Sans, 13, 16, FontWeight.Medium),
    eyebrow = style(Mono, 11, 14, FontWeight.Medium, 0.08),
    mono = style(Mono, 13, 18, features = "tnum"),
)

/** Tabular figures, for numbers in rows. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** The same scale in Material's slots, so stock components that slip through still look right. */
fun PsType.toMaterial(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = display,
    headlineLarge = titleXL,
    headlineMedium = titleL,
    headlineSmall = titleM,
    titleLarge = heading,
    titleMedium = rowTitle,
    titleSmall = rowTitleS,
    bodyLarge = body,
    bodyMedium = bodyS,
    bodySmall = caption,
    labelLarge = button,
    labelMedium = chip,
    labelSmall = eyebrow,
)
