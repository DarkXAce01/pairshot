package com.greasepencil.pairshot.ui.job

import com.greasepencil.pairshot.data.db.GalleryEntity
import com.greasepencil.pairshot.data.db.SendState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StallTest {
    private fun g(state: SendState, at: Long = 0) = GalleryEntity(id = "g", jobId = "j", sendState = state, updatedAt = at)
    private val later = STALL_MILLIS + 1

    @Test fun freshSendIsNotStalled() = assertFalse(looksStalled(g(SendState.SENDING), 60_000, wifiOnly = false))
    @Test fun longSendIsStalled() = assertTrue(looksStalled(g(SendState.SENDING), later, wifiOnly = false))
    @Test fun longQueueIsStalled() = assertTrue(looksStalled(g(SendState.QUEUED), later, wifiOnly = false))
    @Test fun waitingForWifiIsNotStalled() = assertFalse(looksStalled(g(SendState.QUEUED), later, wifiOnly = true))
    @Test fun sentIsNotStalled() = assertFalse(looksStalled(g(SendState.SENT), later, wifiOnly = false))
    @Test fun noGalleryIsNotStalled() = assertFalse(looksStalled(null, later, wifiOnly = false))
}
