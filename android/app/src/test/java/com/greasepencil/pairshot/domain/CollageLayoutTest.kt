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
}
