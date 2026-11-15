package com.greasepencil.pairshot.ui.theme

import androidx.compose.ui.graphics.Color

/** Camera chrome is always dark, in both app themes (spec 4.1). */
object PsCameraColors {
    val background = Color(0xFF0E0E0D)
    val ink = Color(0xFFEDE9E2)
    val muted = Color(0xFFA39E95)
    val hairline = Color(0xFF3A3835)
    val shutter = Color(0xFFFFFFFF)
    val ghost = Color(0xFFE5553A)
    /** Badges and notes over the live preview (zoom, peek, errors): black at 55 %. */
    val overlay = Color(0x8C000000)
    /** Behind the "every before has its after" note. */
    val veil = Color(0xD90E0E0D)
}
