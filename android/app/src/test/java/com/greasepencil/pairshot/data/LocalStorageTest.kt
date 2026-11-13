package com.greasepencil.pairshot.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.data.db.GalleryEntity
import com.greasepencil.pairshot.data.db.SendState
import com.greasepencil.pairshot.data.repo.CapturedPhoto
import com.greasepencil.pairshot.data.repo.Clock
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.data.repo.LocalStorage
import com.greasepencil.pairshot.data.repo.PhotoFiles
import com.greasepencil.pairshot.domain.Ids
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStorageTest {
    private val day = 86_400_000L
    private lateinit var db: AppDatabase
    private lateinit var files: PhotoFiles
    private lateinit var repo: JobRepository
    private lateinit var storage: LocalStorage
    private var time = 400 * 86_400_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        files = PhotoFiles(context)
        files.dir.listFiles()?.forEach { it.delete() }
        val clock = Clock { time }
        repo = JobRepository(db, files, clock)
        storage = LocalStorage(files, db, repo, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun jobWithPhoto(name: String, sentDaysAgo: Int?): String {
        val job = repo.createJob(name, null, null)
        val id = Ids.new()
        val f = files.newFile(id).apply { writeBytes(ByteArray(1000)) }
        repo.addBefore(job, null, CapturedPhoto(id, f, 3, 4, 1f, 0, time))
        if (sentDaysAgo != null) {
            db.galleries().upsert(
                GalleryEntity(Ids.new(), job, SendState.SENT, url = "https://x/g/$name", remoteState = "live", publishedAt = time - sentDaysAgo * day, updatedAt = time),
            )
        }
        return job
    }

    @Test fun `only jobs sent long enough ago are offered, and deleting them removes their photos`() = runTest {
        val old = jobWithPhoto("Old", sentDaysAgo = 120)
        jobWithPhoto("Recent", sentDaysAgo = 10)
        jobWithPhoto("Never sent", sentDaysAgo = null)

        assertThat(storage.usage().photoCount).isEqualTo(3)
        val c = storage.sentOlderThan(90)
        assertThat(c.jobIds).containsExactly(old)
        assertThat(c.bytes).isEqualTo(1000L)

        storage.delete(c)
        assertThat(db.jobs().get(old)).isNull()
        assertThat(storage.usage().photoCount).isEqualTo(2)
        assertThat(storage.sentOlderThan(90).jobIds).isEmpty()
        assertThat(storage.sentOlderThan(5).jobIds).hasSize(1)
    }
}
