package com.greasepencil.pairshot.data.repo

import androidx.room.withTransaction
import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.data.db.AreaEntity
import com.greasepencil.pairshot.data.db.JobEntity
import com.greasepencil.pairshot.data.db.JobSummary
import com.greasepencil.pairshot.data.db.PairEntity
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.data.db.PhotoEntity
import com.greasepencil.pairshot.domain.Crop
import com.greasepencil.pairshot.domain.Ids
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A photo that has just been written to disk by the camera (or copied in from the Photo Picker). */
data class CapturedPhoto(
    val id: String,
    val file: File,
    val width: Int,
    val height: Int,
    val zoomRatio: Float,
    val uprightRotation: Int,
    val takenAt: Long,
)

@Singleton
class JobRepository @Inject constructor(
    private val db: AppDatabase,
    private val files: PhotoFiles,
    private val clock: Clock,
) {
    private val jobs get() = db.jobs()
    private val areas get() = db.areas()
    private val photos get() = db.photos()
    private val pairs get() = db.pairs()

    fun observeSummaries(): Flow<List<JobSummary>> = jobs.observeSummaries()
    fun observeJob(id: String): Flow<JobEntity?> = jobs.observe(id)
    fun observeAreas(jobId: String): Flow<List<AreaEntity>> = areas.observeForJob(jobId)
    fun observePairs(jobId: String): Flow<List<PairRow>> = pairs.observeForJob(jobId)
    fun observePair(id: String): Flow<PairRow?> = pairs.observe(id)

    suspend fun createJob(
        clientName: String,
        address: String?,
        service: String?,
        scheduledAt: Long? = null,
        clientPhone: String? = null,
    ): String {
        val now = clock.now()
        val id = Ids.new()
        jobs.insert(
            JobEntity(
                id = id,
                clientName = clientName.trim(),
                address = address?.trim()?.ifEmpty { null },
                service = service?.trim()?.ifEmpty { null },
                scheduledAt = scheduledAt,
                clientPhone = clientPhone?.trim()?.ifEmpty { null },
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    suspend fun updateJob(job: JobEntity) = jobs.update(job.copy(updatedAt = clock.now()))

    /** This job's page style (a ClientTemplate id), or null to use the business default. */
    suspend fun setTemplate(jobId: String, template: String?) = jobs.setTemplate(jobId, template)

    /** A recent finished pair to show in the page-style thumbnails; null before the first one. */
    fun observeLatestFinishedPair(): Flow<PairRow?> = pairs.observeLatestFinished()

    /** Deletes the job, its areas, pairs and photo rows (cascade), then the files on disk. */
    suspend fun deleteJob(id: String) {
        val doomed = photos.forJob(id)
        jobs.delete(id)
        doomed.forEach { files.delete(it.fileName) }
    }

    suspend fun addArea(jobId: String, name: String): AreaEntity {
        val existing = areas.forJob(jobId)
        existing.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { return it }
        val area = AreaEntity(Ids.new(), jobId, name.trim(), (existing.maxOfOrNull { it.position } ?: -1) + 1)
        areas.insert(area)
        jobs.touch(jobId, clock.now())
        return area
    }

    suspend fun renameArea(id: String, name: String) = areas.rename(id, name.trim())

    /** Deletes a label. Its pairs stay, unlabelled. */
    suspend fun deleteArea(areaId: String) {
        areas.delete(areaId)
    }

    /** Gives a pair a label, or removes it with null. */
    suspend fun setPairArea(pairId: String, areaId: String?) {
        val pair = pairs.get(pairId) ?: return
        if (pair.areaId == areaId) return
        pairs.update(pair.copy(areaId = areaId, position = pairs.nextPosition(pair.jobId, areaId)))
        jobs.touch(pair.jobId, clock.now())
    }

    suspend fun usedAreaNames(): List<String> = areas.allNames()

    /** A new before shot: becomes a new pair (labelled [areaId], or unlabelled) waiting for its after. */
    suspend fun addBefore(jobId: String, areaId: String?, shot: CapturedPhoto): String = db.withTransaction {
        photos.insert(shot.toEntity(jobId))
        val pairId = Ids.new()
        pairs.insert(
            PairEntity(
                id = pairId,
                jobId = jobId,
                areaId = areaId,
                beforePhotoId = shot.id,
                position = pairs.nextPosition(jobId, areaId),
                createdAt = clock.now(),
            ),
        )
        jobs.touch(jobId, clock.now())
        pairId
    }

    /** Sets (or replaces) the after of a pair. A replaced after's file is deleted. */
    suspend fun setAfter(pairId: String, shot: CapturedPhoto) {
        val pair = pairs.get(pairId) ?: error("pair $pairId is gone")
        db.withTransaction {
            photos.insert(shot.toEntity(pair.jobId))
            pairs.update(pair.copy(afterPhotoId = shot.id))
            jobs.touch(pair.jobId, clock.now())
        }
        cleanOrphans(pair.jobId)
    }

    /** Removes a pair's after so it goes back in the After queue. */
    suspend fun clearAfter(pairId: String) {
        val pair = pairs.get(pairId) ?: return
        pairs.update(pair.copy(afterPhotoId = null))
        cleanOrphans(pair.jobId)
    }

    suspend fun swap(pairId: String) {
        val pair = pairs.get(pairId) ?: return
        val after = pair.afterPhotoId ?: return
        pairs.update(pair.copy(beforePhotoId = after, afterPhotoId = pair.beforePhotoId))
    }

    /** Moves a pair [delta] places within its label (−1 earlier, +1 later). Returns false at either end. */
    suspend fun movePair(pairId: String, delta: Int): Boolean = db.withTransaction {
        val pair = pairs.get(pairId) ?: return@withTransaction false
        val list = pairs.inArea(pair.jobId, pair.areaId).toMutableList()
        val from = list.indexOfFirst { it.id == pairId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@withTransaction false
        list.add(to, list.removeAt(from))
        list.forEachIndexed { i, p -> if (p.position != i) pairs.update(p.copy(position = i)) }
        jobs.touch(pair.jobId, clock.now())
        true
    }

    /**
     * Re-pairs: the after of [pairId] goes with [otherPairId]'s before and the other way round, for afters
     * shot against the wrong before. Either pair may still be waiting for its after.
     */
    suspend fun swapAfters(pairId: String, otherPairId: String) = db.withTransaction {
        val a = pairs.get(pairId) ?: return@withTransaction
        val b = pairs.get(otherPairId) ?: return@withTransaction
        if (a.jobId != b.jobId || a.id == b.id) return@withTransaction
        pairs.update(a.copy(afterPhotoId = b.afterPhotoId))
        pairs.update(b.copy(afterPhotoId = a.afterPhotoId))
        jobs.touch(a.jobId, clock.now())
    }

    /** Sets a pair's crop (fractions of the upright photo). A crop smaller than 10 % either way is refused. */
    suspend fun setCrop(pairId: String, crop: Crop) {
        val pair = pairs.get(pairId) ?: return
        val c = crop.clamped()
        pairs.update(pair.copy(cropLeft = c.left, cropTop = c.top, cropRight = c.right, cropBottom = c.bottom))
        jobs.touch(pair.jobId, clock.now())
    }

    suspend fun setHidden(pairId: String, hidden: Boolean) {
        val pair = pairs.get(pairId) ?: return
        pairs.update(pair.copy(hidden = hidden, isCover = pair.isCover && !hidden))
    }

    suspend fun setCover(pairId: String) {
        val pair = pairs.get(pairId) ?: return
        pairs.setCover(pair.jobId, pairId)
        if (pair.hidden) pairs.update(pair.copy(hidden = false, isCover = true))
    }

    suspend fun deletePair(pairId: String) {
        val pair = pairs.get(pairId) ?: return
        pairs.delete(pairId)
        cleanOrphans(pair.jobId)
    }

    /** Throws away a shot that never made it into a pair (e.g. the save failed half way). */
    fun discardFile(file: File) {
        file.delete()
    }

    private suspend fun cleanOrphans(jobId: String) {
        photos.orphans(jobId).forEach {
            photos.delete(it.id)
            files.delete(it.fileName)
        }
    }

    private fun CapturedPhoto.toEntity(jobId: String) = PhotoEntity(
        id = id,
        jobId = jobId,
        fileName = file.name,
        width = width,
        height = height,
        zoomRatio = zoomRatio,
        uprightRotation = uprightRotation,
        takenAt = takenAt,
    )
}

fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
