package com.greasepencil.pairshot.ui.components

import android.content.Context
import android.graphics.Bitmap
import com.greasepencil.pairshot.data.repo.PhotoFiles
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Makes the version of a photo that leaves the phone: upright, at most [maxDim] on the long side, and
 * re-encoded so no EXIF (GPS, phone model) survives. Cached, since the same photo is asked for often.
 */
object PhotoPrep {
    fun display(context: Context, fileName: String, rotation: Int, maxDim: Int = 1600): File? {
        val dir = File(context.cacheDir, "prep").apply { mkdirs() }
        val out = File(dir, "${fileName.substringBeforeLast('.')}-r$rotation-$maxDim.jpg")
        if (out.exists() && out.length() > 0) return out
        val bmp = Bitmaps.decode(PhotoFiles.file(context, fileName), maxDim, rotation) ?: return null
        val scale = maxDim.toFloat() / max(bmp.width, bmp.height)
        val sized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true)
                .also { if (it !== bmp) bmp.recycle() }
        } else bmp
        val tmp = File(dir, out.name + ".tmp")
        tmp.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        sized.recycle()
        tmp.renameTo(out)
        return out
    }
}
