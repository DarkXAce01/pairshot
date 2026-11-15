package com.greasepencil.pairshot.domain

import com.greasepencil.pairshot.data.db.PairEntity

/** A crop as fractions (0..1) of the upright photo. */
data class Crop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width get() = right - left
    val height get() = bottom - top
    val isFull get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    /** Inside the photo and at least [MIN] wide and tall. */
    fun clamped(): Crop {
        var l = left.coerceIn(0f, 1f - MIN); var t = top.coerceIn(0f, 1f - MIN)
        val r = right.coerceIn(l + MIN, 1f); val b = bottom.coerceIn(t + MIN, 1f)
        if (r - l < MIN) l = r - MIN
        if (b - t < MIN) t = b - MIN
        return Crop(l, t, r, b)
    }

    /** The cropped shape's width / height, for a photo of [w] × [h]. */
    fun aspect(w: Int, h: Int): Float = (w * width) / (h * height)

    companion object {
        const val MIN = 0.1f
        val FULL = Crop()
        fun of(p: PairEntity) = Crop(p.cropLeft, p.cropTop, p.cropRight, p.cropBottom)
    }
}

/** Where a job stands, as the home list shows it. */
enum class JobStatus(val label: String) {
    UPCOMING("Upcoming"), IN_PROGRESS("In progress"), DONE("Done"), SENT("Sent"), SEEN("Seen");

    companion object {
        fun of(pairCount: Int, doneCount: Int, linkSent: Boolean, linkLive: Boolean, viewed: Boolean): JobStatus = when {
            linkSent && linkLive && viewed -> SEEN
            linkSent && linkLive -> SENT
            pairCount == 0 -> UPCOMING
            doneCount < pairCount -> IN_PROGRESS
            else -> DONE
        }
    }
}
