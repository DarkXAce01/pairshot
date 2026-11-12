package com.greasepencil.pairshot.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.greasepencil.pairshot.data.repo.PhotoFiles

/** Turns an image clockwise after decoding, for photos taken with the phone held sideways. */
class RotateTransformation(private val degrees: Int) : Transformation() {
    override val cacheKey: String = "rotate-$degrees"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (degrees % 360 == 0) return input
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, m, true)
    }
}

/**
 * A stored photo. [rotation] is the extra clockwise turn for upright display; pass 0 to show the
 * photo in the camera's own frame (what the ghost needs).
 */
@Composable
fun PhotoImage(
    fileName: String,
    rotation: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    alpha: Float = 1f,
) {
    val context = LocalContext.current
    val request = remember(fileName, rotation) {
        ImageRequest.Builder(context)
            .data(PhotoFiles.file(context, fileName))
            .apply { if (rotation % 360 != 0) transformations(RotateTransformation(rotation)) }
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
    )
}

/** Width / height of a stored photo as shown upright. Camera photos are 3:4 in the phone's frame. */
fun displayAspect(rotation: Int): Float = if (rotation % 180 == 0) 3f / 4f else 4f / 3f

