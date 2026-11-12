package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AfterQueueTest {
    // Shot in this order: p1 (no label), p2 (Driveway), p3 (no label). Rows arrive shuffled on purpose.
    private val rows = listOf(
        pairRow("p3", shotAt = 3),
        pairRow("p2", shotAt = 2, areaPosition = 0),
        pairRow("p1", shotAt = 1),
    )

    @Test fun `pending follows shooting order, labelled or not`() {
        assertThat(AfterQueue.pending(rows).map { it.pair.id }).containsExactly("p1", "p2", "p3").inOrder()
    }

    @Test fun `done and hidden pairs are skipped`() {
        val r = listOf(pairRow("p1", 1, hasAfter = true), pairRow("p2", 2, hidden = true), pairRow("p3", 3))
        assertThat(AfterQueue.pending(r).map { it.pair.id }).containsExactly("p3")
    }

    @Test fun `next is the first pending when nothing is finished yet`() {
        assertThat(AfterQueue.next(rows)?.pair?.id).isEqualTo("p1")
    }

    @Test fun `preferred pair wins while it still needs an after`() {
        assertThat(AfterQueue.next(rows, preferredId = "p3")?.pair?.id).isEqualTo("p3")
    }

    @Test fun `preferred pair that is already done falls back to the queue`() {
        val r = listOf(pairRow("p1", 1), pairRow("p3", 3, hasAfter = true))
        assertThat(AfterQueue.next(r, preferredId = "p3")?.pair?.id).isEqualTo("p1")
    }

    @Test fun `after finishing one, moves on to the next shot`() {
        val r = listOf(pairRow("p1", 1), pairRow("p2", 2, hasAfter = true), pairRow("p3", 3))
        assertThat(AfterQueue.next(r, justFinishedId = "p2")?.pair?.id).isEqualTo("p3")
    }

    @Test fun `wraps round to earlier pairs that were skipped`() {
        val r = listOf(pairRow("p1", 1), pairRow("p2", 2), pairRow("p3", 3, hasAfter = true))
        assertThat(AfterQueue.next(r, justFinishedId = "p3")?.pair?.id).isEqualTo("p1")
    }

    @Test fun `null when every before has its after`() {
        val r = rows.map { pairRow(it.pair.id, it.pair.createdAt, hasAfter = true) }
        assertThat(AfterQueue.next(r)).isNull()
    }

    @Test fun `empty job has no queue`() {
        assertThat(AfterQueue.next(emptyList())).isNull()
    }
}
