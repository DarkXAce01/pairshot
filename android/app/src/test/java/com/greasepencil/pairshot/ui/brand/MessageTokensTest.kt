package com.greasepencil.pairshot.ui.brand

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.domain.Trade
import org.junit.Test

class MessageTokensTest {
    @Test fun insertsAtTheCursorAndMovesItPastTheToken() {
        val r = MessageTokens.insert("Hi , thanks", 3, 3, "{first_name}")
        assertThat(r.text).isEqualTo("Hi {first_name}, thanks")
        assertThat(r.cursor).isEqualTo(3 + "{first_name}".length)
    }

    @Test fun replacesTheSelection() {
        val r = MessageTokens.insert("Here: LINK", 6, 10, "{link}")
        assertThat(r.text).isEqualTo("Here: {link}")
        assertThat(r.cursor).isEqualTo(r.text.length)
    }

    @Test fun backwardsSelectionIsTheSameRange() {
        assertThat(MessageTokens.insert("Here: LINK", 10, 6, "{link}").text).isEqualTo("Here: {link}")
    }

    @Test fun addsASpaceRatherThanRunningIntoAWord() {
        val r = MessageTokens.insert("Hi", 2, 2, "{first_name}")
        assertThat(r.text).isEqualTo("Hi {first_name}")
        assertThat(r.cursor).isEqualTo(r.text.length)
        assertThat(MessageTokens.insert("yourphotos", 4, 4, "{service}").text).isEqualTo("your {service} photos")
    }

    @Test fun noExtraSpaceBeforePunctuationOrAtTheStart() {
        assertThat(MessageTokens.insert("", 0, 0, "{link}").text).isEqualTo("{link}")
        assertThat(MessageTokens.insert("Hi .", 3, 3, "{first_name}").text).isEqualTo("Hi {first_name}.")
    }

    @Test fun outOfRangeCursorIsClamped() {
        assertThat(MessageTokens.insert("Hi ", 99, 99, "{link}").text).isEqualTo("Hi {link}")
    }

    @Test fun knowsWhenTheLinkIsMissing() {
        assertThat(MessageTokens.hasLink("Hi {first_name}")).isFalse()
        assertThat(MessageTokens.hasLink("See {link}")).isTrue()
    }

    @Test fun sampleUsesDanaTheTradesServiceAndASampleLink() {
        val text = MessageSample.fill("Hi {first_name}, here's your {service} before & after: {link}", Trade.PRESSURE_WASHING)
        assertThat(text).isEqualTo("Hi Dana, here's your house wash before & after: ${MessageSample.LINK}")
    }

    @Test fun everyTradeHasASampleService() {
        Trade.entries.forEach { assertThat(MessageSample.serviceFor(it)).isNotEmpty() }
    }
}
