package com.greasepencil.pairshot.domain

/** Where the before and after go in an exported image. Pure maths so it can be unit tested. */
object CollageLayout {
    enum class Style { SIDE_BY_SIDE, STACKED }

    data class Box(val left: Int, val top: Int, val width: Int, val height: Int)

    data class Plan(
        val width: Int,
        val height: Int,
        val before: Box,
        val after: Box,
        val labelHeight: Int,
        val footerHeight: Int,
    )

    /**
     * Two equal photo cells of [cellAspect] (width / height), a gutter between, a label strip on each
     * cell, and a footer for the business name. The longer edge of the whole image is [longEdge].
     */
    fun plan(style: Style, cellAspect: Float, longEdge: Int = 2048, footer: Boolean = true): Plan {
        require(cellAspect > 0f)
        val gutter = (longEdge * 0.008f).toInt().coerceAtLeast(4)
        val footerH = if (footer) (longEdge * 0.06f).toInt() else 0
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
        }
    }
}
