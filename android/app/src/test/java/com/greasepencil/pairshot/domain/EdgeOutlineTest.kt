package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EdgeOutlineTest {
    private val red = 0xFFE5553A.toInt()

    private fun image(w: Int, h: Int, px: (Int, Int) -> Int) = IntArray(w * h) { px(it % w, it / w) }

    @Test fun `flat image has no edges`() {
        val out = EdgeOutline.outline(image(20, 20) { _, _ -> 0xFF808080.toInt() }, 20, 20, red)
        assertThat(out.all { it == 0 }).isTrue()
    }

    @Test fun `a hard vertical line is found, in the requested colour`() {
        val w = 40; val h = 30
        val src = image(w, h) { x, _ -> if (x < 20) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val out = EdgeOutline.outline(src, w, h, red)
        for (y in 1 until h - 1) {
            assertThat(out[y * w + 19] ushr 24).isGreaterThan(0)
            assertThat(out[y * w + 19] and 0xFFFFFF).isEqualTo(red and 0xFFFFFF)
        }
        // Far from the line stays clear.
        assertThat(out[10 * w + 5]).isEqualTo(0)
        assertThat(out[10 * w + 35]).isEqualTo(0)
    }

    @Test fun `faint noise is not drawn`() {
        val w = 32; val h = 32
        val src = image(w, h) { x, y -> val v = 128 + ((x * 7 + y * 13) % 5); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val out = EdgeOutline.outline(src, w, h, red)
        assertThat(out.count { it != 0 }).isEqualTo(0)
    }

    @Test fun `tiny images do not crash`() {
        assertThat(EdgeOutline.outline(IntArray(4), 2, 2, red)).hasLength(4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong pixel count is refused`() {
        EdgeOutline.outline(IntArray(10), 4, 4, red)
    }
}
