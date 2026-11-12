package com.greasepencil.b4after.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AfterQueueTest {
    // Shot order: Driveway (area 0) p1, p2; Patio (area 1) p3. Rows arrive shuffled on purpose.
    private val rows = listOf(
        pairRow("p3", areaPosition = 1, position = 0),
        pairRow("p2", areaPosition = 0, position = 1),
        pairRow("p1", areaPosition = 0, position = 0),
    )

    @Test fun `pending follows shooting order`() {
        assertThat(AfterQueue.pending(rows).map { it.pair.id }).containsExactly("p1", "p2", "p3").inOrder()
    }

    @Test fun `done and hidden pairs are skipped`() {
        val r = listOf(
            pairRow("p1", 0, 0, hasAfter = true),
            pairRow("p2", 0, 1, hidden = true),
            pairRow("p3", 1, 0),
        )
        assertThat(AfterQueue.pending(r).map { it.pair.id }).containsExactly("p3")
    }

    @Test fun `next is the first pending when nothing is finished yet`() {
        assertThat(AfterQueue.next(rows)?.pair?.id).isEqualTo("p1")
    }

    @Test fun `preferred pair wins while it still needs an after`() {
        assertThat(AfterQueue.next(rows, preferredId = "p3")?.pair?.id).isEqualTo("p3")
    }

    @Test fun `preferred pair that is already done falls back to the queue`() {
        val r = rows.map { if (it.pair.id == "p3") pairRow("p3", 1, 0, hasAfter = true) else it }
        assertThat(AfterQueue.next(r, preferredId = "p3")?.pair?.id).isEqualTo("p1")
    }

    @Test fun `after finishing one, moves on to the next in order`() {
        val r = listOf(pairRow("p1", 0, 0), pairRow("p2", 0, 1, hasAfter = true), pairRow("p3", 1, 0))
        assertThat(AfterQueue.next(r, justFinishedId = "p2")?.pair?.id).isEqualTo("p3")
    }

    @Test fun `wraps round to earlier pairs that were skipped`() {
        val r = listOf(pairRow("p1", 0, 0), pairRow("p2", 0, 1), pairRow("p3", 1, 0, hasAfter = true))
        assertThat(AfterQueue.next(r, justFinishedId = "p3")?.pair?.id).isEqualTo("p1")
    }

    @Test fun `null when every before has its after`() {
        val r = rows.map { pairRow(it.pair.id, it.areaPosition, it.pair.position, hasAfter = true) }
        assertThat(AfterQueue.next(r)).isNull()
    }

    @Test fun `empty job has no queue`() {
        assertThat(AfterQueue.next(emptyList())).isNull()
    }
}
