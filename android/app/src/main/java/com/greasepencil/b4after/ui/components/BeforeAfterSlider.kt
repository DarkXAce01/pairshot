package com.greasepencil.b4after.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The before on the left, the after on the right, split by a handle you drag. The same idea as the
 * client page, so what the crew sees on the phone is what the client gets.
 */
@Composable
fun BeforeAfterSlider(
    beforeFile: String,
    beforeRotation: Int,
    afterFile: String?,
    afterRotation: Int,
    modifier: Modifier = Modifier,
) {
    var split by remember(beforeFile, afterFile) { mutableFloatStateOf(0.5f) }
    BoxWithConstraints(
        modifier
            .aspectRatio(displayAspect(beforeRotation))
            .clipToBounds()
            .semantics { contentDescription = "Before and after. Drag to compare." },
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val dragMod = Modifier
            .pointerInput(widthPx) {
                detectDragGestures { change, _ -> split = (change.position.x / widthPx).coerceIn(0f, 1f) }
            }
            .pointerInput(widthPx) {
                detectTapGestures { split = (it.x / widthPx).coerceIn(0f, 1f) }
            }
        if (afterFile != null) {
            PhotoImage(afterFile, afterRotation, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Text("No after yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PhotoImage(
            beforeFile,
            beforeRotation,
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * split) { this@drawWithContent.drawContent() }
                },
        )
        Box(Modifier.fillMaxSize().then(dragMod))
        val handleX = maxWidth * split
        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .offset(x = handleX - 1.dp)
                .background(Color.White),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = handleX - 14.dp)
                .size(28.dp)
                .background(Color.White, CircleShape),
        )
        Label("BEFORE", Modifier.align(Alignment.TopStart))
        Label("AFTER", Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun Label(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .padding(10.dp)
            .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

