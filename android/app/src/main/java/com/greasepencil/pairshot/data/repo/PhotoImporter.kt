package com.greasepencil.pairshot.data.repo

import android.content.Context
import android.net.Uri
import com.greasepencil.pairshot.domain.Ids
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings photos in from the system Photo Picker (taken with the stock camera, sent by a crew mate).
 * The picker hands us one-off read access, so no storage permission is ever needed.
 */
@Singleton
class PhotoImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: PhotoFiles,
    private val repo: JobRepository,
    private val clock: Clock,
) {
    /** Each picked photo becomes a new unlabelled before, in the order picked. Returns how many worked. */
    suspend fun importBefores(jobId: String, uris: List<Uri>, areaId: String? = null): Int {
        var ok = 0
        uris.forEach { uri -> copy(uri)?.let { repo.addBefore(jobId, areaId, it); ok++ } }
        return ok
    }

    suspend fun importAfter(pairId: String, uri: Uri): Boolean {
        val shot = copy(uri) ?: return false
        repo.setAfter(pairId, shot)
        return true
    }

    private suspend fun copy(uri: Uri): CapturedPhoto? = withContext(Dispatchers.IO) {
        val id = Ids.new()
        val file = files.newFile(id)
        runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            val (w, h) = files.displaySize(file)
            require(w > 0 && h > 0) { "not an image" }
            // EXIF already turns it upright, so no extra rotation.
            CapturedPhoto(id, file, w, h, zoomRatio = 1f, uprightRotation = 0, takenAt = clock.now())
        }.getOrElse {
            file.delete()
            null
        }
    }
}
