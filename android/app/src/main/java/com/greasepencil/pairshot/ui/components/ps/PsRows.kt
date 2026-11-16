package com.greasepencil.pairshot.ui.components.ps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.tabular

/** A mono uppercase section label over a block, with an optional trailing slot (count, menu). */
@Composable
fun PsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Ps.space.gutter, end = if (trailing != null) 8.dp else Ps.space.gutter, bottom = Ps.space.headerGap)
            .heightIn(min = if (trailing != null) 48.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = Ps.type.eyebrow,
            color = Ps.colors.inkFaint,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** Collects the rows of a [PsListBlock]. Use `if` around a `row { }` to leave one out. */
class PsListBlockScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * The grouped block: surface fill, radius md, 1 dp hairline border, rows divided by a hairline
 * inset 20 dp from the start. Sits inside the 20 dp screen gutter.
 */
@Composable
fun PsListBlock(modifier: Modifier = Modifier, content: PsListBlockScope.() -> Unit) {
    val c = Ps.colors
    val shape = Ps.shape.md
    val rows = PsListBlockScope().apply(content).rows
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Ps.space.gutter)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.hairline, shape),
    ) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = Ps.space.gutter), thickness = 1.dp, color = c.hairline)
            row()
        }
    }
}

/**
 * [PsListBlock] for long lists inside a `LazyColumn`: each item is one row of the same block (same
 * fill, border, corners and inset dividers), so only the rows on screen are composed.
 */
fun <T> LazyListScope.psListBlockItems(
    items: List<T>,
    key: ((T) -> Any)? = null,
    content: @Composable (T) -> Unit,
) {
    val last = items.lastIndex
    itemsIndexed(items, key = key?.let { k -> { _: Int, item: T -> k(item) } }) { i, item ->
        PsListBlockSegment(first = i == 0, last = i == last) { content(item) }
    }
}

/** One row's slice of a list block: rounded only where the block starts and ends. */
@Composable
private fun PsListBlockSegment(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val c = Ps.colors
    // The md radius, read from the token so the block follows the shape scale.
    val md = Ps.shape.md as CornerBasedShape
    val corner = md.topStart
    val shape = md.copy(
        topStart = if (first) corner else ZeroCornerSize,
        topEnd = if (first) corner else ZeroCornerSize,
        bottomStart = if (last) corner else ZeroCornerSize,
        bottomEnd = if (last) corner else ZeroCornerSize,
    )
    val hairline = c.hairline
    val inset = Ps.space.gutter
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ps.space.gutter)
            .clip(shape)
            .background(c.surface)
            .drawWithContent {
                drawContent()
                val w = 1.dp.toPx()
                val r = corner.toPx(size, this)
                // The block's outline, stretched past the edges this slice doesn't own and clipped,
                // so neighbouring slices join into one bordered block.
                val top = if (first) w / 2 else -r * 2
                val bottom = if (last) size.height - w / 2 else size.height + r * 2
                clipRect {
                    drawRoundRect(
                        hairline,
                        topLeft = Offset(w / 2, top),
                        size = Size(size.width - w, bottom - top),
                        cornerRadius = CornerRadius(r),
                        style = Stroke(w),
                    )
                    if (!first) drawLine(hairline, Offset(inset.toPx(), w / 2), Offset(size.width, w / 2), w)
                }
            },
    ) { content() }
}

/** The navigation chevron at the end of a row. */
@Composable
fun PsChevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = Ps.colors.inkFaint,
        modifier = Modifier.size(20.dp),
    )
}

/**
 * A list row: title, optional supporting line, optional right-aligned [value], and a chevron when it
 * opens something. The whole row is one button for TalkBack. [trailing] replaces the value and
 * chevron (e.g. a plan pill).
 */
@Composable
fun PsListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
    titleColor: Color = Ps.colors.ink,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = Ps.colors
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = if (supporting != null) Ps.space.row2 else Ps.space.row)
    val interactive = if (onClick != null) {
        base.psPressable(onClick = onClick, enabled = enabled, focusShape = Ps.shape.xs)
    } else {
        base.semantics(mergeDescendants = true) {}
    }
    Row(
        interactive
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = Ps.space.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Ps.type.rowTitle, color = titleColor)
            if (supporting != null) Text(supporting, style = Ps.type.bodyS, color = c.inkSoft)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                value, style = Ps.type.bodyS.tabular(), color = c.inkSoft, textAlign = TextAlign.End,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp),
            )
        }
        if (onClick != null && showChevron) {
            Spacer(Modifier.width(4.dp))
            PsChevron()
        }
    }
}

/** A row with a switch. The whole row toggles; TalkBack hears a switch with its state. Never red. */
@Composable
fun PsSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val c = Ps.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting != null) Ps.space.row2 else Ps.space.row)
            .psToggleable(value = checked, onValueChange = onChange, role = Role.Switch, enabled = enabled, focusShape = Ps.shape.xs)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = Ps.space.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Ps.type.rowTitle, color = c.ink)
            if (supporting != null) Text(supporting, style = Ps.type.bodyS, color = c.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.ink,
                checkedThumbColor = c.onInk,
                checkedBorderColor = c.ink,
                uncheckedTrackColor = c.sunken,
                // The spec's surface thumb vanishes on the sunken track (1.2:1); outline keeps 3:1.
                uncheckedThumbColor = c.outline,
                uncheckedBorderColor = c.outline,
                disabledCheckedTrackColor = c.ink,
                disabledCheckedThumbColor = c.onInk,
                disabledUncheckedTrackColor = c.sunken,
                disabledUncheckedThumbColor = c.outline,
                disabledUncheckedBorderColor = c.outline,
            ),
        )
    }
}

/** A fact that isn't clickable: title and a mono value (version, plan limits). */
@Composable
fun PsValueRow(title: String, value: String, modifier: Modifier = Modifier) {
    val c = Ps.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Ps.space.row)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Ps.space.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Ps.type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value, style = Ps.type.mono, color = c.inkSoft, textAlign = TextAlign.End,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}

@PsPreviews
@Composable
private fun PsSectionHeaderPreview() = PreviewPaper {
    PsSectionHeader("Today")
    PsSectionHeader("Driveway", trailing = { Text("2/3", style = Ps.type.mono, color = Ps.colors.inkFaint) })
}

@PsPreviews
@Composable
private fun PsListBlockPreview() = PreviewPaper {
    PsListBlock {
        row { PsListRow("Camera", value = "Outline", onClick = {}) }
        row { PsListRow("Name", value = "Sparkle & Shine Exterior Cleaning Co Ltd", onClick = {}) } // 40 characters
        row { PsListRow("Sign in", supporting = "Only needed to send links.", onClick = {}) }
        row {
            PsListRow(
                "Ghost",
                leading = { Icon(Icons.Outlined.CameraAlt, null, tint = Ps.colors.inkSoft) },
                trailing = { PsPlanPill(pro = true) },
                onClick = {},
            )
        }
        row { PsValueRow("Version", "0.1.0") }
        row { PsValueRow("Business", "Sparkle & Shine Exterior Cleaning Co Ltd") }
    }
}

@PsPreviews
@Composable
private fun PsSwitchRowPreview() = PreviewPaper {
    PsListBlock {
        row { PsSwitchRow("Wi-Fi only", checked = true, onChange = {}, supporting = "Links wait for Wi-Fi.") }
        row { PsSwitchRow("Wi-Fi only", checked = false, onChange = {}) }
    }
}
