package com.greasepencil.pairshot.data.cloud

import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.data.db.GalleryEntity
import com.greasepencil.pairshot.data.db.JobEntity
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.data.db.SendState
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.data.prefs.SettingsSource
import com.greasepencil.pairshot.data.repo.Clock
import com.greasepencil.pairshot.domain.Ids
import com.greasepencil.pairshot.domain.gallery.BusinessInfo
import com.greasepencil.pairshot.domain.gallery.GalleryBuilder
import com.greasepencil.pairshot.domain.gallery.GalleryManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a job's before & afters as a client link, and keeps that link up to date.
 *
 * Send = prepare each photo (upright, resized, EXIF gone) → hash → ask the server which it lacks →
 * upload only those → publish. An update re-uses every photo already uploaded, so a reorder or a
 * hidden pair uploads nothing.
 */
@Singleton
class GallerySender @Inject constructor(
    private val db: AppDatabase,
    private val cloud: CloudAccess,
    private val photos: PhotoSource,
    private val settings: SettingsSource,
    private val clock: Clock,
) {
    private val galleries get() = db.galleries()

    fun observe(jobId: String): Flow<GalleryEntity?> = galleries.observeForJob(jobId)

    /**
     * Marks the job's link to be sent (first time) or updated. A closed or expired link is replaced by
     * a new one. The actual work happens in [send], normally from SendWorker so it survives no signal.
     */
    suspend fun queue(jobId: String, shareWhenReady: Boolean): GalleryEntity {
        val old = galleries.forJob(jobId)
        val closed = old?.remoteState == "revoked" || old?.remoteState == "expired"
        val g = if (old == null || closed) {
            if (old != null) galleries.delete(old.id)
            GalleryEntity(id = Ids.new(), jobId = jobId, sendState = SendState.QUEUED, shareWhenReady = shareWhenReady, updatedAt = clock.now())
        } else {
            old.copy(sendState = SendState.QUEUED, error = null, shareWhenReady = shareWhenReady || old.shareWhenReady, updatedAt = clock.now())
        }
        galleries.upsert(g)
        return g
    }

    /** Does the send. Throws [OfflineException] / retryable [CloudException] when it should be tried again later. */
    suspend fun send(jobId: String): GalleryEntity {
        val queued = galleries.forJob(jobId) ?: queue(jobId, shareWhenReady = false)
        galleries.upsert(queued.copy(sendState = SendState.SENDING, updatedAt = clock.now()))
        try {
            val job = db.jobs().get(jobId) ?: throw CloudException(0, "no_job", "This job was deleted.")
            val pairs = db.pairs().observeForJob(jobId).first()
            val s = settings.settings.first()
            val pro = cloud.state.first().isPro

            // Prepare and hash every photo the page shows.
            val keyed = build(job, pairs, s, pro) { file, rot -> "$file|$rot" }
            val keys = keyed.photoSrcs()
            if (keys.isEmpty()) throw CloudException(0, "nothing_to_send", "Finish at least one before & after first.")
            val hashOf = HashMap<String, String>()
            for (key in keys) {
                val bytes = readPhoto(key)
                hashOf[key] = sha256Hex(bytes)
            }
            val manifest = build(job, pairs, s, pro) { file, rot -> hashOf.getValue("$file|$rot") }
            val req = SendRequest(
                jobId = jobId,
                externalId = job.externalId,
                job = SendJob(job.clientName, job.clientPhone, job.clientEmail, job.address, job.service),
                manifest = GalleryManifest.json.encodeToJsonElement(GalleryManifest.serializer(), manifest),
            )

            val missing = cloud.call { it.prepare(queued.id, req) }.missing.toSet()
            val keyOf = hashOf.entries.associate { (k, h) -> h to k }
            for (hash in missing) {
                val key = keyOf[hash] ?: continue
                cloud.call { it.uploadPhoto(hash, readPhoto(key)) }
            }
            val dto = cloud.call { it.publish(queued.id, req) }

            val done = (galleries.get(queued.id) ?: queued).copy(
                sendState = SendState.SENT,
                error = null,
                url = dto.url,
                remoteState = dto.state,
                publishedAt = dto.publishedAt,
                expiresAt = dto.expiresAt,
                firstViewedAt = dto.firstViewedAt,
                viewCount = dto.viewCount,
                sentFingerprint = fingerprint(job, pairs, s, pro),
                updatedAt = clock.now(),
            )
            galleries.upsert(done)
            return done
        } catch (e: Exception) {
            val retry = e is OfflineException || (e is CloudException && e.retryable)
            val current = galleries.get(queued.id) ?: queued
            galleries.upsert(
                current.copy(
                    sendState = if (retry) SendState.QUEUED else SendState.FAILED,
                    error = e.message ?: "Something went wrong.",
                    remoteState = when ((e as? CloudException)?.code) {
                        "closed" -> "revoked"
                        "expired" -> "expired"
                        else -> current.remoteState
                    },
                    updatedAt = clock.now(),
                ),
            )
            throw e
        }
    }

    private suspend fun readPhoto(key: String): ByteArray {
        val file = key.substringBeforeLast('|')
        val rot = key.substringAfterLast('|').toInt()
        return photos.prepared(file, rot) ?: throw CloudException(0, "photo_unreadable", "A photo on this job couldn't be read. Retake or hide it.")
    }

    suspend fun clearShareFlag(galleryId: String) {
        galleries.get(galleryId)?.let { galleries.upsert(it.copy(shareWhenReady = false)) }
    }

    /** Asks the server for views and expiry of every link. Quietly does nothing when offline or signed out. */
    suspend fun refresh() {
        val s = cloud.state.first()
        if (!s.signedIn || s.serverUrl.isBlank()) return
        val remote = runCatching { cloud.call { it.galleries() } }.getOrNull() ?: return
        val byId = remote.associateBy { it.id }
        for (g in galleries.all()) {
            val r = byId[g.id] ?: continue
            galleries.upsert(
                g.copy(
                    url = r.url, remoteState = r.state, publishedAt = r.publishedAt, expiresAt = r.expiresAt,
                    firstViewedAt = r.firstViewedAt, viewCount = r.viewCount,
                ),
            )
        }
    }

    /** Closes the link: the client sees a polite "closed" page and the photos are deleted from the server. */
    suspend fun close(jobId: String) {
        val g = galleries.forJob(jobId) ?: return
        if (g.url != null) cloud.call { it.closeGallery(g.id) }
        galleries.upsert(g.copy(remoteState = "revoked", sendState = SendState.SENT, error = null, updatedAt = clock.now()))
    }

    /** Has the page changed since it was sent? Cheap: no photo is read. */
    suspend fun fingerprintNow(jobId: String): String? {
        val job = db.jobs().get(jobId) ?: return null
        return fingerprint(job, db.pairs().observeForJob(jobId).first(), settings.settings.first(), cloud.state.first().isPro)
    }

    companion object {
        fun build(job: JobEntity, pairs: List<PairRow>, s: AppSettings, pro: Boolean, src: (String, Int) -> String) =
            GalleryBuilder.build(job, pairs, BusinessInfo(s.businessName, s.businessPhone, s.reviewUrl, s.bookingUrl, pro), imageUrl = src)

        fun fingerprint(job: JobEntity, pairs: List<PairRow>, s: AppSettings, pro: Boolean): String =
            sha256Hex(build(job, pairs, s, pro) { file, rot -> "$file|$rot" }.toJson().encodeToByteArray())
    }
}

fun GalleryManifest.photoSrcs(): List<String> =
    (listOfNotNull(cover) + sections.flatMap { it.pairs }).flatMap { listOf(it.before.src, it.after.src) }.distinct()
