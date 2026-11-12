package com.greasepencil.b4after.domain

/**
 * Photos are saved in the phone's portrait frame (so the ghost lines up). When the phone was held
 * sideways, [uprightFromOrientation] says how far to turn the photo clockwise to show it upright.
 */
object Rotation {
    /**
     * @param degrees the raw OrientationEventListener value (0..359, or -1 when flat/unknown)
     * @return 0, 90, 180 or 270
     */
    fun uprightFromOrientation(degrees: Int): Int = when {
        degrees < 0 -> 0
        // 90 = the phone's left side is at the top, so the sky sits on the image's left: turn clockwise.
        degrees in 45 until 135 -> 90
        degrees in 135 until 225 -> 180
        degrees in 225 until 315 -> 270
        else -> 0
    }

    /** Width and height after turning by [rotation]. */
    fun size(width: Int, height: Int, rotation: Int): Pair<Int, Int> =
        if (rotation % 180 == 0) width to height else height to width
}
