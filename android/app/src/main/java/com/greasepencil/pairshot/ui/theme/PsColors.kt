package com.greasepencil.pairshot.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The app's colours (spec section 4.1). Paper, ink and one pencil red that only marks what matters.
 * Screens read these as `Ps.colors.*`, never raw colour literals.
 */
@Immutable
data class PsColors(
    /** Screen background. */
    val paper: Color,
    /** Sheets, dialogs, grouped list blocks, bottom action bar, tab bar. */
    val surface: Color,
    /** Photo placeholders, code wells, input fill, meter track, unselected segment track. */
    val sunken: Color,
    /** Main text, icons, primary button fill. */
    val ink: Color,
    /** Text on an ink fill. */
    val onInk: Color,
    /** Secondary text. */
    val inkSoft: Color,
    /** Tertiary text, placeholders, mono eyebrows. */
    val inkFaint: Color,
    /** Dividers and decorative borders (never the only boundary of a control). */
    val hairline: Color,
    /** Borders of inputs, secondary buttons, unselected chips. */
    val outline: Color,
    /** Accent fill and marks. */
    val pencil: Color,
    /** Text on a pencil fill. */
    val onPencil: Color,
    /** Red text on paper/surface/sunken: errors and destructive labels. */
    val pencilText: Color,
    /** Tinted background for the "Sent" pill and warning banners. */
    val pencilWash: Color,
    /** Behind sheets and dialogs. */
    val scrim: Color,
    /** Tags over photos. */
    val photoTag: Color,
    /** Text on [photoTag]. */
    val onPhotoTag: Color,
    val isDark: Boolean,
)

val PsLightColors = PsColors(
    paper = Color(0xFFF3F0EA),
    surface = Color(0xFFFBF9F5),
    sunken = Color(0xFFE9E5DC),
    ink = Color(0xFF1B1A18),
    onInk = Color(0xFFF3F0EA),
    inkSoft = Color(0xFF57534C),
    inkFaint = Color(0xFF6B665E),
    hairline = Color(0xFFDCD6CA),
    outline = Color(0xFF847E73),
    pencil = Color(0xFFD4432A),
    onPencil = Color(0xFFFFFFFF),
    pencilText = Color(0xFFB3361F),
    pencilWash = Color(0xFFF6DDD5),
    scrim = Color.Black.copy(alpha = 0.40f),
    photoTag = Color.Black.copy(alpha = 0.55f),
    onPhotoTag = Color.White,
    isDark = false,
)

val PsDarkColors = PsColors(
    paper = Color(0xFF141312),
    surface = Color(0xFF1D1C1A),
    sunken = Color(0xFF262422),
    ink = Color(0xFFEDE9E2),
    onInk = Color(0xFF141312),
    inkSoft = Color(0xFFB8B3A9),
    inkFaint = Color(0xFF958F85),
    hairline = Color(0xFF302E2A),
    outline = Color(0xFF77726A),
    pencil = Color(0xFFE5553A),
    onPencil = Color(0xFF1A0A06),
    pencilText = Color(0xFFF0745A),
    pencilWash = Color(0xFF3B1B13),
    scrim = Color.Black.copy(alpha = 0.60f),
    photoTag = Color.Black.copy(alpha = 0.55f),
    onPhotoTag = Color.White,
    isDark = true,
)

fun psColors(dark: Boolean): PsColors = if (dark) PsDarkColors else PsLightColors

/** WCAG 2.1 contrast maths, so the token table can be checked in tests (and by anyone tuning a colour). */
object Contrast {
    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** Relative luminance of an opaque sRGB colour. */
    fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    /** Contrast ratio between two opaque colours, 1 to 21. */
    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
