package com.greasepencil.pairshot.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayIntegrityTest {
    /** The server's requestHashFor("phone-1") is sha256("pairshot-signin:phone-1") in hex; both sides must agree. */
    @Test fun `request hash matches the server's`() {
        assertEquals("8aa2a70a2ad2225a0ebd1e7e3f7b0ed401385831d204a311d69f2d29ad552553", PlayIntegrity.requestHash("phone-1"))
    }
}
