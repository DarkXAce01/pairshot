package com.greasepencil.pairshot.data.billing

import com.greasepencil.pairshot.data.cloud.CloudException
import com.greasepencil.pairshot.data.cloud.FakeAccess
import com.greasepencil.pairshot.data.cloud.FakeCloudApi
import com.greasepencil.pairshot.data.cloud.OfflineException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProClaimsTest {
    private val api = FakeCloudApi()
    private val access = FakeAccess(api)

    private fun paid(token: String = "tok-1", product: String = "pro") = OwnedPurchase(token, listOf(product), purchased = true, pending = false)

    @Test fun `a paid purchase is claimed and the account becomes Pro`() = runTest {
        assertEquals(ProResult.Pro, ProClaims.claim(access, listOf(paid())))
        assertEquals(listOf("tok-1"), api.claims)
        assertTrue(access.state.value.isPro)
    }

    @Test fun `nothing owned means nothing to restore, and the server isn't asked`() = runTest {
        assertEquals(ProResult.NothingToRestore, ProClaims.claim(access, emptyList()))
        assertEquals(ProResult.NothingToRestore, ProClaims.claim(access, listOf(paid(product = "something_else"))))
        assertTrue(api.claims.isEmpty())
    }

    @Test fun `a pending payment waits without asking the server`() = runTest {
        val pending = OwnedPurchase("tok-p", listOf("pro"), purchased = false, pending = true)
        assertEquals(ProResult.Pending, ProClaims.claim(access, listOf(pending)))
        assertTrue(api.claims.isEmpty())
    }

    @Test fun `the server can still say pending`() = runTest {
        api.claimStatus = "pending"
        assertEquals(ProResult.Pending, ProClaims.claim(access, listOf(paid())))
        assertEquals(false, access.state.value.isPro)
    }

    @Test fun `offline and refusals become messages`() = runTest {
        api.failNext = OfflineException()
        assertTrue(ProClaims.claim(access, listOf(paid())) is ProResult.Failed)
        api.failNext = CloudException(409, "used_elsewhere", "This Pro purchase is already on another Pairshot account.")
        val r = ProClaims.claim(access, listOf(paid()))
        assertEquals("This Pro purchase is already on another Pairshot account.", (r as ProResult.Failed).message)
    }
}
