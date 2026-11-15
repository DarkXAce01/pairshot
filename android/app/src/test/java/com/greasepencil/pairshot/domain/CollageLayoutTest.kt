package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.domain.CollageLayout.Style
import org.junit.Test
import kotlin.math.abs

class CollageLayoutTest {
    private fun aspectOf(b: CollageLayout.Box) = b.width.toFloat() / b.height

    @Test fun `side by side keeps the photo shape and fits the width`() {
        val p = CollageLayout.plan(Style.SIDE_BY_SIDE, 3f / 4f, longEdge = 2048)
        assertThat(p.width).isEqualTo(2048)
        assertThat(abs(aspectOf(p.before) - 0.75f)).isLessThan(0.01f)
        assertThat(p.after.left).isGreaterThan(p.before.left + p.before.width) // gutter
        assertThat(p.after.left + p.after.width).isAtMost(p.width)
        assertThat(p.before.top + p.before.height).isAtMost(p.height - p.footerHeight)
    }

    @Test fun `stacked portrait cells fit within the long edge`() {
        val p = CollageLayout.plan(Style.STACKED, 3f / 4f, longEdge = 2048)
        assertThat(p.height).isAtMost(2048)
        assertThat(p.after.top).isGreaterThan(p.before.top + p.before.height)
        assertThat(abs(aspectOf(p.after) - 0.75f)).isLessThan(0.01f)
    }

    @Test fun `stacked landscape cells use the full width`() {
        val p = CollageLayout.plan(Style.STACKED, 4f / 3f, longEdge = 2048)
        assertThat(p.width).isAtMost(2048)
        assertThat(p.after.top + p.after.height + p.footerHeight).isEqualTo(p.height)
    }

    @Test fun `no footer when not asked`() {
        assertThat(CollageLayout.plan(Style.SIDE_BY_SIDE, 1f, footer = false).footerHeight).isEqualTo(0)
    }

    private fun inside(p: CollageLayout.Plan, b: CollageLayout.Box) {
        assertThat(b.left).isAtLeast(0)
        assertThat(b.top).isAtLeast(p.headerHeight)
        assertThat(b.right).isAtMost(p.width)
        assertThat(b.bottom).isAtMost(p.height - p.footerHeight)
    }

    private fun disjoint(a: CollageLayout.Box, b: CollageLayout.Box) =
        a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top

    @Test fun `every style keeps both boxes inside the canvas without overlap`() {
        for (style in Style.entries) for (aspect in listOf(0.75f, 1f, 4f / 3f, 0.5f, 2f)) for (bands in listOf(true, false)) {
            val p = CollageLayout.plan(style, aspect, longEdge = 2048, footer = bands, header = bands)
            inside(p, p.before); inside(p, p.after)
            assertThat(disjoint(p.before, p.after)).isTrue()
        }
    }

    @Test fun `square and story have exact canvases`() {
        for (aspect in listOf(0.75f, 4f / 3f)) {
            val sq = CollageLayout.plan(Style.SQUARE, aspect, footer = true, header = true)
            assertThat(sq.width to sq.height).isEqualTo(2048 to 2048)
            val st = CollageLayout.plan(Style.STORY, aspect, footer = true, header = true)
            assertThat(st.width to st.height).isEqualTo(1152 to 2048)
            val bare = CollageLayout.plan(Style.STORY, aspect, footer = false, header = false)
            assertThat(bare.width to bare.height).isEqualTo(1152 to 2048)
        }
    }

    @Test fun `square and story cells fill the space between the bands`() {
        val sq = CollageLayout.plan(Style.SQUARE, 0.75f, footer = true, header = true)
        assertThat(sq.headerHeight).isGreaterThan(0)
        assertThat(sq.before.top).isEqualTo(sq.headerHeight)
        assertThat(sq.before.bottom).isEqualTo(sq.height - sq.footerHeight)
        assertThat(sq.after.right).isEqualTo(sq.width)
        val st = CollageLayout.plan(Style.STORY, 0.75f, footer = true, header = true)
        assertThat(st.after.bottom).isEqualTo(st.height - st.footerHeight)
        assertThat(st.after.top).isGreaterThan(st.before.bottom)
    }

    @Test fun `split halves meet at the seam with no gap`() {
        for (aspect in listOf(0.75f, 4f / 3f)) {
            val p = CollageLayout.plan(Style.SPLIT, aspect)
            val frame = requireNotNull(p.frame)
            assertThat(p.before.right).isEqualTo(p.after.left)
            assertThat(p.before.left).isEqualTo(frame.left)
            assertThat(p.after.right).isEqualTo(frame.right)
            assertThat(abs(aspectOf(frame) - aspect)).isLessThan(0.01f)
            assertThat(p.height).isEqualTo(frame.height + p.footerHeight)
        }
    }

    @Test fun `bands collapse with no name on Pro`() {
        for (style in Style.entries) {
            val (header, footer) = CollageLayout.bands(style, hasName = false, watermark = false)
            val p = CollageLayout.plan(style, 0.75f, footer = footer, header = header)
            assertThat(p.headerHeight).isEqualTo(0)
            assertThat(p.footerHeight).isEqualTo(0)
        }
    }

    @Test fun `free always keeps a footer for the mark`() {
        for (style in Style.entries) for (name in listOf(true, false)) {
            assertThat(CollageLayout.bands(style, hasName = name, watermark = true).second).isTrue()
        }
    }

    @Test fun `square and story put the name in a header, the others in the footer`() {
        assertThat(CollageLayout.bands(Style.SQUARE, hasName = true, watermark = false)).isEqualTo(true to false)
        assertThat(CollageLayout.bands(Style.STORY, hasName = true, watermark = true)).isEqualTo(true to true)
        assertThat(CollageLayout.bands(Style.SPLIT, hasName = true, watermark = false)).isEqualTo(false to true)
    }

    @Test fun `a thumbnail is the same layout scaled down`() {
        for (style in Style.entries) {
            val full = CollageLayout.plan(style, 0.75f, longEdge = 2048)
            val thumb = CollageLayout.plan(style, 0.75f, longEdge = 240)
            assertThat(abs(full.width.toFloat() / full.height - thumb.width.toFloat() / thumb.height)).isLessThan(0.03f)
            assertThat(maxOf(thumb.width, thumb.height)).isAtMost(240)
        }
    }

    @Test fun `stored layouts read back and unknown ones fall back`() {
        for (style in Style.entries) assertThat(Style.fromStored(style.name)).isEqualTo(style)
        assertThat(Style.fromStored(null)).isEqualTo(Style.SIDE_BY_SIDE)
        assertThat(Style.fromStored("WIDE")).isEqualTo(Style.SIDE_BY_SIDE)
    }
}
