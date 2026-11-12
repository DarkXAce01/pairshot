package com.greasepencil.pairshot.data.cloud

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.data.db.JobSource
import com.greasepencil.pairshot.data.repo.CapturedPhoto
import com.greasepencil.pairshot.data.repo.Clock
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.data.repo.PhotoFiles
import com.greasepencil.pairshot.domain.Ids
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrmSyncTest {
    private lateinit var db: AppDatabase
    private lateinit var api: FakeCloudApi
    private lateinit var access: FakeAccess
    private lateinit var crm: CrmSync
    private lateinit var repo: JobRepository
    private lateinit var files: PhotoFiles
    private var time = 1_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        api = FakeCloudApi()
        access = FakeAccess(api, plan = "pro")
        val clock = Clock { time++ }
        crm = CrmSync(db, access, clock)
        files = PhotoFiles(context)
        repo = JobRepository(db, files, clock)
    }

    @After fun tearDown() = db.close()

    private fun page(cursor: Long, vararg jobs: CrmJobDto, more: Boolean = false) = CrmPullResponse(jobs.toList(), cursor, more)

    @Test fun `free accounts don't sync`() = runTest {
        access.state.value = access.state.value.copy(plan = "free")
        assertThat(crm.pull()).isNull()
        assertThat(api.calls).isEqualTo(0)
    }

    @Test fun `not signed in, nothing happens`() = runTest {
        access.state.value = access.state.value.copy(token = null)
        assertThat(crm.pull()).isNull()
    }

    @Test fun `new CRM jobs land on the phone, and the cursor moves on`() = runTest {
        api.crmPages += page(50, CrmJobDto("J1", "Dana Ruiz", "555", null, "12 Oak St", "House wash", 9_000))
        val r = crm.pull()!!
        assertThat(r.added).isEqualTo(1)
        val job = db.jobs().byExternalId("J1")!!
        assertThat(job.source).isEqualTo(JobSource.CRM)
        assertThat(job.clientName).isEqualTo("Dana Ruiz")
        assertThat(job.scheduledAt).isEqualTo(9_000)
        assertThat(access.state.value.crmCursor).isEqualTo(50)

        crm.pull()
        assertThat(api.crmSince).containsExactly(0L, 50L).inOrder()
    }

    @Test fun `an update changes the job, but a blank never wipes what's there`() = runTest {
        api.crmPages += page(1, CrmJobDto("J1", "Dana", "555", null, "12 Oak St", "House wash"))
        api.crmPages += page(2, CrmJobDto("J1", "Dana Ruiz", null, null, null, "Roof clean"))
        crm.pull(); val r = crm.pull()!!
        assertThat(r.updated).isEqualTo(1)
        val job = db.jobs().byExternalId("J1")!!
        assertThat(job.clientName).isEqualTo("Dana Ruiz")
        assertThat(job.service).isEqualTo("Roof clean")
        assertThat(job.clientPhone).isEqualTo("555")
        assertThat(job.address).isEqualTo("12 Oak St")
    }

    @Test fun `the same job twice is not counted as a change`() = runTest {
        val j = CrmJobDto("J1", "Dana", "555")
        api.crmPages += page(1, j); api.crmPages += page(2, j)
        crm.pull()
        assertThat(crm.pull()!!.total).isEqualTo(0)
    }

    @Test fun `a cancelled job goes, unless photos were taken`() = runTest {
        api.crmPages += page(1, CrmJobDto("A", "Ann"), CrmJobDto("B", "Bo"))
        crm.pull()
        val b = db.jobs().byExternalId("B")!!
        val f = files.newFile(Ids.new()).apply { writeText("jpeg") }
        repo.addBefore(b.id, null, CapturedPhoto(f.nameWithoutExtension, f, 3, 4, 1f, 0, 1))

        api.crmPages += page(2, CrmJobDto("A", cancelled = true), CrmJobDto("B", cancelled = true))
        val r = crm.pull()!!
        assertThat(r.removed).isEqualTo(1)
        assertThat(db.jobs().byExternalId("A")).isNull()
        assertThat(db.jobs().byExternalId("B")).isNotNull()
    }

    @Test fun `a cancelled job the phone never saw is ignored`() = runTest {
        api.crmPages += page(1, CrmJobDto("Z", cancelled = true))
        assertThat(crm.pull()!!.total).isEqualTo(0)
        assertThat(db.jobs().byExternalId("Z")).isNull()
    }

    @Test fun `long backlogs are fetched page by page`() = runTest {
        api.crmPages += page(10, CrmJobDto("1", "A"), more = true)
        api.crmPages += page(20, CrmJobDto("2", "B"), more = true)
        api.crmPages += page(30, CrmJobDto("3", "C"))
        assertThat(crm.pull()!!.added).isEqualTo(3)
        assertThat(api.crmSince).containsExactly(0L, 10L, 20L).inOrder()
    }

    @Test fun `a job without a name still arrives`() = runTest {
        api.crmPages += page(1, CrmJobDto("N1", null, "555"))
        crm.pull()
        assertThat(db.jobs().byExternalId("N1")!!.clientName).isEqualTo("Client")
    }
}
