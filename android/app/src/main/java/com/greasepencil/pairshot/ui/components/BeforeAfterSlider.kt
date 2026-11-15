package com.greasepencil.pairshot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import com.greasepencil.pairshot.ui.components.ps.PsTag
import com.greasepencil.pairshot.ui.theme.Ps
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
    val c = Ps.colors
    BoxWithConstraints(
        modifier
            .aspectRatio(displayAspect(beforeRotation))
            .clip(Ps.shape.md)
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
            // The empty half: sunken, with the dashed edge of an after still to come.
            val dash = c.outline
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.sunken)
                    .drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            dash,
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                        )
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    "No after yet",
                    style = Ps.type.bodyS,
                    color = c.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.5f).padding(8.dp),
                )
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
        // The knob: a white disc with ‹ › in it, like the client page's.
        val chevron = c.photoTag
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = handleX - 18.dp)
                .size(36.dp)
                .background(Color.White, CircleShape)
                .drawBehind {
                    val w = 2.dp.toPx()
                    val cx = size.width / 2; val cy = size.height / 2
                    val d = 4.dp.toPx(); val gap = 4.dp.toPx()
                    val left = Path().apply { moveTo(cx - gap, cy - d); lineTo(cx - gap - d, cy); lineTo(cx - gap, cy + d) }
                    val right = Path().apply { moveTo(cx + gap, cy - d); lineTo(cx + gap + d, cy); lineTo(cx + gap, cy + d) }
                    val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(left, chevron, style = stroke)
                    drawPath(right, chevron, style = stroke)
                },
        )
        PsTag("Before", Modifier.align(Alignment.TopStart).padding(Ps.space.xs))
        PsTag("After", Modifier.align(Alignment.TopEnd).padding(Ps.space.xs))
    }
}
