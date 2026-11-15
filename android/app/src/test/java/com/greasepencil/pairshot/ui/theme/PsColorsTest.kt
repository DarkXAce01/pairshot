package com.greasepencil.pairshot.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The colour table of spec 4.1, exactly, and the contrast it promises. */
class PsColorsTest {
    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    @Test fun lightValuesMatchTheSpec() {
        val c = PsLightColors
        assertThat(listOf(c.paper, c.surface, c.sunken, c.ink, c.onInk, c.inkSoft, c.inkFaint, c.hairline, c.outline, c.pencil, c.onPencil, c.pencilText, c.pencilWash).map(::hex))
            .containsExactly("#F3F0EA", "#FBF9F5", "#E9E5DC", "#1B1A18", "#F3F0EA", "#57534C", "#6B665E", "#DCD6CA", "#847E73", "#D4432A", "#FFFFFF", "#B3361F", "#F6DDD5").inOrder()
        assertThat(c.scrim.alpha).isWithin(0.01f).of(0.40f)
    }

    @Test fun darkValuesMatchTheSpec() {
        val c = PsDarkColors
        assertThat(listOf(c.paper, c.surface, c.sunken, c.ink, c.onInk, c.inkSoft, c.inkFaint, c.hairline, c.outline, c.pencil, c.onPencil, c.pencilText, c.pencilWash).map(::hex))
            .containsExactly("#141312", "#1D1C1A", "#262422", "#EDE9E2", "#141312", "#B8B3A9", "#958F85", "#302E2A", "#77726A", "#E5553A", "#1A0A06", "#F0745A", "#3B1B13").inOrder()
        assertThat(c.scrim.alpha).isWithin(0.01f).of(0.60f)
    }

    @Test fun textPairsMeetAA() {
        for (c in listOf(PsLightColors, PsDarkColors)) {
            for (bg in listOf(c.paper, c.surface, c.sunken)) {
                for (fg in listOf(c.ink, c.inkSoft, c.inkFaint, c.pencilText)) {
                    assertThat(Contrast.ratio(fg, bg)).isAtLeast(4.5)
                }
            }
            assertThat(Contrast.ratio(c.onInk, c.ink)).isAtLeast(4.5)
            assertThat(Contrast.ratio(c.onPencil, c.pencil)).isAtLeast(4.5)
            assertThat(Contrast.ratio(c.pencilText, c.pencilWash)).isAtLeast(4.5)
            assertThat(Contrast.ratio(c.ink, c.pencilWash)).isAtLeast(4.5)
        }
    }

    @Test fun boundariesAndMarksMeetThreeToOne() {
        for (c in listOf(PsLightColors, PsDarkColors)) {
            for (bg in listOf(c.paper, c.surface, c.sunken, c.pencilWash)) {
                assertThat(Contrast.ratio(c.outline, bg)).isAtLeast(3.0)
            }
            for (bg in listOf(c.paper, c.surface, c.sunken)) {
                assertThat(Contrast.ratio(c.pencil, bg)).isAtLeast(3.0)
            }
            // The white check on the selected-frame badge.
            assertThat(Contrast.ratio(Color.White, c.pencil)).isAtLeast(3.0)
        }
    }

    @Test fun inkIsFifteenToOneOnPaperInBothThemes() {
        assertThat(Contrast.ratio(PsLightColors.ink, PsLightColors.paper)).isWithin(0.05).of(15.3)
        assertThat(Contrast.ratio(PsDarkColors.ink, PsDarkColors.paper)).isWithin(0.05).of(15.3)
    }

    @Test fun cameraChromeIsReadable() {
        assertThat(Contrast.ratio(PsCameraColors.ink, PsCameraColors.background)).isAtLeast(15.0)
        assertThat(Contrast.ratio(PsCameraColors.muted, PsCameraColors.background)).isAtLeast(7.0)
    }

    @Test fun materialPrimaryIsInkNotRed() {
        val light = PsLightColors.toMaterial()
        assertThat(light.primary).isEqualTo(PsLightColors.ink)
        assertThat(light.error).isEqualTo(PsLightColors.pencilText)
        assertThat(light.surfaceContainerHighest).isEqualTo(PsLightColors.sunken)
        assertThat(PsDarkColors.toMaterial().background).isEqualTo(PsDarkColors.paper)
    }

    @Test fun nothingReadableBelowThirteenSp() {
        val t = PsTypography
        listOf(t.display, t.titleXL, t.titleL, t.titleM, t.heading, t.rowTitle, t.rowTitleS, t.body, t.bodyS, t.caption, t.button, t.chip, t.mono)
            .forEach { assertThat(it.fontSize.value).isAtLeast(13f) }
        listOf(t.display, t.titleXL, t.titleL, t.titleM).forEach {
            assertThat(it.fontFamily).isEqualTo(Serif)
            assertThat(it.fontSize.value).isAtLeast(22f)
        }
        assertThat(t.eyebrow.fontFamily).isEqualTo(Mono)
    }
}
