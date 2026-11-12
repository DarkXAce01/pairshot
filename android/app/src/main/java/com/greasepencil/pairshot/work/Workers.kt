package com.greasepencil.pairshot.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.greasepencil.pairshot.data.cloud.CloudException
import com.greasepencil.pairshot.data.cloud.CrmSync
import com.greasepencil.pairshot.data.cloud.GallerySender
import com.greasepencil.pairshot.data.cloud.OfflineException
import com.greasepencil.pairshot.data.db.AppDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkEntryPoint {
    fun sender(): GallerySender
    fun crm(): CrmSync
    fun db(): AppDatabase
}

private fun Context.deps() = EntryPointAccessors.fromApplication(applicationContext, WorkEntryPoint::class.java)

private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** Sends (or updates) one job's link. Waits for a connection, and keeps trying if the server is busy. */
class SendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB) ?: return Result.failure()
        val deps = applicationContext.deps()
        val wasSent = deps.db().galleries().forJob(jobId)?.url != null
        return try {
            val g = deps.sender().send(jobId)
            val job = deps.db().jobs().get(jobId)
            if (!AppVisibility.foreground && job != null && g.url != null) {
                Notifier.linkReady(applicationContext, jobId, job.clientName, updated = wasSent)
            }
            Result.success()
        } catch (e: OfflineException) {
            Result.retry()
        } catch (e: CloudException) {
            if (e.retryable && runAttemptCount < 8) Result.retry()
            else {
                if (!AppVisibility.foreground) Notifier.sendFailed(applicationContext, jobId, e.message)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_JOB = "jobId"
        fun enqueue(context: Context, jobId: String) {
            val req = OneTimeWorkRequestBuilder<SendWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_JOB to jobId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("send-$jobId", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

/** Pulls new CRM jobs now and then (Pro), so they're waiting when the crew opens the app. */
class CrmSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = applicationContext.deps()
        return try {
            deps.crm().pull()
            deps.sender().refresh()
            Result.success()
        } catch (e: OfflineException) {
            Result.retry()
        } catch (e: CloudException) {
            Result.success() // try again at the next period
        }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CrmSyncWorker>(30, TimeUnit.MINUTES).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("crm-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
