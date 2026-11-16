package com.greasepencil.pairshot.data.cloud

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.domain.pairRow
import org.junit.Test

class UnreadableMessageTest {
    @Test fun namesTheSideLabelAndPair() {
        val pairs = listOf(pairRow("a", 1, hasAfter = true, areaPosition = 0), pairRow("b", 2, hasAfter = true, areaPosition = 0))
        assertThat(unreadableMessage("af-b.jpg", pairs))
            .isEqualTo("The after photo in \"Area 0\" (pair 2) couldn't be read. Open that pair and retake the after, or hide the pair.")
        assertThat(unreadableMessage("b-a.jpg", pairs)).startsWith("The before photo in \"Area 0\" (pair 1)")
    }

    @Test fun unknownFileFallsBack() {
        assertThat(unreadableMessage("x.jpg", emptyList())).isEqualTo("A photo on this job couldn't be read. Retake or hide it.")
    }
}
