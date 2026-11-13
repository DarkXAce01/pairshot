package com.greasepencil.pairshot.data.cloud

import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.data.prefs.SettingsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A pretend server that behaves like server/src for what the app uses. */
class FakeCloudApi : CloudApi {
    val stored = mutableSetOf<String>()
    val uploads = mutableListOf<String>()
    val published = mutableMapOf<String, SendRequest>()
    var galleries = mutableMapOf<String, GalleryDto>()
    var crmPages = ArrayDeque<CrmPullResponse>()
    val crmSince = mutableListOf<Long>()
    val closed = mutableListOf<String>()
    /** Thrown by the next call, once. */
    var failNext: Exception? = null
    /** Thrown by every call. */
    var failAlways: Exception? = null
    var calls = 0

    private fun gate() {
        calls++
        failAlways?.let { throw it }
        failNext?.let { failNext = null; throw it }
    }

    override suspend fun signIn(req: AuthRequest) = AuthResponse("t", AccountDto("acc", "free"))
    override suspend fun me(): AccountDto { gate(); return AccountDto("acc", "free") }
    override suspend fun usage(): UsageDto { gate(); return UsageDto("free", 0, 3, 30, 0, 1, 14) }
    override suspend fun setDevPlan(plan: String) = gate()
    val claims = mutableListOf<String>()
    /** What the server says to a Pro claim: "pro" or "pending". */
    var claimStatus = "pro"
    override suspend fun claimPro(purchaseToken: String, productId: String): ClaimProResponse {
        gate()
        claims += purchaseToken
        return ClaimProResponse(claimStatus, AccountDto("acc", if (claimStatus == "pro") "pro" else "free"))
    }
    override suspend fun deleteEverything() = gate()
    override suspend fun signOut() = gate()

    override suspend fun uploadPhoto(hash: String, jpeg: ByteArray) {
        gate()
        check(sha256Hex(jpeg) == hash) { "hash mismatch" }
        uploads += hash
        stored += hash
    }

    override suspend fun prepare(galleryId: String, req: SendRequest): PrepareResponse {
        gate()
        return PrepareResponse(srcs(req.manifest).filter { it !in stored })
    }

    override suspend fun publish(galleryId: String, req: SendRequest): GalleryDto {
        gate()
        check(srcs(req.manifest).all { it in stored }) { "photos_missing" }
        published[galleryId] = req
        val old = galleries[galleryId]
        val g = old?.copy(updatedAt = 2) ?: GalleryDto(galleryId, req.jobId, req.externalId, "https://pairshot.test/g/${galleryId.take(12)}", "live", 1, 1, 1_000_000)
        galleries[galleryId] = g
        return g
    }

    override suspend fun galleries(): List<GalleryDto> { gate(); return galleries.values.toList() }
    override suspend fun closeGallery(galleryId: String) {
        gate(); closed += galleryId
        galleries[galleryId]?.let { galleries[galleryId] = it.copy(state = "revoked") }
    }

    override suspend fun crmJobs(since: Long): CrmPullResponse {
        gate(); crmSince += since
        return crmPages.removeFirstOrNull() ?: CrmPullResponse(emptyList(), since, false)
    }
    override suspend fun rotateInbound(): String { gate(); return "https://pairshot.test/hooks/in/new" }
    override suspend fun webhooks(): WebhooksResponse { gate(); return WebhooksResponse(emptyList(), emptyList()) }
    override suspend fun addWebhook(url: String, events: List<String>?): NewWebhookDto { gate(); return NewWebhookDto("h", url, emptyList(), "whsec_x") }
    override suspend fun deleteWebhook(id: String) = gate()
    override suspend fun testWebhook(id: String): Boolean { gate(); return true }
    override suspend fun apiKeys(): List<ApiKeyDto> { gate(); return emptyList() }
    override suspend fun createApiKey(): NewApiKeyDto { gate(); return NewApiKeyDto("k", "ps_x", "ps_x") }
    override suspend fun deleteApiKey(id: String) = gate()

    companion object {
        fun srcs(manifest: JsonElement): List<String> {
            val m = manifest.jsonObject
            val pairs = listOfNotNull(m["cover"]?.takeIf { it is JsonObject }?.jsonObject) +
                (m["sections"]?.jsonArray ?: emptyList()).flatMap { it.jsonObject["pairs"]!!.jsonArray.map { p -> p.jsonObject } }
            return pairs.flatMap { listOf(it["before"]!!.jsonObject["src"]!!.jsonPrimitive.content, it["after"]!!.jsonObject["src"]!!.jsonPrimitive.content) }.distinct()
        }
    }
}

class FakeAccess(val api: CloudApi, plan: String = "free", signedIn: Boolean = true) : CloudAccess {
    override val state = MutableStateFlow(CloudState(serverUrl = "https://pairshot.test", deviceId = "d", token = if (signedIn) "t" else null, plan = plan))
    var signOuts = 0
    override suspend fun api(): CloudApi = api
    override suspend fun signedOut() { signOuts++ }
    override suspend fun saveAccount(a: AccountDto) { state.value = state.value.copy(plan = a.plan) }
    override suspend fun setCrmCursor(cursor: Long, at: Long) { state.value = state.value.copy(crmCursor = cursor, lastCrmSync = at) }
}

class FakeSettings(s: AppSettings = AppSettings(businessName = "Shine Bros", businessPhone = "555 0100")) : SettingsSource {
    override val settings = MutableStateFlow(s)
}

/** Photo bytes that look like a JPEG and differ per photo and rotation. */
class FakePhotos : PhotoSource {
    val unreadable = mutableSetOf<String>()
    val reads = mutableListOf<String>()
    override suspend fun prepared(fileName: String, rotation: Int): ByteArray? {
        reads += "$fileName|$rotation"
        if (fileName in unreadable) return null
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + "$fileName@$rotation".encodeToByteArray()
    }
}
