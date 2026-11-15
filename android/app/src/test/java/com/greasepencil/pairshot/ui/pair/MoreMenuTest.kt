package com.greasepencil.pairshot.ui.pair

import org.junit.Assert.assertEquals
import org.junit.Test

class MoreMenuTest {
    @Test fun lonePairWithoutAfterCanOnlyBeDeleted() {
        val m = moreMenu(siblings = 1, index = 0, jobPairs = 1, hasAfter = false)
        assertEquals(listOf(MoreItem.DELETE), m.items)
        assertEquals(emptySet<MoreItem>(), m.disabled)
    }

    @Test fun fullMenuInOrderWithDeleteLast() {
        val m = moreMenu(siblings = 3, index = 1, jobPairs = 4, hasAfter = true)
        assertEquals(
            listOf(MoreItem.EARLIER, MoreItem.LATER, MoreItem.RE_PAIR, MoreItem.SWAP, MoreItem.PICK_AFTER, MoreItem.DELETE),
            m.items,
        )
        assertEquals(emptySet<MoreItem>(), m.disabled)
    }

    @Test fun endsDisableMoves() {
        assertEquals(setOf(MoreItem.EARLIER), moreMenu(3, 0, 3, true).disabled)
        assertEquals(setOf(MoreItem.LATER), moreMenu(3, 2, 3, true).disabled)
    }

    @Test fun needsAfterHasNoSwap() {
        val m = moreMenu(siblings = 2, index = 0, jobPairs = 2, hasAfter = false)
        assertEquals(listOf(MoreItem.EARLIER, MoreItem.LATER, MoreItem.RE_PAIR, MoreItem.DELETE), m.items)
    }

    @Test fun toolWords() {
        assertEquals("Cropped", PairToolText.crop(true))
        assertEquals("Make cover", PairToolText.cover(false))
        assertEquals("Hidden", PairToolText.shown(true))
        assertEquals("Shown to client", PairToolText.shownState(false))
    }
}
