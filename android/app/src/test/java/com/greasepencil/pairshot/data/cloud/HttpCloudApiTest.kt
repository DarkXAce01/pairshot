package com.greasepencil.pairshot.data.cloud

import com.google.common.truth.Truth.assertThat
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/** The real HTTP client against a tiny local server: headers, bodies and errors. */
class HttpCloudApiTest {
    private lateinit var server: MockWebServer
    private val base get() = server.url("/").toString().trimEnd('/')

    private fun reply(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun seen(): Triple<String, Map<String, String>, ByteArray> {
        val r = server.takeRequest()
        return Triple("${r.method} ${r.path}", r.headers.toMultimap().mapValues { it.value.first() }, r.body.readByteArray())
    }

    @Before fun start() {
        server = MockWebServer()
        server.start()
    }

    @After fun stop() = server.shutdown()

    @Test fun `sends the session token and JSON, and reads the answer`() = runBlocking {
        reply(201, """{"id":"g1","jobId":"j1","url":"https://x/g/abc","state":"live","viewCount":0,"extra":"ignored"}""")
        val req = SendRequest("j1", "EXT-1", SendJob("Dana"), kotlinx.serialization.json.JsonObject(emptyMap()))
        val g = HttpCloudApi(base, "tok").publish("g1", req)
        assertThat(g.url).isEqualTo("https://x/g/abc")
        val (line, headers, body) = seen()
        assertThat(line).isEqualTo("POST /v1/galleries/g1/publish")
        assertThat(headers["authorization"]).isEqualTo("Bearer tok")
        assertThat(headers["content-type"]).isEqualTo("application/json")
        assertThat(body.decodeToString()).contains("\"externalId\":\"EXT-1\"")
    }

    @Test fun `photos go up as raw JPEG bytes`() = runBlocking {
        reply(201, """{"ok":true,"stored":true}""")
        val bytes = byteArrayOf(-1, -40, -1, 1, 2, 3)
        HttpCloudApi("$base/", "tok").uploadPhoto("abc", bytes)
        val (line, headers, body) = seen()
        assertThat(line).isEqualTo("PUT /v1/photos/abc")
        assertThat(headers["content-type"]).isEqualTo("image/jpeg")
        assertThat(body).isEqualTo(bytes)
    }

    @Test fun `a refusal carries the server's code and words`() {
        reply(402, """{"error":"monthly_limit","message":"You've sent your 3 free galleries this month."}""")
        val e = assertThrows(CloudException::class.java) { runBlocking { HttpCloudApi(base, "t").usage() } }
        assertThat(e.status).isEqualTo(402)
        assertThat(e.code).isEqualTo("monthly_limit")
        assertThat(e.message).isEqualTo("You've sent your 3 free galleries this month.")
        assertThat(e.retryable).isFalse()
    }

    @Test fun `server trouble without a body is still a clear, retryable error`() {
        reply(502, "")
        val e = assertThrows(CloudException::class.java) { runBlocking { HttpCloudApi(base, "t").usage() } }
        assertThat(e.retryable).isTrue()
    }

    @Test fun `nobody listening means offline`() {
        val port = ServerSocket(0).use { it.localPort }
        assertThrows(OfflineException::class.java) { runBlocking { HttpCloudApi("http://127.0.0.1:$port", "t").me() } }
    }

    @Test fun `CRM pull sends the cursor`() = runBlocking {
        reply(200, """{"jobs":[{"externalId":"J1","clientName":"Dana","cancelled":false}],"cursor":7,"more":false}""")
        val r = HttpCloudApi(base, "t").crmJobs(42)
        assertThat(seen().first).isEqualTo("GET /v1/crm/jobs?since=42")
        assertThat(r.jobs.single().externalId).isEqualTo("J1")
        assertThat(r.cursor).isEqualTo(7)
    }

    @Test fun `webhook lists read the server's snake_case`() = runBlocking {
        reply(200, """{"webhooks":[{"id":"h","url":"https://z","events":"gallery.published","created_at":5}],
            "deliveries":[{"id":"d","webhook_id":"h","event":"gallery.published","status":"pending","attempts":1,"last_status":500,"last_error":null,"created_at":6}]}""")
        val r = HttpCloudApi(base, "t").webhooks()
        assertThat(r.webhooks.single().createdAt).isEqualTo(5)
        assertThat(r.deliveries.single().lastStatus).isEqualTo(500)
    }
}
