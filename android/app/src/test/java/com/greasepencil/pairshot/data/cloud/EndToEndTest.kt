package com.greasepencil.pairshot.data.cloud

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.data.repo.CapturedPhoto
import com.greasepencil.pairshot.data.repo.Clock
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.data.repo.PhotoFiles
import com.greasepencil.pairshot.domain.Ids
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app's real client and sender against a real local server (server/: npm run dev).
 * Skipped unless PAIRSHOT_SERVER is set, e.g.
 *   PAIRSHOT_SERVER=http://localhost:8787 ./gradlew :app:testDebugUnitTest --tests '*EndToEndTest*'
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    private val server: String? = System.getenv("PAIRSHOT_SERVER")

    private class Counting(val inner: CloudApi) : CloudApi by inner {
        var uploads = 0
        override suspend fun uploadPhoto(hash: String, jpeg: ByteArray) { uploads++; inner.uploadPhoto(hash, jpeg) }
    }

    /** Signs in like the app does on first Send: just this phone's id (the dev server allows it). */
    private class LiveAccess(val server: String) : CloudAccess {
        override val state = MutableStateFlow(CloudState(serverUrl = server, deviceId = Ids.new()))
        lateinit var api: Counting
        override suspend fun api(): CloudApi {
            if (!state.value.signedIn) {
                val auth = HttpCloudApi(server, null).signIn(AuthRequest(state.value.deviceId))
                state.value = state.value.copy(token = auth.token)
                saveAccount(auth.account)
                api = Counting(HttpCloudApi(server, auth.token))
            }
            return api
        }
        override suspend fun signedOut() { state.value = state.value.copy(token = null) }
        override suspend fun saveAccount(a: AccountDto) { state.value = state.value.copy(plan = a.plan, inboundUrl = a.inboundUrl, accountId = a.id) }
        override suspend fun setCrmCursor(cursor: Long, at: Long) { state.value = state.value.copy(crmCursor = cursor, lastCrmSync = at) }
    }

    private fun http(method: String, url: String, body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("user-agent", "Mozilla/5.0 (Linux; Android 15) Mobile Safari")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body.encodeToByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().decodeToString() }.orEmpty()
        return code to text
    }

    @Test fun `send, view, update, CRM in, close — against the real server`() = runBlocking {
        assumeTrue("set PAIRSHOT_SERVER to run", !server.isNullOrBlank())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val files = PhotoFiles(context)
        val clock = Clock { System.currentTimeMillis() }
        val repo = JobRepository(db, files, clock)
        val access = LiveAccess(server!!)
        val sender = GallerySender(db, access, FakePhotos(), FakeSettings(), clock)
        fun shot() = Ids.new().let { CapturedPhoto(it, files.newFile(it).apply { writeText("x") }, 3, 4, 1f, 0, 1) }

        // A job with three finished pairs.
        val jobId = repo.createJob("Dana Ruiz", "12 Oak St, Springfield", "House wash", clientPhone = "555 0199")
        val pairs = List(3) { repo.addBefore(jobId, null, shot()).also { p -> repo.setAfter(p, shot()) } }

        // 1. Send: every photo goes up once, and the page is live.
        val g = sender.send(jobId)
        assertThat(access.api.uploads).isEqualTo(6)
        val (code, page) = http("GET", g.url!!)
        assertThat(code).isEqualTo(200)
        assertThat(page).contains("Shine Bros")
        assertThat(page).doesNotContain("Dana")

        // 2. The client opened it: the phone sees that.
        sender.refresh()
        assertThat(db.galleries().forJob(jobId)!!.viewCount).isAtLeast(1)

        // 3. Update: hide a pair. Same link, nothing uploaded again.
        repo.setHidden(pairs[0], true)
        val updated = sender.send(jobId)
        assertThat(updated.url).isEqualTo(g.url)
        assertThat(access.api.uploads).isEqualTo(6)

        // 4. CRM: free accounts are refused; Pro jobs land on the phone.
        val inbound = access.state.value.inboundUrl!!
        assertThat(http("POST", inbound, """{"id":"J-77","client_name":"Sam Lee"}""").first).isEqualTo(402)
        access.api.setDevPlan("pro")
        access.saveAccount(access.api.me())
        val crmBody = """{"id":9876,"title":"Driveway","client":{"first_name":"Sam","last_name":"Lee","phone":"555 1"},"property":{"address1":"4 Bay Rd","city":"Tampa"}}"""
        assertThat(http("POST", inbound, crmBody).first).isEqualTo(200)
        val r = CrmSync(db, access, clock).pull()!!
        assertThat(r.added).isEqualTo(1)
        val crmJob = db.jobs().byExternalId("9876")!!
        assertThat(crmJob.clientName).isEqualTo("Sam Lee")
        assertThat(crmJob.address).isEqualTo("4 Bay Rd, Tampa")
        assertThat(crmJob.service).isEqualTo("Driveway")

        // 5. Pro: webhooks and API keys can be made; the API finds the gallery by CRM job.
        val key = access.api.createApiKey().key
        val c = URL("$server/api/v1/galleries").openConnection() as HttpURLConnection
        c.setRequestProperty("x-api-key", key)
        assertThat(c.responseCode).isEqualTo(200)
        assertThat(c.inputStream.readBytes().decodeToString()).contains(g.url!!)

        // 6. Close: the client sees a polite closed page.
        sender.close(jobId)
        assertThat(http("GET", g.url!!).first).isEqualTo(410)
        db.close()
    }
}
