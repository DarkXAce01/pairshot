package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClientMessageTest {
    private val link = "https://pairshot.app/g/abc"

    @Test fun `fills first name, service and link`() {
        assertThat(ClientMessage.fill("Hi {first_name}, here's your {service} before & after: {link}", "Dana Ruiz", "House Wash", link))
            .isEqualTo("Hi Dana, here's your house wash before & after: $link")
    }

    @Test fun `no service says job, no name says there`() {
        assertThat(ClientMessage.fill("Hi {first_name}, your {service}: {link}", "  ", null, link)).isEqualTo("Hi there, your job: $link")
    }

    @Test fun `a template without the link still carries it`() {
        assertThat(ClientMessage.fill("Thanks {name}!", "Dana Ruiz", null, link)).isEqualTo("Thanks Dana Ruiz! $link")
    }

    @Test fun `a blank template falls back to the default`() {
        assertThat(ClientMessage.fill("", "Sam", "Roof clean", link)).isEqualTo("Hi Sam, here's your roof clean before & after: $link")
    }

    @Test fun `times read plainly`() {
        val h = 3_600_000L; val d = 24 * h
        assertThat(Ago.past(0, 30_000)).isEqualTo("just now")
        assertThat(Ago.past(0, 2 * h)).isEqualTo("2 hours ago")
        assertThat(Ago.past(0, d)).isEqualTo("1 day ago")
        assertThat(Ago.until(14 * d, 0)).isEqualTo("in 14 days")
        assertThat(Ago.until(13 * d + 1, 0)).isEqualTo("in 14 days")
        assertThat(Ago.until(h / 2, 0)).isEqualTo("in 30 minutes")
        assertThat(Ago.until(0, 5)).isEqualTo("now")
    }
}
