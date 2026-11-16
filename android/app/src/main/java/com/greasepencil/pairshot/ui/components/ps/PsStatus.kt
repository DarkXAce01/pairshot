package com.greasepencil.pairshot.ui.components.ps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.domain.JobStatus
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.tabular

@Composable
private fun Pill(text: String, fill: Color, textColor: Color, border: Color?, spoken: String, modifier: Modifier) {
    val shape = Ps.shape.pill
    Box(
        modifier
            .heightIn(min = 26.dp)
            .clip(shape)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ps.type.chip, color = textColor, maxLines = 1)
    }
}

/**
 * Where a job stands, in words, with ink/pencil emphasis: Upcoming outlined, In progress sunken,
 * Done ink, Sent pencil wash, Seen pencil. TalkBack says "Status: Seen".
 */
@Composable
fun PsStatusPill(status: JobStatus, modifier: Modifier = Modifier) {
    val c = Ps.colors
    val (fill, text, border) = when (status) {
        JobStatus.UPCOMING -> Triple(Color.Transparent, c.inkSoft, c.outline)
        JobStatus.IN_PROGRESS -> Triple(c.sunken, c.ink, null)
        JobStatus.DONE -> Triple(c.ink, c.onInk, null)
        JobStatus.SENT -> Triple(c.pencilWash, c.pencilText, null)
        JobStatus.SEEN -> Triple(c.pencil, c.onPencil, null)
    }
    Pill(status.label, fill, text, border, "Status: ${status.label}", modifier)
}

/**
 * The plan: "Free" outlined, "Pro" in ink. [spoken] replaces what TalkBack says ("Plan: Pro"), for
 * when the pill marks something else, such as a feature that needs Pro.
 */
@Composable
fun PsPlanPill(pro: Boolean, modifier: Modifier = Modifier, spoken: String? = null) {
    val c = Ps.colors
    if (pro) {
        Pill("Pro", c.ink, c.onInk, null, spoken ?: "Plan: Pro", modifier)
    } else {
        Pill("Free", Color.Transparent, c.inkSoft, c.outline, spoken ?: "Plan: Free", modifier)
    }
}

/** A small mono tag over a photo (BEFORE, AFTER, Cover). */
@Composable
fun PsTag(text: String, modifier: Modifier = Modifier) {
    val c = Ps.colors
    Text(
        text.uppercase(),
        style = Ps.type.eyebrow,
        color = c.onPhotoTag,
        modifier = modifier
            .clip(Ps.shape.xs)
            .background(c.photoTag)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

enum class PsBannerTone { Neutral, Warning }

/** A note across the page: low storage, limit reached. Warning uses the pencil wash. */
@Composable
fun PsBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: PsBannerTone = PsBannerTone.Neutral,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Ps.colors
    val shape = Ps.shape.md
    val warning = tone == PsBannerTone.Warning
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Ps.space.gutter)
            .clip(shape)
            .background(if (warning) c.pencilWash else c.surface)
            .then(if (warning) Modifier else Modifier.border(1.dp, c.hairline, shape))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = Ps.type.rowTitle,
            color = if (warning) c.pencilText else c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = Ps.type.bodyS, color = c.ink)
        if (actionLabel != null && onAction != null) {
            Box(Modifier.padding(top = 4.dp)) {
                PsButton(actionLabel, onAction, variant = PsButtonVariant.Secondary, height = 48.dp)
            }
        }
    }
}

/**
 * A usage meter: label and mono value over a 6 dp track. TalkBack hears [label] then [spoken]
 * ("2 of 3 used"), not the bar.
 */
@Composable
fun PsMeter(
    label: String,
    value: String,
    spoken: String,
    fraction: Float,
    full: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Ps.colors
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label; stateDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Ps.type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
            Text(value, style = Ps.type.mono.tabular(), color = c.inkSoft)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(Ps.shape.pill),
            color = if (full) c.pencil else c.ink,
            trackColor = c.sunken,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** A 2 dp working line under the top bar. It keeps its height when idle so nothing jumps. */
@Composable
fun PsBusyBar(busy: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp)) {
        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Ps.colors.ink,
                trackColor = Color.Transparent,
                gapSize = 0.dp,
            )
        }
    }
}

@PsPreviews
@Composable
private fun PsStatusPillPreview() = PreviewPaper {
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JobStatus.entries.forEach { PsStatusPill(it) }
    }
}

@PsPreviews
@Composable
private fun PsPlanPillPreview() = PreviewPaper {
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PsPlanPill(pro = false)
        PsPlanPill(pro = true)
    }
}

@PsPreviews
@Composable
private fun PsTagPreview() = PreviewPaper {
    Box(Modifier.padding(horizontal = 20.dp).background(Ps.colors.sunken).padding(12.dp)) { PsTag("Before") }
}

@PsPreviews
@Composable
private fun PsBannerPreview() = PreviewPaper {
    PsBanner("Phone storage is nearly full", "Only 412 MB left. New photos may not save.", tone = PsBannerTone.Warning, actionLabel = "Free up space", onAction = {})
    PsBanner("This month's links are used", "Your next link can go out on 1 December.")
}

@PsPreviews
@Composable
private fun PsMeterPreview() = PreviewPaper {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PsMeter("Client links", "2 of 3", "2 of 3 used", 2 / 3f, full = false)
        PsMeter("Client links", "3 of 3", "3 of 3 used, none left", 1f, full = true)
    }
}

@PsPreviews
@Composable
private fun PsBusyBarPreview() = PreviewPaper {
    PsBusyBar(busy = true)
    PsBusyBar(busy = false)
}
