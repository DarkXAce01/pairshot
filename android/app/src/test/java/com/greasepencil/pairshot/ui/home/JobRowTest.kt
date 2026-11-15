package com.greasepencil.pairshot.ui.home

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.domain.summary
import org.junit.Test

class JobRowTest {
    private val dana = summary("1", name = "Dana Whitfield", service = "House wash", address = "12 Oak St")

    @Test fun inProgressRowSaysEverythingInOneLine() {
        val s = dana.copy(pairCount = 5, doneCount = 2)
        assertThat(jobRowDescription(s)).isEqualTo("Dana Whitfield, House wash, 12 Oak St, status In progress, 2 of 5 afters")
        assertThat(jobProgressLine(s)).isEqualTo("2 OF 5 AFTERS")
    }

    @Test fun progressLineOnlyWhileInProgress() {
        assertThat(jobProgressLine(dana)).isNull()
        assertThat(jobProgressLine(dana.copy(pairCount = 3, doneCount = 3))).isNull()
        assertThat(jobRowDescription(dana.copy(pairCount = 3, doneCount = 3)))
            .isEqualTo("Dana Whitfield, House wash, 12 Oak St, status Done")
    }

    @Test fun seenLink() {
        val s = dana.copy(pairCount = 2, doneCount = 2, linkRemoteState = "live", linkViewedAt = 5)
        assertThat(jobRowDescription(s)).endsWith("status Seen")
    }

    @Test fun missingFieldsAreLeftOut() {
        val bare = summary("2", name = "Marcus Lee", address = " ")
        assertThat(jobSubtitle(bare)).isNull()
        assertThat(jobRowDescription(bare)).isEqualTo("Marcus Lee, status Upcoming")
        assertThat(jobSubtitle(summary("3", address = "4 Elm Ct"))).isEqualTo("4 Elm Ct")
        assertThat(jobSubtitle(dana)).isEqualTo("House wash · 12 Oak St")
    }

    @Test fun sectionNames() {
        assertThat(earlierSectionName(hasToday = true)).isEqualTo("Earlier")
        assertThat(earlierSectionName(hasToday = false)).isEqualTo("Recent")
    }
}
