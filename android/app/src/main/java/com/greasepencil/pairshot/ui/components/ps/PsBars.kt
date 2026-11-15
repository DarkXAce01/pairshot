package com.greasepencil.pairshot.ui.components.ps

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.ui.theme.Ps

/**
 * Pushed screens' top bar: back arrow, an optional mono eyebrow over a serif title (one line).
 * The title is a heading for TalkBack.
 */
@Composable
fun PsTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Ps.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = if (onBack != null) 8.dp else Ps.space.gutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            PsIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            if (eyebrow != null) {
                Text(eyebrow.uppercase(), style = Ps.type.eyebrow, color = c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                title,
                style = Ps.type.titleL,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Root tab screens' header: eyebrow, then a big serif title. It scrolls with the content. */
@Composable
fun PsTabHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    titleModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Ps.colors
    Row(
        modifier.fillMaxWidth().padding(start = Ps.space.gutter, end = 8.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(eyebrow.uppercase(), style = Ps.type.eyebrow, color = c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
            }
            Text(title, style = Ps.type.titleXL, color = c.ink, modifier = titleModifier.semantics { heading() })
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** One item of [PsTabBar]. */
data class PsTab(val label: String, val icon: ImageVector)

/**
 * The bottom tabs: outlined icon over a label; the selected one is in ink with a pencil stroke
 * drawn in under its label. Consumes the navigation bar inset.
 */
@Composable
fun PsTabBar(
    tabs: List<PsTab>,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ps.colors
    Column(modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(thickness = 1.dp, color = c.hairline)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .heightIn(min = 64.dp)
                .selectableGroup(),
        ) {
            tabs.forEachIndexed { i, tab ->
                val selected = i == current
                val tint = if (selected) c.ink else c.inkFaint
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .psSelectable(selected = selected, onClick = { onSelect(i) }, role = Role.Tab)
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, style = Ps.type.chip, color = tint, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    if (selected) PsPencilStroke(animate = true) else Spacer(Modifier.size(26.dp, 6.dp))
                }
            }
        }
    }
}

/**
 * The short slanted grease-pencil stroke under a chosen tab (Album's). With [animate] it draws in
 * over the `stroke` duration, unless animations are off.
 */
@Composable
fun PsPencilStroke(modifier: Modifier = Modifier, animate: Boolean = false) {
    val reduced = rememberReducedMotion()
    val duration = Ps.motion.stroke
    val easing = Ps.motion.easing
    val progress = remember { Animatable(if (animate && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(duration, easing = easing))
    }
    val pencil = Ps.colors.pencil
    Canvas(modifier.size(26.dp, 6.dp)) {
        val p = progress.value
        if (p <= 0f) return@Canvas
        val w = 2.5.dp.toPx()
        val start = Offset(w / 2 + 1f, size.height * 0.75f)
        val endFull = Offset(size.width - w / 2 - 1f, size.height * 0.3f)
        val end = Offset(start.x + (endFull.x - start.x) * p, start.y + (endFull.y - start.y) * p)
        drawLine(pencil.copy(alpha = 0.92f), start, end, w, StrokeCap.Round)
        // The faint, slightly offset second pass that makes it look waxy.
        drawLine(
            pencil.copy(alpha = 0.35f),
            start + Offset(w * 0.3f, -w * 0.25f),
            end + Offset(w * 0.3f, -w * 0.25f),
            w * 0.55f,
            StrokeCap.Round,
        )
    }
}

/**
 * The pinned action bar of pushed screens: surface, top hairline, 12/20 dp padding and 12 dp
 * between buttons. Give each button `Modifier.weight(1f)` so they share the width. It pads for the
 * navigation bar and the keyboard.
 */
@Composable
fun PsBottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = Ps.colors
    Column(modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(thickness = 1.dp, color = c.hairline)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = Ps.space.gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The three tabs, for previews and the component gallery (the real model arrives with navigation). */
internal val SampleTabs = listOf(
    PsTab("Jobs", Icons.Outlined.WorkOutline),
    PsTab("Brand", Icons.Outlined.Brush),
    PsTab("Settings", Icons.Outlined.Settings),
)

@PsPreviews
@Composable
private fun PsTopBarPreview() = PreviewPaper {
    PsTopBar("Dana Whitfield", onBack = {}, actions = { PsIconButton(Icons.Outlined.Search, "Search", {}) })
    PsTopBar("Updates to your CRM", onBack = {}, eyebrow = "Connections")
}

@PsPreviews
@Composable
private fun PsTabHeaderPreview() = PreviewPaper {
    PsTabHeader("Jobs", eyebrow = "Sparkle Wash", actions = { PsIconButton(Icons.Outlined.Search, "Search jobs", {}) })
}

@PsPreviews
@Composable
private fun PsTabBarPreview() = PreviewPaper {
    PsTabBar(SampleTabs, current = 0, onSelect = {})
}

@PsPreviews
@Composable
private fun PsPencilStrokePreview() = PreviewPaper {
    Box(Modifier.padding(horizontal = 20.dp)) { PsPencilStroke() }
}

@PsPreviews
@Composable
private fun PsBottomBarPreview() = PreviewPaper {
    PsBottomBar {
        PsButton("Preview", {}, Modifier.weight(1f), variant = PsButtonVariant.Secondary)
        PsButton("Send link", {}, Modifier.weight(1f), variant = PsButtonVariant.Accent)
    }
}
