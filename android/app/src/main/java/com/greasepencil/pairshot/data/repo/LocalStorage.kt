package com.greasepencil.pairshot.data.repo

import android.os.StatFs
import com.greasepencil.pairshot.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** How much room the photos take, and clearing out old sent jobs to get it back. */
@Singleton
class LocalStorage @Inject constructor(
    private val files: PhotoFiles,
    private val db: AppDatabase,
    private val repo: JobRepository,
    private val clock: Clock,
) {
    data class Usage(val photoBytes: Long, val photoCount: Int, val freeBytes: Long)
    data class Cleanup(val jobIds: List<String>, val bytes: Long)

    suspend fun usage(): Usage = withContext(Dispatchers.IO) {
        val all = files.dir.listFiles().orEmpty()
        Usage(all.sumOf { it.length() }, all.size, freeBytes())
    }

    fun freeBytes(): Long = runCatching { StatFs(files.dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)

    /** Jobs whose link was sent more than [days] days ago, and how much deleting them frees. */
    suspend fun sentOlderThan(days: Int): Cleanup = withContext(Dispatchers.IO) {
        val ids = db.galleries().jobsSentBefore(clock.now() - days * 86_400_000L)
        val bytes = ids.sumOf { id -> db.photos().forJob(id).sumOf { files.file(it.fileName).length() } }
        Cleanup(ids, bytes)
    }

    /** Deletes those jobs and their photos from this phone. Sent links stay online. */
    suspend fun delete(c: Cleanup) = c.jobIds.forEach { repo.deleteJob(it) }

    companion object {
        /** Below this, the home screen warns that the phone is nearly full. */
        const val LOW_BYTES = 1_000_000_000L
    }
}
