package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AreaPresetsTest {
    @Test fun `new business gets the trade defaults`() {
        assertThat(AreaPresets.rank(Trade.PRESSURE_WASHING, emptyList()))
            .isEqualTo(Trade.PRESSURE_WASHING.defaultAreas)
    }

    @Test fun `names typed at least twice come first, most used first`() {
        val used = listOf("Pool deck", "pool deck ", "Garage", "Garage", "Garage", "Once only")
        val ranked = AreaPresets.rank(Trade.PRESSURE_WASHING, used)
        assertThat(ranked.take(2)).containsExactly("Garage", "Pool deck").inOrder()
        assertThat(ranked).doesNotContain("Once only")
    }

    @Test fun `no duplicates between learned and defaults, ignoring case`() {
        val ranked = AreaPresets.rank(Trade.PRESSURE_WASHING, listOf("driveway", "Driveway"))
        assertThat(ranked.count { it.equals("driveway", ignoreCase = true) }).isEqualTo(1)
        assertThat(ranked.first()).isEqualTo("driveway")
    }

    @Test fun `respects the limit`() {
        val used = (1..20).flatMap { listOf("Spot $it", "Spot $it") }
        assertThat(AreaPresets.rank(Trade.OTHER, used, limit = 5)).hasSize(5)
    }

    @Test fun `blank names are ignored`() {
        assertThat(AreaPresets.rank(Trade.OTHER, listOf(" ", "", " "))).isEqualTo(Trade.OTHER.defaultAreas)
    }
}
