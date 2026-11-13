package com.greasepencil.pairshot.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The server said no, with a reason meant for the user (e.g. "You've sent your 3 free galleries this month."). */
class CloudException(val status: Int, val code: String, override val message: String) : Exception(message) {
    /** Worth trying again later (server trouble, rate limits), rather than something the user must fix. */
    val retryable: Boolean get() = status >= 500 || status == 429 || code == "busy"
}

/** No connection, or the server couldn't be reached. Sends wait and try again. */
class OfflineException(cause: Throwable? = null) : IOException("No connection right now. It'll go when you're back online.", cause)

/** Everything the app asks of the Pairshot server. */
interface CloudApi {
    suspend fun signIn(req: AuthRequest): AuthResponse
    suspend fun me(): AccountDto
    suspend fun usage(): UsageDto
    suspend fun setDevPlan(plan: String)
    suspend fun deleteEverything()
    /** Ends this phone's session on the server. */
    suspend fun signOut()

    suspend fun uploadPhoto(hash: String, jpeg: ByteArray)
    suspend fun prepare(galleryId: String, req: SendRequest): PrepareResponse
    suspend fun publish(galleryId: String, req: SendRequest): GalleryDto
    suspend fun galleries(): List<GalleryDto>
    suspend fun closeGallery(galleryId: String)

    suspend fun crmJobs(since: Long): CrmPullResponse
    suspend fun rotateInbound(): String
    suspend fun webhooks(): WebhooksResponse
    suspend fun addWebhook(url: String, events: List<String>? = null): NewWebhookDto
    suspend fun deleteWebhook(id: String)
    suspend fun testWebhook(id: String): Boolean
    suspend fun apiKeys(): List<ApiKeyDto>
    suspend fun createApiKey(): NewApiKeyDto
    suspend fun deleteApiKey(id: String)
}

/** [CloudApi] over plain HttpURLConnection: no extra libraries, and nothing the server doesn't need. */
class HttpCloudApi(baseUrl: String, private val token: String?) : CloudApi {
    private val base = baseUrl.trimEnd('/')

    private suspend fun <T> request(
        method: String,
        path: String,
        out: KSerializer<T>,
        body: ByteArray? = null,
        contentType: String = "application/json",
    ): T = withContext(Dispatchers.IO) {
        val conn = try {
            (URL(base + path).openConnection() as HttpURLConnection)
        } catch (e: Exception) {
            throw CloudException(0, "bad_server", "The server address looks wrong: $base")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("accept", "application/json")
            token?.let { conn.setRequestProperty("authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("content-type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            val text = (if (status >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            if (status >= 400) {
                val err = runCatching { CloudJson.decodeFromString(ErrorDto.serializer(), text) }.getOrNull()
                throw CloudException(status, err?.error ?: "http_$status", err?.message ?: "The server had a problem ($status). Try again soon.")
            }
            CloudJson.decodeFromString(out, text)
        } catch (e: IOException) {
            throw OfflineException(e)
        } finally {
            conn.disconnect()
        }
    }

    private suspend inline fun <reified B, T> send(method: String, path: String, body: B, out: KSerializer<T>): T =
        request(method, path, out, CloudJson.encodeToString(kotlinx.serialization.serializer<B>(), body).encodeToByteArray())

    override suspend fun signIn(req: AuthRequest) = send("POST", "/v1/auth", req, AuthResponse.serializer())
    override suspend fun me() = request("GET", "/v1/me", AccountDto.serializer())
    override suspend fun usage() = request("GET", "/v1/usage", UsageDto.serializer())
    override suspend fun setDevPlan(plan: String) { send("POST", "/v1/dev/plan", PlanRequest(plan), PlanRequest.serializer()) }
    override suspend fun deleteEverything() { request("DELETE", "/v1/me", OkDto.serializer()) }
    override suspend fun signOut() { request("DELETE", "/v1/session", OkDto.serializer()) }

    override suspend fun uploadPhoto(hash: String, jpeg: ByteArray) {
        request("PUT", "/v1/photos/$hash", OkDto.serializer(), jpeg, "image/jpeg")
    }
    override suspend fun prepare(galleryId: String, req: SendRequest) =
        send("POST", "/v1/galleries/$galleryId/prepare", req, PrepareResponse.serializer())
    override suspend fun publish(galleryId: String, req: SendRequest) =
        send("POST", "/v1/galleries/$galleryId/publish", req, GalleryDto.serializer())
    override suspend fun galleries() = request("GET", "/v1/galleries", GalleriesResponse.serializer()).galleries
    override suspend fun closeGallery(galleryId: String) { request("DELETE", "/v1/galleries/$galleryId", OkDto.serializer()) }

    override suspend fun crmJobs(since: Long) = request("GET", "/v1/crm/jobs?since=$since", CrmPullResponse.serializer())
    override suspend fun rotateInbound() = request("POST", "/v1/crm/rotate", InboundResponse.serializer()).inboundUrl
    override suspend fun webhooks() = request("GET", "/v1/webhooks", WebhooksResponse.serializer())
    override suspend fun addWebhook(url: String, events: List<String>?) =
        send("POST", "/v1/webhooks", AddWebhookRequest(url, events), NewWebhookDto.serializer())
    override suspend fun deleteWebhook(id: String) { request("DELETE", "/v1/webhooks/$id", OkDto.serializer()) }
    override suspend fun testWebhook(id: String) = request("POST", "/v1/webhooks/$id/test", OkDto.serializer()).ok
    override suspend fun apiKeys() = request("GET", "/v1/api-keys", ApiKeysResponse.serializer()).keys
    override suspend fun createApiKey() = request("POST", "/v1/api-keys", NewApiKeyDto.serializer())
    override suspend fun deleteApiKey(id: String) { request("DELETE", "/v1/api-keys/$id", OkDto.serializer()) }

}
