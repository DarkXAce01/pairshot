package com.greasepencil.pairshot.ui.job

import com.greasepencil.pairshot.data.db.GalleryEntity
import com.greasepencil.pairshot.data.db.SendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The job bottom bar and link panel against every SendState × remoteState × changed × canSend. */
class JobBarTest {
    private fun g(state: SendState, remote: String? = null, url: String? = "https://p/g/x") =
        GalleryEntity(id = "g", jobId = "j", sendState = state, remoteState = remote, url = url, updatedAt = 0)

    private fun bar(g: GalleryEntity?, changed: Boolean = false, canSend: Boolean = true) =
        jobBar(LinkState(gallery = g, changed = changed, canSend = canSend))

    @Test fun noFinishedPairHidesBar() = assertNull(bar(null, canSend = false))
    @Test fun noLinkSends() = assertEquals(JobBar(JobBarAction.SEND), bar(null))
    @Test fun closedSendsNew() = assertEquals(JobBar(JobBarAction.SEND_NEW), bar(g(SendState.SENT, "revoked")))
    @Test fun expiredSendsNew() = assertEquals(JobBar(JobBarAction.SEND_NEW), bar(g(SendState.SENT, "expired"), changed = true))
    @Test fun queuedIsSending() = assertEquals(JobBar(JobBarAction.SENDING), bar(g(SendState.QUEUED)))
    @Test fun sendingEvenWithoutPairs() = assertEquals(JobBar(JobBarAction.SENDING), bar(g(SendState.SENDING), canSend = false))
    @Test fun liveUnchangedIsPreviewOnly() = assertEquals(JobBar(null), bar(g(SendState.SENT, "live")))
    @Test fun liveChangedUpdates() = assertEquals(JobBar(JobBarAction.UPDATE), bar(g(SendState.SENT, "live"), changed = true))
    @Test fun failedTriesAgain() = assertEquals(JobBar(JobBarAction.TRY_AGAIN), bar(g(SendState.FAILED, "revoked")))
    @Test fun onlyTryAgainIsInk() = JobBarAction.entries.forEach { assertEquals(it != JobBarAction.TRY_AGAIN, it.accent) }

    @Test fun everyCombination() {
        val states = listOf<SendState?>(null) + SendState.entries
        for (st in states) for (remote in listOf(null, "live", "revoked", "expired")) for (changed in listOf(false, true)) for (canSend in listOf(false, true)) {
            val gal = st?.let { g(it, remote) }
            val b = bar(gal, changed, canSend)
            val closed = remote == "revoked" || remote == "expired"
            val expected: JobBarAction? = when {
                st == SendState.QUEUED || st == SendState.SENDING -> JobBarAction.SENDING
                !canSend -> { assertNull(b); continue }
                st == null -> JobBarAction.SEND
                st == SendState.FAILED -> JobBarAction.TRY_AGAIN
                closed -> JobBarAction.SEND_NEW
                changed -> JobBarAction.UPDATE
                else -> null
            }
            assertEquals("$st $remote $changed $canSend", JobBar(expected), b)
            val mode = linkPanelMode(gal)
            val expectedMode = when {
                st == null -> LinkPanelMode.ABSENT
                st == SendState.QUEUED || st == SendState.SENDING -> LinkPanelMode.WORKING
                st == SendState.FAILED -> LinkPanelMode.FAILED
                closed -> LinkPanelMode.CLOSED
                else -> LinkPanelMode.LIVE
            }
            assertEquals(expectedMode, mode)
        }
    }

    @Test fun workingWords() {
        assertEquals("Sending…", workingText(g(SendState.SENDING, url = null), wifiOnly = false))
        assertEquals("Updating the link…", workingText(g(SendState.SENDING), wifiOnly = false))
        assertEquals("Waiting for Wi-Fi. It'll go by itself.", workingText(g(SendState.QUEUED), wifiOnly = true))
        assertEquals("Waiting for signal. It'll go by itself.", workingText(g(SendState.QUEUED), wifiOnly = false))
    }

    @Test fun hints() {
        assertEquals("No photos yet. Tap Before and shoot. Labels like \"Driveway\" are optional.", jobHint(0, false, false))
        assertEquals("Finish a before and after to send a link.", jobHint(2, false, false))
        assertEquals("Send makes a page with these before and afters for your client. Their name isn't on it.", jobHint(2, true, false))
        assertNull(jobHint(2, true, true))
    }

    @Test fun meta() {
        assertEquals("House wash · 12 Oak St", jobMeta("House wash", "12 Oak St"))
        assertEquals("12 Oak St", jobMeta(" ", "12 Oak St"))
        assertNull(jobMeta(null, null))
    }
}
