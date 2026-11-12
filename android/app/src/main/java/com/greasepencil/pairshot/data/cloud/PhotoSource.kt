package com.greasepencil.pairshot.data.cloud

import android.content.Context
import com.greasepencil.pairshot.ui.components.PhotoPrep
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** The bytes of a photo as it leaves the phone: upright, resized, no EXIF. */
interface PhotoSource {
    suspend fun prepared(fileName: String, rotation: Int): ByteArray?
}

@Singleton
class PreparedPhotos @Inject constructor(@ApplicationContext private val context: Context) : PhotoSource {
    override suspend fun prepared(fileName: String, rotation: Int): ByteArray? = withContext(Dispatchers.IO) {
        // The server takes up to 1 MB a photo. 1600px is almost always well under; busy scenes get 1280px.
        for (size in intArrayOf(1600, 1280, 1024)) {
            val f = PhotoPrep.display(context, fileName, rotation, size) ?: return@withContext null
            if (f.length() <= MAX_BYTES) return@withContext f.readBytes()
        }
        null
    }

    companion object { const val MAX_BYTES = 1_000_000L }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
