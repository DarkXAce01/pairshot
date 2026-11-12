package com.greasepencil.pairshot.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a photo into a see-through outline (the "edges" ghost). Works on raw ARGB pixels so it can be
 * tested on the JVM. Strong edges become [color] at full alpha, weaker ones fade, flat areas vanish.
 */
object EdgeOutline {
    /**
     * @param argb pixels, row by row, [width] × [height]
     * @param keepFraction roughly what share of pixels to keep as edges (busy photos stay readable)
     */
    fun outline(argb: IntArray, width: Int, height: Int, color: Int, keepFraction: Float = 0.12f): IntArray {
        require(argb.size == width * height) { "pixel count ${argb.size} != $width×$height" }
        val out = IntArray(argb.size)
        if (width < 3 || height < 3) return out
        val lum = IntArray(argb.size) { i ->
            val p = argb[i]
            ((p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
        }
        val mag = IntArray(argb.size)
        val histogram = IntArray(1021) // |gx|+|gy| is at most 4*255 = 1020
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val tl = lum[i - width - 1]; val t = lum[i - width]; val tr = lum[i - width + 1]
                val l = lum[i - 1]; val r = lum[i + 1]
                val bl = lum[i + width - 1]; val b = lum[i + width]; val br = lum[i + width + 1]
                val gx = (tr + 2 * r + br) - (tl + 2 * l + bl)
                val gy = (bl + 2 * b + br) - (tl + 2 * t + tr)
                val m = min(1020, (abs(gx) + abs(gy)) / 2)
                mag[i] = m
                histogram[m]++
            }
        }
        // Threshold so that about keepFraction of pixels survive, but never below a noise floor.
        val target = (argb.size * keepFraction).toInt()
        var count = 0
        var threshold = 1020
        while (threshold > 0 && count + histogram[threshold] <= target) {
            count += histogram[threshold]
            threshold--
        }
        threshold = max(threshold, 24)
        val rgb = color and 0x00FFFFFF
        val full = threshold * 2
        for (i in mag.indices) {
            val m = mag[i]
            if (m > threshold) {
                val alpha = min(255, 110 + (m - threshold) * 145 / max(1, full - threshold))
                out[i] = (alpha shl 24) or rgb
            }
        }
        return out
    }
}
