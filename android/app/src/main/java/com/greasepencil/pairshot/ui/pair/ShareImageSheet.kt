package com.greasepencil.pairshot.ui.pair

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.domain.CollageLayout
import com.greasepencil.pairshot.ui.components.CollageExporter
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsSelectableFrame
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.theme.Ps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** Long edge of the large preview and of the layout thumbnails, in pixels (spec 8b). */
private const val PREVIEW_EDGE = 720
private const val THUMB_EDGE = 240

/** One render at a time, so memory stays at one canvas plus one decoded photo. */
@OptIn(ExperimentalCoroutinesApi::class)
private val renderer = Dispatchers.Default.limitedParallelism(1)

/**
 * "Share as one image": a live preview of the chosen layout, real mini renders of all five, and
 * Share. The frame follows the business's page style and colour; the free mark shows on free.
 */
@Composable
fun ShareImageSheet(
    pair: PairRow,
    look: CollageExporter.Look,
    initial: CollageLayout.Style,
    busy: Boolean,
    onSelect: (CollageLayout.Style) -> Unit,
    onShare: (CollageLayout.Style) -> Unit,
    onSeePro: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var selected by remember { mutableStateOf(initial) }
    val previews = remember(pair, look) { mutableStateMapOf<CollageLayout.Style, ImageBitmap>() }
    val thumbs = remember(pair, look) { mutableStateMapOf<CollageLayout.Style, ImageBitmap>() }
    val styles = CollageLayout.Style.entries

    // The selected preview first, then the five thumbnails, then the other previews (so any tap is instant).
    LaunchedEffect(pair, look) {
        suspend fun draw(style: CollageLayout.Style, edge: Int) = withContext(renderer) {
            runCatching { CollageExporter.render(context, pair, style, look, edge).asImageBitmap() }.getOrNull()
        }
        draw(selected, PREVIEW_EDGE)?.let { previews[selected] = it }
        for (s in styles) draw(s, THUMB_EDGE)?.let { thumbs[s] = it }
        for (s in styles) if (s !in previews) draw(s, PREVIEW_EDGE)?.let { previews[s] = it }
    }
    val aspects = remember(pair, look) {
        styles.associateWith { s -> CollageExporter.planOf(pair, s, look, THUMB_EDGE).let { it.width.toFloat() / it.height } }
    }

    // At most 40 % of the window, so the Share button stays above the fold on small phones.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val previewHeight = minOf(320.dp, windowHeight * 0.4f).takeIf { it > 0.dp } ?: 320.dp
    PsSheet("Share as one image", onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.Center) {
            Crossfade(selected, animationSpec = tween(Ps.motion.standard), label = "sharePreview") { style ->
                Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.Center) {
                    Render(
                        previews[style],
                        aspects.getValue(style),
                        "Preview, ${style.label}",
                        Modifier.clip(Ps.shape.sm),
                    )
                }
            }
        }
        Spacer(Modifier.height(Ps.space.md))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Ps.space.sm),
        ) {
            styles.forEach { style ->
                Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PsSelectableFrame(
                        selected = style == selected,
                        onClick = { selected = style; onSelect(style) },
                        label = style.label,
                    ) {
                        Box(Modifier.size(84.dp).background(Ps.colors.surface), contentAlignment = Alignment.Center) {
                            Render(thumbs[style], aspects.getValue(style), null, Modifier.padding(Ps.space.xxs))
                        }
                    }
                    Spacer(Modifier.height(Ps.space.xxs))
                    Text(
                        style.label,
                        style = Ps.type.caption,
                        color = if (style == selected) Ps.colors.ink else Ps.colors.inkSoft,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
        if (look.watermark) {
            Spacer(Modifier.height(Ps.space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Free adds a small \"Made with Pairshot\" mark.",
                    style = Ps.type.caption,
                    color = Ps.colors.inkSoft,
                    modifier = Modifier.weight(1f),
                )
                PsButton("See Pro", onSeePro, variant = PsButtonVariant.Quiet)
            }
        }
        Spacer(Modifier.height(Ps.space.md))
        PsButton(
            if (busy) "Making image…" else "Share",
            { onShare(selected) },
            busy = busy,
            fullWidth = true,
        )
    }
}

/** A render at its own shape inside the space given; a sunken block until it's ready. */
@Composable
private fun Render(bitmap: ImageBitmap?, aspect: Float, description: String?, modifier: Modifier) {
    val m = modifier.aspectRatio(aspect)
    if (bitmap == null) {
        Box(m.background(Ps.colors.sunken).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier))
    } else {
        Image(bitmap, description, m, contentScale = ContentScale.Fit)
    }
}
