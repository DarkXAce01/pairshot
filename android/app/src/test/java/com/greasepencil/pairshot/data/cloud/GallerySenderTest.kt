package com.greasepencil.pairshot.data.cloud

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.data.db.SendState
import com.greasepencil.pairshot.data.repo.CapturedPhoto
import com.greasepencil.pairshot.data.repo.Clock
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.data.repo.PhotoFiles
import com.greasepencil.pairshot.domain.Ids
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GallerySenderTest {
    private lateinit var db: AppDatabase
    private lateinit var files: PhotoFiles
    private lateinit var repo: JobRepository
    private lateinit var api: FakeCloudApi
    private lateinit var access: FakeAccess
    private lateinit var photos: FakePhotos
    private lateinit var settings: FakeSettings
    private lateinit var sender: GallerySender
    private var time = 1_000L
    private lateinit var jobId: String
    private val pairIds = mutableListOf<String>()

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        files = PhotoFiles(context)
        val clock = Clock { time++ }
        repo = JobRepository(db, files, clock)
        api = FakeCloudApi()
        access = FakeAccess(api)
        photos = FakePhotos()
        settings = FakeSettings()
        sender = GallerySender(db, access, photos, settings, clock)

        jobId = repo.createJob("Dana Ruiz", "12 Oak St, Springfield", "House wash", clientPhone = "555 0199")
        repeat(2) {
            val p = repo.addBefore(jobId, null, shot())
            repo.setAfter(p, shot())
            pairIds += p
        }
    }

    @After fun tearDown() = db.close()

    private fun shot(): CapturedPhoto {
        val id = Ids.new()
        val f = files.newFile(id).apply { writeText("jpeg") }
        return CapturedPhoto(id, f, 3, 4, 1f, 0, time)
    }

    @Test fun `first send uploads each photo once, publishes, and remembers the link`() = runTest {
        sender.queue(jobId, shareWhenReady = true)
        val g = sender.send(jobId)

        assertThat(api.uploads).hasSize(4)
        assertThat(g.sendState).isEqualTo(SendState.SENT)
        assertThat(g.url).startsWith("https://pairshot.test/g/")
        assertThat(g.remoteState).isEqualTo("live")
        assertThat(g.shareWhenReady).isTrue() // the screen clears it once it has shared
        assertThat(g.sentFingerprint).isNotNull()

        val req = api.published.getValue(g.id)
        assertThat(req.jobId).isEqualTo(jobId)
        assertThat(req.job.clientName).isEqualTo("Dana Ruiz")
        assertThat(req.job.clientPhone).isEqualTo("555 0199")
        // The page gets photo hashes, never file names, and never the client's name.
        val srcs = FakeCloudApi.srcs(req.manifest)
        assertThat(srcs).hasSize(4)
        assertThat(srcs.all { Regex("[0-9a-f]{64}").matches(it) }).isTrue()
        assertThat(req.manifest.toString()).doesNotContain("Dana")
        assertThat(req.manifest.jsonObject["job"]!!.jsonObject["place"]!!.jsonPrimitive.content).isEqualTo("12 Oak St")
    }

    @Test fun `an update keeps the link and uploads only new photos`() = runTest {
        val first = sender.send(jobId)
        api.uploads.clear()

        repo.setHidden(pairIds[0], true)
        val second = sender.send(jobId)
        assertThat(api.uploads).isEmpty()
        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.url).isEqualTo(first.url)

        val p = repo.addBefore(jobId, null, shot())
        repo.setAfter(p, shot())
        sender.send(jobId)
        assertThat(api.uploads).hasSize(2)
    }

    @Test fun `changes since sending are noticed without reading photos`() = runTest {
        val g = sender.send(jobId)
        photos.reads.clear()
        assertThat(sender.fingerprintNow(jobId)).isEqualTo(g.sentFingerprint)
        repo.setHidden(pairIds[1], true)
        assertThat(sender.fingerprintNow(jobId)).isNotEqualTo(g.sentFingerprint)
        settings.settings.value = settings.settings.value.copy(reviewUrl = "g.page/r/x")
        assertThat(photos.reads).isEmpty()
    }

    @Test fun `no signal leaves it queued to go later`() = runTest {
        sender.queue(jobId, shareWhenReady = true)
        api.failAlways = OfflineException()
        assertThrows(OfflineException::class.java) { kotlinx.coroutines.runBlocking { sender.send(jobId) } }
        val g = db.galleries().forJob(jobId)!!
        assertThat(g.sendState).isEqualTo(SendState.QUEUED)
        assertThat(g.shareWhenReady).isTrue()

        api.failAlways = null
        val sent = sender.send(jobId)
        assertThat(sent.id).isEqualTo(g.id) // same gallery: a retry never makes a second link
        assertThat(sent.sendState).isEqualTo(SendState.SENT)
    }

    @Test fun `a refusal is shown in the server's words and not retried`() = runTest {
        api.failNext = CloudException(402, "monthly_limit", "You've sent your 3 free galleries this month.")
        runCatching { sender.send(jobId) }
        val g = db.galleries().forJob(jobId)!!
        assertThat(g.sendState).isEqualTo(SendState.FAILED)
        assertThat(g.error).isEqualTo("You've sent your 3 free galleries this month.")
    }

    @Test fun `a busy server is retried`() = runTest {
        api.failNext = CloudException(503, "http_503", "Server trouble")
        runCatching { sender.send(jobId) }
        assertThat(db.galleries().forJob(jobId)!!.sendState).isEqualTo(SendState.QUEUED)
    }

    @Test fun `an expired session signs in again once`() = runTest {
        api.failNext = CloudException(401, "unauthorised", "Sign in again.")
        val g = sender.send(jobId)
        assertThat(access.signOuts).isEqualTo(1)
        assertThat(g.sendState).isEqualTo(SendState.SENT)
    }

    @Test fun `an unreadable photo stops the send with a clear reason`() = runTest {
        val name = repo.observePairs(jobId).first().first().beforeFile
        photos.unreadable += name
        runCatching { sender.send(jobId) }
        val g = db.galleries().forJob(jobId)!!
        assertThat(g.sendState).isEqualTo(SendState.FAILED)
        assertThat(g.error).contains("couldn't be read")
        assertThat(api.calls).isEqualTo(0)
    }

    @Test fun `nothing finished means nothing to send`() = runTest {
        val empty = repo.createJob("Sam", null, null)
        runCatching { sender.send(empty) }
        assertThat(db.galleries().forJob(empty)!!.error).contains("Finish at least one")
    }

    @Test fun `closing a link, then sending again, makes a new link`() = runTest {
        val first = sender.send(jobId)
        sender.close(jobId)
        assertThat(api.closed).containsExactly(first.id)
        assertThat(db.galleries().forJob(jobId)!!.remoteState).isEqualTo("revoked")

        sender.queue(jobId, shareWhenReady = true)
        val second = sender.send(jobId)
        assertThat(second.id).isNotEqualTo(first.id)
        assertThat(second.remoteState).isEqualTo("live")
    }

    @Test fun `a link the server says was closed is marked closed`() = runTest {
        sender.send(jobId)
        api.failNext = CloudException(410, "closed", "This gallery was closed. Send a new one.")
        runCatching { sender.send(jobId) }
        assertThat(db.galleries().forJob(jobId)!!.remoteState).isEqualTo("revoked")
    }

    @Test fun `refresh brings in views`() = runTest {
        val g = sender.send(jobId)
        api.galleries[g.id] = api.galleries.getValue(g.id).copy(viewCount = 3, firstViewedAt = 5)
        sender.refresh()
        val r = db.galleries().forJob(jobId)!!
        assertThat(r.viewCount).isEqualTo(3)
        assertThat(r.firstViewedAt).isEqualTo(5)
    }

    @Test fun `refresh is quiet when offline or signed out`() = runTest {
        api.failAlways = OfflineException()
        sender.refresh()
        access.state.value = access.state.value.copy(token = null)
        sender.refresh()
    }

    @Test fun `deleting the job deletes its link record`() = runTest {
        sender.send(jobId)
        repo.deleteJob(jobId)
        assertThat(db.galleries().all()).isEmpty()
    }
}
