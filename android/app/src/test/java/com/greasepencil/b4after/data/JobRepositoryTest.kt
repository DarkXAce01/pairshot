package com.greasepencil.b4after.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.greasepencil.b4after.data.db.AppDatabase
import com.greasepencil.b4after.data.repo.CapturedPhoto
import com.greasepencil.b4after.data.repo.Clock
import com.greasepencil.b4after.data.repo.JobRepository
import com.greasepencil.b4after.data.repo.PhotoFiles
import com.greasepencil.b4after.domain.Ids
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var files: PhotoFiles
    private lateinit var repo: JobRepository
    private var time = 1_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        files = PhotoFiles(context)
        files.dir.listFiles()?.forEach { it.delete() }
        repo = JobRepository(db, files, Clock { time++ })
    }

    @After fun tearDown() = db.close()

    private fun shot(zoom: Float = 1f): CapturedPhoto {
        val id = Ids.new()
        val f = files.newFile(id).apply { writeText("jpeg") }
        return CapturedPhoto(id, f, 3, 4, zoom, 0, time)
    }

    @Test fun `befores become pairs waiting for afters, in shooting order`() = runTest {
        val job = repo.createJob("Dana Ruiz", "12 Oak St", "House wash")
        val drive = repo.addArea(job, "Driveway")
        val p1 = repo.addBefore(job, drive.id, shot(zoom = 2f))
        val p2 = repo.addBefore(job, drive.id, shot())

        val pairs = repo.observePairs(job).first()
        assertThat(pairs.map { it.pair.id }).containsExactly(p1, p2).inOrder()
        assertThat(pairs.all { it.needsAfter }).isTrue()
        assertThat(pairs.first().beforeZoom).isEqualTo(2f)

        val summary = repo.observeSummaries().first().single()
        assertThat(summary.pairCount).isEqualTo(2)
        assertThat(summary.doneCount).isEqualTo(0)
    }

    @Test fun `adding an area twice reuses it`() = runTest {
        val job = repo.createJob("A", null, null)
        val a = repo.addArea(job, "Patio")
        val b = repo.addArea(job, " patio ")
        assertThat(b.id).isEqualTo(a.id)
        assertThat(repo.observeAreas(job).first()).hasSize(1)
    }

    @Test fun `setting an after completes the pair, and a retake deletes the old file`() = runTest {
        val job = repo.createJob("A", null, null)
        val area = repo.addArea(job, "Roof")
        val pair = repo.addBefore(job, area.id, shot())
        val first = shot()
        repo.setAfter(pair, first)
        assertThat(repo.observePair(pair).first()!!.afterFile).isEqualTo(first.file.name)

        val retake = shot()
        repo.setAfter(pair, retake)
        assertThat(repo.observePair(pair).first()!!.afterFile).isEqualTo(retake.file.name)
        assertThat(first.file.exists()).isFalse()
        assertThat(retake.file.exists()).isTrue()
        assertThat(repo.observeSummaries().first().single().doneCount).isEqualTo(1)
    }

    @Test fun `clearing an after puts the pair back in the queue and removes the file`() = runTest {
        val job = repo.createJob("A", null, null)
        val area = repo.addArea(job, "Deck")
        val pair = repo.addBefore(job, area.id, shot())
        val after = shot()
        repo.setAfter(pair, after)
        repo.clearAfter(pair)
        assertThat(repo.observePair(pair).first()!!.needsAfter).isTrue()
        assertThat(after.file.exists()).isFalse()
    }

    @Test fun `swap exchanges before and after`() = runTest {
        val job = repo.createJob("A", null, null)
        val area = repo.addArea(job, "Deck")
        val before = shot(); val after = shot()
        val pair = repo.addBefore(job, area.id, before)
        repo.setAfter(pair, after)
        repo.swap(pair)
        val row = repo.observePair(pair).first()!!
        assertThat(row.beforeFile).isEqualTo(after.file.name)
        assertThat(row.afterFile).isEqualTo(before.file.name)
    }

    @Test fun `only one cover per job, and hiding a cover un-covers it`() = runTest {
        val job = repo.createJob("A", null, null)
        val area = repo.addArea(job, "Deck")
        val p1 = repo.addBefore(job, area.id, shot())
        val p2 = repo.addBefore(job, area.id, shot())
        repo.setCover(p1)
        repo.setCover(p2)
        val rows = repo.observePairs(job).first()
        assertThat(rows.filter { it.pair.isCover }.map { it.pair.id }).containsExactly(p2)

        repo.setHidden(p2, true)
        val hidden = repo.observePair(p2).first()!!
        assertThat(hidden.pair.hidden).isTrue()
        assertThat(hidden.pair.isCover).isFalse()
    }

    @Test fun `deleting a pair deletes both files`() = runTest {
        val job = repo.createJob("A", null, null)
        val area = repo.addArea(job, "Deck")
        val before = shot(); val after = shot()
        val pair = repo.addBefore(job, area.id, before)
        repo.setAfter(pair, after)
        repo.deletePair(pair)
        assertThat(before.file.exists()).isFalse()
        assertThat(after.file.exists()).isFalse()
        assertThat(repo.observePairs(job).first()).isEmpty()
    }

    @Test fun `deleting an area deletes its pairs and files, not other areas`() = runTest {
        val job = repo.createJob("A", null, null)
        val keep = repo.addArea(job, "Keep")
        val drop = repo.addArea(job, "Drop")
        val kept = shot(); val dropped = shot()
        repo.addBefore(job, keep.id, kept)
        repo.addBefore(job, drop.id, dropped)
        repo.deleteArea(job, drop.id)
        assertThat(repo.observePairs(job).first().map { it.areaName }).containsExactly("Keep")
        assertThat(kept.file.exists()).isTrue()
        assertThat(dropped.file.exists()).isFalse()
    }

    @Test fun `deleting a job deletes everything on disk`() = runTest {
        val job = repo.createJob("A", null, null)
        val area = repo.addArea(job, "Deck")
        val before = shot(); val after = shot()
        val pair = repo.addBefore(job, area.id, before)
        repo.setAfter(pair, after)
        repo.deleteJob(job)
        assertThat(repo.observeSummaries().first()).isEmpty()
        assertThat(files.dir.listFiles()!!.toList()).isEmpty()
    }

    @Test fun `blank optional fields are stored as null`() = runTest {
        val id = repo.createJob("  Dana  ", "  ", "", clientPhone = " ")
        val job = repo.observeJob(id).first()!!
        assertThat(job.clientName).isEqualTo("Dana")
        assertThat(job.address).isNull()
        assertThat(job.service).isNull()
        assertThat(job.clientPhone).isNull()
    }

    @Test fun `cover photo on home prefers the chosen cover and its after`() = runTest {
        val job = repo.createJob("A", null, null)
        val area = repo.addArea(job, "Deck")
        repo.addBefore(job, area.id, shot())
        val coverBefore = shot(); val coverAfter = shot()
        val p2 = repo.addBefore(job, area.id, coverBefore)
        repo.setAfter(p2, coverAfter)
        repo.setCover(p2)
        assertThat(repo.observeSummaries().first().single().coverFile).isEqualTo(coverAfter.file.name)
    }
}
