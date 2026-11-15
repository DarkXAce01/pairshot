package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.domain.gallery.BrandLook
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import org.junit.Test

class CollageFrameTest {
    @Test fun `each page style has its own background`() {
        assertThat(CollageFrame.of(ClientTemplate.CLASSIC, null).background).isEqualTo("#F3F0EA")
        assertThat(CollageFrame.of(ClientTemplate.SHOWROOM, null).background).isEqualTo("#0E0D0C")
        assertThat(CollageFrame.of(ClientTemplate.BRIGHT, null).background).isEqualTo("#FFFFFF")
        assertThat(CollageFrame.of(ClientTemplate.REPORT, null).background).isEqualTo("#FFFFFF")
    }

    @Test fun `the accent is the business colour, or the frame's ink`() {
        assertThat(CollageFrame.of(ClientTemplate.CLASSIC, "#1e3a5f").accent).isEqualTo("#1E3A5F")
        assertThat(CollageFrame.of(ClientTemplate.CLASSIC, null).accent).isEqualTo("#1B1A18")
        assertThat(CollageFrame.of(ClientTemplate.REPORT, null).accent).isEqualTo("#111111")
    }

    @Test fun `a rule that would vanish falls back to the ink`() {
        assertThat(CollageFrame.of(ClientTemplate.SHOWROOM, BrandLook.INK.hex).accent).isEqualTo("#F2EEE7")
        assertThat(CollageFrame.of(ClientTemplate.REPORT, "#FFFFFF").accent).isEqualTo("#111111")
    }

    @Test fun `bright fills the bands and sets the name on the accent`() {
        val f = CollageFrame.of(ClientTemplate.BRIGHT, BrandLook.BLUE.hex)
        assertThat(f.bandsFilled).isTrue()
        assertThat(f.accent).isEqualTo(BrandLook.BLUE.hex)
        assertThat(f.nameColour).isEqualTo("#FFFFFF")
        assertThat(f.rule(2048)).isEqualTo(0)
        assertThat(CollageFrame.of(ClientTemplate.BRIGHT, "#FFF4B0").nameColour).isEqualTo("#111111")
    }

    @Test fun `rules scale with the image and never vanish`() {
        assertThat(CollageFrame.of(ClientTemplate.CLASSIC, null).rule(2048)).isEqualTo(6)
        assertThat(CollageFrame.of(ClientTemplate.REPORT, null).rule(2048)).isEqualTo(3)
        assertThat(CollageFrame.of(ClientTemplate.REPORT, null).rule(240)).isEqualTo(1)
    }

    @Test fun `names are set per style`() {
        assertThat(CollageFrame.of(ClientTemplate.CLASSIC, null).nameText(" Sparkle Wash ", "Driveway")).isEqualTo("Sparkle Wash")
        assertThat(CollageFrame.of(ClientTemplate.SHOWROOM, null).nameText("Sparkle Wash", null)).isEqualTo("SPARKLE WASH")
        assertThat(CollageFrame.of(ClientTemplate.REPORT, null).nameText("Sparkle Wash", "Driveway")).isEqualTo("SPARKLE WASH · DRIVEWAY")
        assertThat(CollageFrame.of(ClientTemplate.REPORT, null).nameText("Sparkle Wash", null)).isEqualTo("SPARKLE WASH")
        assertThat(CollageFrame.of(ClientTemplate.REPORT, null).nameText("", "Driveway")).isEmpty()
    }

    @Test fun `long names shrink to fit, then give up for shortening`() {
        assertThat(CollageFrame.fitSize(10f, 1000f, 60f, 30f)).isEqualTo(60f)
        assertThat(CollageFrame.fitSize(25f, 1000f, 60f, 30f)).isEqualTo(40f)
        assertThat(CollageFrame.fitSize(50f, 1000f, 60f, 30f)).isNull()
    }
}
