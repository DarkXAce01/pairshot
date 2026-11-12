package com.greasepencil.b4after.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class RotationTest {
    @Test fun `upright rotation snaps to the nearest quarter turn`() {
        assertThat(Rotation.uprightFromOrientation(-1)).isEqualTo(0)
        assertThat(Rotation.uprightFromOrientation(0)).isEqualTo(0)
        assertThat(Rotation.uprightFromOrientation(30)).isEqualTo(0)
        assertThat(Rotation.uprightFromOrientation(330)).isEqualTo(0)
        assertThat(Rotation.uprightFromOrientation(90)).isEqualTo(90)
        assertThat(Rotation.uprightFromOrientation(180)).isEqualTo(180)
        assertThat(Rotation.uprightFromOrientation(270)).isEqualTo(270)
    }

    @Test fun `quarter turns swap width and height`() {
        assertThat(Rotation.size(3, 4, 90)).isEqualTo(4 to 3)
        assertThat(Rotation.size(3, 4, 180)).isEqualTo(3 to 4)
    }
}

class ScheduleTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 11, 12, 16, 30).toInstant(zone).toEpochMilli()

    @Test fun `today and tomorrow round trip`() {
        Schedule.Day.entries.forEach { d ->
            assertThat(Schedule.dayOf(Schedule.startOf(d, now, zone), now, zone)).isEqualTo(d)
        }
    }

    @Test fun `a date far away shows as no date`() {
        val far = LocalDateTime.of(2026, 12, 25, 9, 0).toInstant(zone).toEpochMilli()
        assertThat(Schedule.dayOf(far, now, zone)).isEqualTo(Schedule.Day.NONE)
    }
}
