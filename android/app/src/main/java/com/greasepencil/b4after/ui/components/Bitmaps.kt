package com.greasepencil.b4after.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

object Bitmaps {
    private fun exifDegrees(file: File): Int = runCatching {
        ExifInterface(file).rotationDegrees
    }.getOrDefault(0)

    /**
     * Decodes [file] no bigger than about [maxDim] on its long side, with its EXIF rotation applied and
     * then turned a further [extraRotation] degrees clockwise.
     */
    fun decode(file: File, maxDim: Int, extraRotation: Int = 0): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val raw = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val degrees = (exifDegrees(file) + extraRotation) % 360
        if (degrees == 0) return raw
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }
}
