package com.greasepencil.pairshot.data.cloud

import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.data.db.JobEntity
import com.greasepencil.pairshot.data.db.JobSource
import com.greasepencil.pairshot.data.repo.Clock
import com.greasepencil.pairshot.domain.Ids
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Brings jobs from the business's CRM (via the server's inbound address) onto the phone. Pro only. */
@Singleton
class CrmSync @Inject constructor(
    private val db: AppDatabase,
    private val cloud: CloudAccess,
    private val clock: Clock,
) {
    data class Result(val added: Int = 0, val updated: Int = 0, val removed: Int = 0) {
        val total get() = added + updated + removed
    }

    /** null when there is nothing to sync with (not signed in, or not Pro). */
    suspend fun pull(): Result? {
        val s = cloud.state.first()
        if (!s.signedIn || !s.isPro || s.serverUrl.isBlank()) return null
        var cursor = s.crmCursor
        var result = Result()
        var pages = 0
        do {
            val page = cloud.call { it.crmJobs(cursor) }
            for (j in page.jobs) result = apply(j, result)
            cursor = page.cursor
            cloud.setCrmCursor(cursor, clock.now())
        } while (page.more && ++pages < 20)
        if (pages == 0) cloud.setCrmCursor(cursor, clock.now())
        return result
    }

    internal suspend fun apply(j: CrmJobDto, r: Result): Result {
        val jobs = db.jobs()
        val existing = jobs.byExternalId(j.externalId)
        val now = clock.now()
        if (j.cancelled) {
            // A cancelled job goes, unless photos were already taken: those are never deleted for you.
            if (existing != null && jobs.pairCount(existing.id) == 0) {
                jobs.delete(existing.id)
                return r.copy(removed = r.removed + 1)
            }
            return r
        }
        if (existing == null) {
            jobs.insert(
                JobEntity(
                    id = Ids.new(),
                    externalId = j.externalId,
                    source = JobSource.CRM,
                    clientName = j.clientName ?: "Client",
                    clientPhone = j.clientPhone,
                    clientEmail = j.clientEmail,
                    address = j.address,
                    service = j.service,
                    scheduledAt = j.scheduledAt,
                    notes = j.notes,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            return r.copy(added = r.added + 1)
        }
        // The CRM is the source of truth for client details, but a blank from it never wipes what's here.
        val merged = existing.copy(
            clientName = j.clientName ?: existing.clientName,
            clientPhone = j.clientPhone ?: existing.clientPhone,
            clientEmail = j.clientEmail ?: existing.clientEmail,
            address = j.address ?: existing.address,
            service = j.service ?: existing.service,
            scheduledAt = j.scheduledAt ?: existing.scheduledAt,
            notes = j.notes ?: existing.notes,
        )
        if (merged == existing) return r
        jobs.update(merged.copy(updatedAt = now))
        return r.copy(updated = r.updated + 1)
    }
}
