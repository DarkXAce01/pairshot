package com.greasepencil.pairshot.domain

/** Where the before and after go in an exported image. Pure maths so it can be unit tested. */
object CollageLayout {
    enum class Style(val label: String) {
        SIDE_BY_SIDE("Side by side"),
        STACKED("Before on top"),
        /** One frame: the left half of the before meets the right half of the after. */
        SPLIT("Split"),
        /** 1:1, for Instagram and Facebook posts. */
        SQUARE("Square post"),
        /** 9:16, for stories, reels covers and WhatsApp status. */
        STORY("Story"),
        ;

        /** Whether the business name sits in a header band (else it is in the footer). */
        val nameInHeader: Boolean get() = this == SQUARE || this == STORY

        companion object {
            /** A stored value back to a style; anything unknown means [SIDE_BY_SIDE]. */
            fun fromStored(value: String?): Style = entries.firstOrNull { it.name == value } ?: SIDE_BY_SIDE
        }
    }

    data class Box(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    data class Plan(
        val width: Int,
        val height: Int,
        /** Where the before is shown. For [Style.SPLIT], the left half of [frame]. */
        val before: Box,
        /** Where the after is shown. For [Style.SPLIT], the right half of [frame]. */
        val after: Box,
        val labelHeight: Int,
        val footerHeight: Int,
        val headerHeight: Int = 0,
        /** [Style.SPLIT] only: the whole photo frame both photos are fitted to before halving. */
        val frame: Box? = null,
    )

    /** The canvas size of [style] for a long edge, before bands; null when it follows the photo. */
    fun fixedCanvas(style: Style, longEdge: Int): Pair<Int, Int>? = when (style) {
        Style.SQUARE -> longEdge to longEdge
        Style.STORY -> longEdge * 9 / 16 to longEdge
        else -> null
    }

    /**
     * Which bands an image has, as (header, footer). Square and Story put the name in a header and
     * keep the footer for the free mark; the others put both in the footer. No name and Pro: none.
     */
    fun bands(style: Style, hasName: Boolean, watermark: Boolean): Pair<Boolean, Boolean> =
        if (style.nameInHeader) (hasName to watermark) else (false to (hasName || watermark))

    /** Height of a footer or header band for a long edge. */
    fun bandHeight(longEdge: Int): Int = (longEdge * 0.06f).toInt()

    /**
     * Two photo cells of [cellAspect] (width / height), a gutter between, a label strip on each
     * cell, an optional [header] band and a [footer] band for the business name and the mark. The
     * longer edge of the whole image is [longEdge]. Square and Story have fixed canvases; their
     * cells are centre-cut to fit.
     */
    fun plan(
        style: Style,
        cellAspect: Float,
        longEdge: Int = 2048,
        footer: Boolean = true,
        header: Boolean = false,
    ): Plan {
        require(cellAspect > 0f)
        val gutter = (longEdge * 0.008f).toInt().coerceAtLeast(4)
        val footerH = if (footer) bandHeight(longEdge) else 0
        return when (style) {
            Style.SIDE_BY_SIDE -> {
                val totalW = longEdge
                val cellW = (totalW - gutter) / 2
                val cellH = (cellW / cellAspect).toInt()
                val label = (cellH * 0.09f).toInt()
                Plan(totalW, cellH + footerH, Box(0, 0, cellW, cellH), Box(cellW + gutter, 0, totalW - cellW - gutter, cellH), label, footerH)
            }
            Style.STACKED -> {
                // Height is the long edge for portrait-ish cells; otherwise width is.
                val cellWFromHeight = (((longEdge - gutter - footerH) / 2) * cellAspect).toInt()
                val cellW = minOf(longEdge, cellWFromHeight)
                val cellH = (cellW / cellAspect).toInt()
                val label = (cellH * 0.09f).toInt()
                Plan(cellW, cellH * 2 + gutter + footerH, Box(0, 0, cellW, cellH), Box(0, cellH + gutter, cellW, cellH), label, footerH)
            }
            Style.SPLIT -> {
                // One frame at the pair's shape; the long edge of the whole image is longEdge.
                val (w, h) = if (cellAspect >= 1f) {
                    longEdge to (longEdge / cellAspect).toInt()
                } else {
                    val fh = longEdge - footerH
                    (fh * cellAspect).toInt() to fh
                }
                val half = w / 2
                val frame = Box(0, 0, w, h)
                val label = (minOf(w, h) * 0.07f).toInt()
                Plan(w, h + footerH, Box(0, 0, half, h), Box(half, 0, w - half, h), label, footerH, frame = frame)
            }
            Style.SQUARE -> {
                val (w, h) = fixedCanvas(style, longEdge)!!
                val headerH = if (header) bandHeight(longEdge) else 0
                val cellTop = headerH
                val cellH = h - headerH - footerH
                val cellW = (w - gutter) / 2
                val label = (minOf(cellW, cellH) * 0.09f).toInt()
                Plan(w, h, Box(0, cellTop, cellW, cellH), Box(cellW + gutter, cellTop, w - cellW - gutter, cellH), label, footerH, headerH)
            }
            Style.STORY -> {
                val (w, h) = fixedCanvas(style, longEdge)!!
                val headerH = if (header) bandHeight(longEdge) else 0
                val space = h - headerH - footerH - gutter
                val cellH = space / 2
                val label = (minOf(w, cellH) * 0.09f).toInt()
                Plan(w, h, Box(0, headerH, w, cellH), Box(0, headerH + cellH + gutter, w, space - cellH), label, footerH, headerH)
            }
        }
    }
}
