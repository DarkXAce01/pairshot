package com.greasepencil.pairshot.data.repo

import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Where photo files live: app-private storage, never the shared gallery (no storage permission needed). */
@Singleton
class PhotoFiles @Inject constructor(@ApplicationContext context: Context) {
    val dir: File = dir(context).apply { mkdirs() }

    companion object {
        fun dir(context: Context): File = File(context.filesDir, "photos")
        fun file(context: Context, fileName: String): File = File(dir(context), fileName)
    }

    fun file(fileName: String): File = File(dir, fileName)

    fun newFile(id: String): File = File(dir, "$id.jpg")

    fun delete(fileName: String) {
        file(fileName).delete()
    }

    /** Width and height as the pixels will be shown (EXIF orientation applied). */
    fun displaySize(file: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val swapped = orientation in setOf(
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
        )
        return if (swapped) opts.outHeight to opts.outWidth else opts.outWidth to opts.outHeight
    }
}
