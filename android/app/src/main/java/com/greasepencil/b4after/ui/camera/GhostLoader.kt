package com.greasepencil.b4after.ui.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.greasepencil.b4after.brand.GreasePencil
import com.greasepencil.b4after.data.repo.PhotoFiles
import com.greasepencil.b4after.domain.EdgeOutline
import com.greasepencil.b4after.ui.components.Bitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds the red-outline version of a before photo, in the camera's own frame. */
object GhostLoader {
    suspend fun outline(context: Context, fileName: String): ImageBitmap? = withContext(Dispatchers.Default) {
        val src = Bitmaps.decode(PhotoFiles.file(context, fileName), maxDim = 640) ?: return@withContext null
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        src.recycle()
        val edges = EdgeOutline.outline(pixels, w, h, GreasePencil.RedOnDark.toArgb())
        Bitmap.createBitmap(edges, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
