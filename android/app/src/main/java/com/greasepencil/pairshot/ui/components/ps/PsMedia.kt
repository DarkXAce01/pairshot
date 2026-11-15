package com.greasepencil.pairshot.ui.components.ps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.brand.circled
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.domain.JobStatus
import com.greasepencil.pairshot.ui.components.PhotoImage
import com.greasepencil.pairshot.ui.theme.Ps

/** A stored photo as a square thumbnail, radius sm, on a sunken placeholder. */
@Composable
fun PsPhotoThumb(
    file: String?,
    rotation: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(Ps.shape.sm)
            .background(Ps.colors.sunken),
    ) {
        if (file != null) PhotoImage(file, rotation, Modifier.fillMaxSize(), contentDescription = contentDescription)
    }
}

/** A badge over a photo, PsTag-style, with an icon. */
@Composable
private fun PhotoBadge(icon: ImageVector) {
    val c = Ps.colors
    Box(Modifier.clip(Ps.shape.xs).background(c.photoTag).padding(4.dp)) {
        Icon(icon, contentDescription = null, tint = c.onPhotoTag, modifier = Modifier.size(14.dp))
    }
}

/** A pair as a tile: before and after halves, 3:2, with Cover and Hidden badges. */
@Composable
fun PsPairTile(pair: PairRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PsPairTile(
        label = pair.areaName ?: "No label",
        beforeFile = pair.beforeFile,
        beforeRotation = pair.beforeRotation,
        afterFile = pair.afterFile,
        afterRotation = pair.afterRotation ?: 0,
        cover = pair.pair.isCover,
        hidden = pair.pair.hidden,
        onClick = onClick,
        modifier = modifier,
    )
}

/** [PsPairTile] from plain values. An empty after shows a dashed "Needs after" half. */
@Composable
fun PsPairTile(
    label: String,
    beforeFile: String?,
    beforeRotation: Int,
    afterFile: String?,
    afterRotation: Int,
    cover: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ps.colors
    val spoken = buildString {
        append(label)
        append(if (afterFile != null) ", before and after" else ", needs after")
        if (cover) append(", cover")
        if (hidden) append(", hidden from client")
    }
    Box(
        modifier
            .psPressable(onClick = onClick, focusShape = Ps.shape.sm)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(Modifier.fillMaxWidth().aspectRatio(1.5f).alpha(if (hidden) 0.45f else 1f)) {
            Box(Modifier.weight(1f).fillMaxSize().clip(Ps.shape.sm).background(c.sunken)) {
                if (beforeFile != null) PhotoImage(beforeFile, beforeRotation, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(2.dp))
            if (afterFile != null) {
                Box(Modifier.weight(1f).fillMaxSize().clip(Ps.shape.sm).background(c.sunken)) {
                    PhotoImage(afterFile, afterRotation, Modifier.fillMaxSize())
                }
            } else {
                val dash = c.outline
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(Ps.shape.sm)
                        .background(c.sunken)
                        .drawBehind {
                            val w = 1.dp.toPx()
                            drawRoundRect(
                                dash,
                                topLeft = Offset(w / 2, w / 2),
                                size = Size(size.width - w, size.height - w),
                                cornerRadius = CornerRadius(10.dp.toPx()),
                                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("NEEDS\nAFTER", style = Ps.type.eyebrow, color = c.inkSoft, textAlign = TextAlign.Center)
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (cover) PhotoBadge(Icons.Filled.Star)
            if (hidden) PhotoBadge(Icons.Outlined.VisibilityOff)
        }
    }
}

/** A 44 dp circle with a serif initial (the account row). Decorative: the row says who it is. */
@Composable
fun PsAvatar(letter: String, modifier: Modifier = Modifier) {
    val c = Ps.colors
    Box(
        modifier.size(44.dp).clip(Ps.shape.pill).background(c.sunken).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.take(1).uppercase(), style = Ps.type.titleM, color = c.ink)
    }
}

/**
 * Line art for empty states: two rounded rectangles side by side, the before drawn solid, the after
 * dashed (still to come). Decorative.
 */
@Composable
fun PsPairArt(modifier: Modifier = Modifier) {
    val c = Ps.colors
    val line = c.outline
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false, true).forEach { dashed ->
            Box(
                Modifier
                    .size(72.dp, 88.dp)
                    .drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            line,
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(10.dp.toPx()),
                            style = Stroke(w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null),
                        )
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(if (dashed) "AFTER" else "BEFORE", style = Ps.type.eyebrow, color = c.inkFaint)
            }
        }
    }
}

/** An empty screen: line art, a serif title, a short line, and optionally the way forward. */
@Composable
fun PsEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    art: (@Composable () -> Unit)? = { PsPairArt() },
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Ps.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = Ps.space.gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) {
            art()
            Spacer(Modifier.height(24.dp))
        }
        Text(
            title,
            style = Ps.type.titleM,
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(body, style = Ps.type.bodyS, color = c.inkSoft, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            PsButton(actionLabel, onAction, icon = Icons.Outlined.Add)
        }
    }
}

/**
 * The hand-drawn loop, static, round its content. Allowed only on the onboarding wordmark, "Seen" in
 * the link panel, and "Pro" on the Pro page when owned. It spills outside; it leaves itself room.
 */
@Composable
fun PsLoopMark(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pencil = Ps.colors.pencil
    Box(modifier.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.circled(pencil)) { content() }
    }
}

@PsPreviews
@Composable
private fun PsPhotoThumbPreview() = PreviewPaper {
    PsPhotoThumb(null, 0, 56.dp, Modifier.padding(horizontal = 20.dp))
}

@PsPreviews
@Composable
private fun PsPairTilePreview() = PreviewPaper {
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PsPairTile("Driveway", null, 0, "x", 0, cover = true, hidden = false, onClick = {}, modifier = Modifier.weight(1f))
        PsPairTile("Driveway", null, 0, null, 0, cover = false, hidden = true, onClick = {}, modifier = Modifier.weight(1f))
    }
}

@PsPreviews
@Composable
private fun PsAvatarPreview() = PreviewPaper {
    PsAvatar("D", Modifier.padding(horizontal = 20.dp))
}

@PsPreviews
@Composable
private fun PsEmptyStatePreview() = PreviewPaper {
    PsEmptyState(
        "No jobs yet",
        "Add a job, shoot the befores, then the afters. They pair up on their own.",
        actionLabel = "New job",
        onAction = {},
    )
}

@PsPreviews
@Composable
private fun PsLoopMarkPreview() = PreviewPaper {
    PsLoopMark(Modifier.padding(horizontal = 20.dp)) { PsStatusPill(JobStatus.SEEN) }
}
