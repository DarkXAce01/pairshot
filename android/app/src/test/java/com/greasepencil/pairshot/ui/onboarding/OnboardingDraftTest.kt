package com.greasepencil.pairshot.ui.onboarding

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.domain.Trade
import com.greasepencil.pairshot.domain.gallery.BrandLook
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import org.junit.Test

class OnboardingDraftTest {
    @Test fun `starts on step one with the first trade's look`() {
        val d = OnboardingDraft()
        assertThat(d.step).isEqualTo(1)
        assertThat(d.accent).isEqualTo(BrandLook.NAVY.hex)
        assertThat(d.template).isEqualTo(ClientTemplate.CLASSIC)
    }

    @Test fun `every trade applies its own look`() {
        for (trade in Trade.entries) {
            val d = OnboardingDraft().withTrade(trade)
            assertThat(d.accent to d.template).isEqualTo(BrandLook.defaultsFor(trade))
        }
    }

    @Test fun `a chosen colour survives a trade change, the style still follows`() {
        val d = OnboardingDraft().withAccent(BrandLook.RUST.hex).withTrade(Trade.CAR_DETAILING)
        assertThat(d.accent).isEqualTo(BrandLook.RUST.hex)
        assertThat(d.template).isEqualTo(ClientTemplate.SHOWROOM)
    }

    @Test fun `a chosen style survives a trade change, the colour still follows`() {
        val d = OnboardingDraft().withTemplate(ClientTemplate.REPORT).withTrade(Trade.LANDSCAPING)
        assertThat(d.template).isEqualTo(ClientTemplate.REPORT)
        assertThat(d.accent).isEqualTo(BrandLook.GREEN.hex)
    }

    @Test fun `start saves what is shown, skip saves the trade's defaults`() {
        val d = OnboardingDraft().withName("Sparkle Wash").withTrade(Trade.WINDOW_CLEANING)
            .withAccent(BrandLook.PLUM.hex).withTemplate(ClientTemplate.SHOWROOM)
        assertThat(d.started()).isEqualTo(OnboardingDraft.Look("Sparkle Wash", Trade.WINDOW_CLEANING, BrandLook.PLUM.hex, ClientTemplate.SHOWROOM))
        assertThat(d.skipped()).isEqualTo(OnboardingDraft.Look("Sparkle Wash", Trade.WINDOW_CLEANING, BrandLook.BLUE.hex, ClientTemplate.BRIGHT))
    }

    @Test fun `next and back move between the two steps and keep the choices`() {
        val d = OnboardingDraft().withName("A").next()
        assertThat(d.step).isEqualTo(2)
        val back = d.withAccent(BrandLook.TEAL.hex).back()
        assertThat(back.step).isEqualTo(1)
        assertThat(back.accent).isEqualTo(BrandLook.TEAL.hex)
        assertThat(back.name).isEqualTo("A")
    }

    @Test fun `a colour that is not a colour is ignored`() {
        val d = OnboardingDraft().withAccent("nope")
        assertThat(d.accent).isEqualTo(BrandLook.NAVY.hex)
    }
}
