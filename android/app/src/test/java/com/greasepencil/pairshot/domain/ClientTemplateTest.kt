package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.domain.gallery.BrandLook
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import org.junit.Test

class ClientTemplateTest {
    @Test fun `ids round-trip and anything else is unknown`() {
        ClientTemplate.entries.forEach { assertThat(ClientTemplate.fromId(it.id)).isEqualTo(it) }
        assertThat(ClientTemplate.entries.map { it.id }).containsExactly("classic", "showroom", "bright", "report").inOrder()
        assertThat(ClientTemplate.fromId(null)).isNull()
        assertThat(ClientTemplate.fromId("Classic")).isNull()
        assertThat(ClientTemplate.fromId("")).isNull()
    }

    @Test fun `nine colours in the order shown, all readable under white text`() {
        assertThat(BrandLook.colours.map { it.name })
            .containsExactly("Ink", "Slate", "Navy", "Blue", "Teal", "Green", "Plum", "Rust", "Red").inOrder()
        BrandLook.colours.forEach {
            assertThat(BrandLook.contrast(it.hex, "#FFFFFF")).isAtLeast(5.0)
            assertThat(BrandLook.onAccent(it.hex)).isEqualTo("#FFFFFF")
        }
    }

    @Test fun `every trade has a look that needs no setup`() {
        assertThat(BrandLook.defaultsFor(Trade.PRESSURE_WASHING)).isEqualTo(BrandLook.NAVY.hex to ClientTemplate.CLASSIC)
        assertThat(BrandLook.defaultsFor(Trade.WINDOW_CLEANING)).isEqualTo(BrandLook.BLUE.hex to ClientTemplate.BRIGHT)
        assertThat(BrandLook.defaultsFor(Trade.GUTTER_CLEANING)).isEqualTo(BrandLook.SLATE.hex to ClientTemplate.REPORT)
        assertThat(BrandLook.defaultsFor(Trade.CAR_DETAILING)).isEqualTo(BrandLook.INK.hex to ClientTemplate.SHOWROOM)
        assertThat(BrandLook.defaultsFor(Trade.CARPET_CLEANING)).isEqualTo(BrandLook.PLUM.hex to ClientTemplate.BRIGHT)
        assertThat(BrandLook.defaultsFor(Trade.CLEANING)).isEqualTo(BrandLook.TEAL.hex to ClientTemplate.BRIGHT)
        assertThat(BrandLook.defaultsFor(Trade.LANDSCAPING)).isEqualTo(BrandLook.GREEN.hex to ClientTemplate.CLASSIC)
        assertThat(BrandLook.defaultsFor(Trade.OTHER)).isEqualTo(BrandLook.INK.hex to ClientTemplate.CLASSIC)
    }

    @Test fun `colour codes are cleaned up or refused`() {
        assertThat(BrandLook.normalise("#1f5fbf")).isEqualTo("#1F5FBF")
        assertThat(BrandLook.normalise("1F5FBF")).isEqualTo("#1F5FBF")
        assertThat(BrandLook.normalise(" #abcdef ")).isEqualTo("#ABCDEF")
        listOf(null, "", "#", "#12345", "#1234567", "#GGGGGG", "red").forEach { assertThat(BrandLook.normalise(it)).isNull() }
        assertThat(BrandLook.named("#1e3a5f")).isEqualTo(BrandLook.NAVY)
        assertThat(BrandLook.named("#123456")).isNull()
    }

    @Test fun `text on a pale accent is dark, on a dark accent white`() {
        assertThat(BrandLook.onAccent("#F5E663")).isEqualTo("#111111")
        assertThat(BrandLook.onAccent("#FFFFFF")).isEqualTo("#111111")
        assertThat(BrandLook.onAccent("#000000")).isEqualTo("#FFFFFF")
        assertThat(BrandLook.contrast("#000000", "#FFFFFF")).isWithin(0.01).of(21.0)
    }
}
