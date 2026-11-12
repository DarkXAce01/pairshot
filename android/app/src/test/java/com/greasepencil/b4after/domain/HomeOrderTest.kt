package com.greasepencil.b4after.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class HomeOrderTest {
    private val zone = ZoneOffset.UTC
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).toInstant(zone).toEpochMilli()
    private val now = at(12, 8)

    @Test fun `today's jobs come first in time order, the rest by latest activity`() {
        val jobs = listOf(
            summary("late-today", scheduledAt = at(12, 15)),
            summary("early-today", scheduledAt = at(12, 9)),
            summary("tomorrow", scheduledAt = at(13, 9), updatedAt = at(11, 9)),
            summary("old", updatedAt = at(1, 9)),
            summary("shot-recently", updatedAt = at(2, 9), lastShotAt = at(11, 20)),
        )
        val s = HomeOrder.sections(jobs, now, zone)
        assertThat(s.today.map { it.job.id }).containsExactly("early-today", "late-today").inOrder()
        assertThat(s.recent.map { it.job.id }).containsExactly("shot-recently", "tomorrow", "old").inOrder()
    }

    @Test fun `search matches name, address and service, ignoring case`() {
        val j = summary("1", name = "Dana Ruiz", address = "12 Oak St", service = "House wash")
        assertThat(HomeOrder.matches(j, "dana")).isTrue()
        assertThat(HomeOrder.matches(j, "OAK")).isTrue()
        assertThat(HomeOrder.matches(j, "wash")).isTrue()
        assertThat(HomeOrder.matches(j, "roof")).isFalse()
        assertThat(HomeOrder.matches(j, "  ")).isTrue()
    }
}
