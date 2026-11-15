package com.greasepencil.pairshot.ui.pair

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greasepencil.pairshot.domain.Crop
import com.greasepencil.pairshot.ui.components.PhotoImage
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.PsCameraColors
import kotlin.math.abs
import kotlin.math.min

/** Which part of the box a drag started on. */
internal enum class Grip { MOVE, TL, TR, BL, BR }

/** A preset shape (width / height of the crop); null is the whole photo. */
private data class Shape(val label: String, val aspect: Float?)
private val shapes = listOf(Shape("Full", null), Shape("Square", 1f), Shape("4:5", 4f / 5f), Shape("16:9", 16f / 9f))

/** The biggest centred crop of [aspect] in a photo of [photoAspect] (both width / height). */
fun centredCrop(aspect: Float?, photoAspect: Float): Crop {
    if (aspect == null) return Crop.FULL
    return if (aspect > photoAspect) {
        val h = photoAspect / aspect
        Crop(0f, (1 - h) / 2, 1f, (1 + h) / 2)
    } else {
        val w = aspect / photoAspect
        Crop((1 - w) / 2, 0f, (1 + w) / 2, 1f)
    }
}

/**
 * Full-screen crop of the before photo; the same crop is used on the after so they still line up.
 * Drag inside the box to move it, or a corner to resize it.
 */
@Composable
fun CropEditor(
    file: String,
    rotation: Int,
    photoAspect: Float,
    initial: Crop,
    onSave: (Crop) -> Unit,
    onDismiss: () -> Unit,
) {
    var crop by remember { mutableStateOf(initial) }
    var shape by remember { mutableStateOf<Float?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(PsCameraColors.background).systemBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Crop", style = Ps.type.titleM, color = PsCameraColors.ink, modifier = Modifier.semantics { heading() })
                Text(
                    "The after gets the same crop, so the pair still lines up.",
                    style = Ps.type.caption,
                    color = PsCameraColors.muted,
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val fitsWidth = maxWidth / maxHeight < photoAspect
                val w = if (fitsWidth) maxWidth else maxHeight * photoAspect
                Box(Modifier.size(w, w / photoAspect)) {
                    PhotoImage(file, rotation, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    CropBox(crop, lockAspect = shape?.let { it / photoAspect }) { crop = it }
                }
            }
            PsChipRow {
                shapes.forEach { s ->
                    PsChip(
                        s.label,
                        selected = shape == s.aspect && (s.aspect != null || crop.isFull),
                        onClick = { shape = s.aspect; crop = centredCrop(s.aspect, photoAspect) },
                        onDark = true,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PsButton("Cancel", onDismiss, Modifier.weight(1f), variant = PsButtonVariant.Secondary, onDark = true)
                PsButton("Save crop", { onSave(crop.clamped()) }, Modifier.weight(1f), onDark = true)
            }
        }
    }
}

/**
 * The draggable box. [lockAspect] keeps width/height (in fractions of the photo) fixed while resizing.
 */
@Composable
private fun CropBox(crop: Crop, lockAspect: Float?, onChange: (Crop) -> Unit) {
    val current by androidx.compose.runtime.rememberUpdatedState(crop)
    val lock by androidx.compose.runtime.rememberUpdatedState(lockAspect)
    val pct = { f: Float -> "${(f * 100).toInt()}%" }
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Crop area"
                stateDescription = "${pct(crop.width)} wide, ${pct(crop.height)} tall"
            }
            .pointerInput(Unit) {
                var grip = Grip.MOVE
                detectDragGestures(
                    onDragStart = { at ->
                        val c = current
                        val corners = mapOf(
                            Grip.TL to Offset(c.left * size.width, c.top * size.height),
                            Grip.TR to Offset(c.right * size.width, c.top * size.height),
                            Grip.BL to Offset(c.left * size.width, c.bottom * size.height),
                            Grip.BR to Offset(c.right * size.width, c.bottom * size.height),
                        )
                        val near = corners.minBy { (_, p) -> (p - at).getDistance() }
                        grip = if ((near.value - at).getDistance() < 48.dp.toPx()) near.key else Grip.MOVE
                    },
                ) { change, drag ->
                    change.consume()
                    onChange(dragCrop(current, grip, drag.x / size.width, drag.y / size.height, lock))
                }
            },
    ) {
        val l = crop.left * size.width; val t = crop.top * size.height
        val r = crop.right * size.width; val b = crop.bottom * size.height
        val shade = Color.Black.copy(alpha = 0.55f)
        drawRect(shade, Offset.Zero, Size(size.width, t))
        drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
        drawRect(shade, Offset(0f, t), Size(l, b - t))
        drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
        // Thirds, to help line things up.
        for (i in 1..2) {
            val x = l + (r - l) * i / 3; val y = t + (b - t) * i / 3
            drawLine(Color.White.copy(alpha = 0.35f), Offset(x, t), Offset(x, b), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.35f), Offset(l, y), Offset(r, y), 1.dp.toPx())
        }
        val k = 8.dp.toPx()
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { drawCircle(Color.White, k, it) }
    }
}

/** One drag step. dx, dy are fractions of the photo. */
internal fun dragCrop(c: Crop, grip: Grip, dx: Float, dy: Float, lockAspect: Float?): Crop {
    if (grip == Grip.MOVE) {
        val mx = dx.coerceIn(-c.left, 1f - c.right)
        val my = dy.coerceIn(-c.top, 1f - c.bottom)
        return Crop(c.left + mx, c.top + my, c.right + mx, c.bottom + my)
    }
    var l = c.left; var t = c.top; var r = c.right; var b = c.bottom
    when (grip) {
        Grip.TL -> { l += dx; t += dy }
        Grip.TR -> { r += dx; t += dy }
        Grip.BL -> { l += dx; b += dy }
        Grip.BR -> { r += dx; b += dy }
        Grip.MOVE -> Unit
    }
    l = l.coerceIn(0f, c.right - Crop.MIN); r = r.coerceIn(c.left + Crop.MIN, 1f)
    t = t.coerceIn(0f, c.bottom - Crop.MIN); b = b.coerceIn(c.top + Crop.MIN, 1f)
    if (lockAspect != null) {
        // Keep the shape: size from the larger change, anchored at the opposite corner, kept inside.
        val w0 = r - l; val h0 = b - t
        val scale = if (abs(w0 - c.width) / c.width > abs(h0 - c.height) / c.height) w0 / c.width else h0 / c.height
        var w = c.width * scale; var h = w / lockAspect
        val maxW = when (grip) { Grip.TL, Grip.BL -> c.right; else -> 1f - c.left }
        val maxH = when (grip) { Grip.TL, Grip.TR -> c.bottom; else -> 1f - c.top }
        val fit = min(1f, min(maxW / w, maxH / h))
        w *= fit; h *= fit
        if (w < Crop.MIN || h < Crop.MIN) return c
        when (grip) {
            Grip.TL -> { l = c.right - w; t = c.bottom - h; r = c.right; b = c.bottom }
            Grip.TR -> { l = c.left; t = c.bottom - h; r = c.left + w; b = c.bottom }
            Grip.BL -> { l = c.right - w; t = c.top; r = c.right; b = c.top + h }
            else -> { l = c.left; t = c.top; r = c.left + w; b = c.top + h }
        }
    }
    return Crop(l, t, r, b)
}
