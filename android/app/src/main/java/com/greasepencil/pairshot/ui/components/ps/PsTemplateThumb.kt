package com.greasepencil.pairshot.ui.components.ps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.graphics.toColorInt
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.domain.gallery.BrandLook
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import com.greasepencil.pairshot.ui.components.PhotoImage
import com.greasepencil.pairshot.ui.theme.Mono
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.Sans
import com.greasepencil.pairshot.ui.theme.Serif

/** A template's fixed palette, as gallery.html draws it. */
private class ThumbPalette(val pageHex: String, val card: Color, val ink: Color, val muted: Color, val line: Color, val fallbackAccent: String) {
    val page: Color = hex(pageHex)
}

private fun hex(h: String): Color = Color(h.toColorInt())

private fun paletteOf(t: ClientTemplate, dark: Boolean): ThumbPalette = when (t) {
    ClientTemplate.CLASSIC -> if (dark) {
        ThumbPalette("#141312", hex("#201F1D"), hex("#EDE9E2"), hex("#A39E95"), hex("#34322E"), "#E5553A")
    } else {
        ThumbPalette("#F3F0EA", hex("#FBF9F5"), hex("#1C1B19"), hex("#6E6A62"), hex("#DDD7CC"), BrandLook.CLASSIC_FALLBACK)
    }
    ClientTemplate.SHOWROOM -> ThumbPalette("#0E0D0C", hex("#1A1917"), hex("#F2EEE7"), hex("#A8A39A"), hex("#2A2826"), "#F2EEE7")
    ClientTemplate.BRIGHT -> ThumbPalette("#FFFFFF", hex("#F5F7F8"), hex("#14171A"), hex("#5B6168"), hex("#E3E7EA"), "#14171A")
    ClientTemplate.REPORT -> ThumbPalette("#FFFFFF", hex("#FFFFFF"), hex("#111111"), hex("#5F5F5F"), hex("#D9D9D9"), "#111111")
}

/**
 * A miniature of the client page in [template]: its background, the business name in that style's
 * header, a before/after block (the [pair]'s photos, or two neutral tones), a title line and the
 * main button in the [accent]. One node for TalkBack. Drawn at [width]; the height follows (104 × 148).
 * [dark] is the client's phone setting, which only Classic follows.
 */
@Composable
fun PsTemplateThumb(
    template: ClientTemplate,
    accent: String?,
    businessName: String,
    modifier: Modifier = Modifier,
    width: Dp = 104.dp,
    pair: PairRow? = null,
    dark: Boolean = Ps.colors.isDark,
    description: String = "${template.label}. ${template.blurb}",
) {
    val p = paletteOf(template, dark)
    val accentHex = BrandLook.normalise(accent) ?: p.fallbackAccent
    val accentColour = hex(accentHex)
    val onAccent = hex(BrandLook.onAccent(accentHex))
    // Fills that barely show on the page get an ink edge, as on the page.
    val edge = if (BrandLook.contrast(accentHex, p.pageHex) < 3.0) p.ink else null
    val k = width.value / 104f
    val density = LocalDensity.current
    // Sizes follow the thumbnail, not the font scale: it is a picture of a page.
    fun sz(v: Float): TextUnit = with(density) { (v * k).dp.toSp() }
    fun d(v: Float): Dp = (v * k).dp
    val name = businessName.ifBlank { "Your business" }

    Box(
        modifier
            .size(width, d(148f))
            .background(p.page)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        when (template) {
            ClientTemplate.CLASSIC -> Column(Modifier.padding(d(8f))) {
                Text(name, style = TextStyle(fontFamily = Serif, fontSize = sz(10f)), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(d(8f)))
                Text("BEFORE & AFTER", style = TextStyle(fontFamily = Mono, fontSize = sz(4.5f), letterSpacing = 0.14.em), color = p.muted, maxLines = 1)
                Spacer(Modifier.height(d(3f)))
                TitleLine(p.ink, d(56f), d(6f), RoundedCornerShape(d(1f)))
                Spacer(Modifier.height(d(6f)))
                Split(pair, Modifier.fillMaxWidth().height(d(58f)), RoundedCornerShape(d(4f)))
                Spacer(Modifier.weight(1f))
                ButtonShape(accentColour, onAccent, edge, CircleShape, d(14f), sz(5f))
            }

            ClientTemplate.SHOWROOM -> Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = d(7f), vertical = d(6f)), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name.uppercase(),
                        style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = sz(6f), letterSpacing = 0.1.em),
                        color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Split(pair, Modifier.fillMaxWidth().height(d(66f)), RoundedCornerShape(0.dp))
                Column(Modifier.padding(d(7f)).weight(1f)) {
                    TitleLine(p.ink, d(60f), d(7f), RoundedCornerShape(d(1f)))
                    Spacer(Modifier.height(d(4f)))
                    TitleLine(p.line, d(40f), d(3f), RoundedCornerShape(d(1f)))
                    Spacer(Modifier.weight(1f))
                    ButtonShape(accentColour, onAccent, edge, RoundedCornerShape(d(2f)), d(14f), sz(5f))
                }
            }

            ClientTemplate.BRIGHT -> Column {
                Box(Modifier.fillMaxWidth().height(d(4f)).background(accentColour))
                Column(Modifier.padding(d(7f)).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(d(13f)).clip(CircleShape).background(accentColour)
                                .then(if (edge != null) Modifier.border(d(0.6f), edge, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(name.take(1).uppercase(), style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = sz(6.5f)), color = onAccent)
                        }
                        Spacer(Modifier.width(d(4f)))
                        Text(name, style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = sz(6f)), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(d(6f)))
                    TitleLine(p.ink, d(58f), d(7f), RoundedCornerShape(d(2f)))
                    Spacer(Modifier.height(d(5f)))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(d(5f))).background(p.card)
                            .border(d(0.6f), p.line, RoundedCornerShape(d(5f))).padding(d(3f)),
                    ) {
                        Split(pair, Modifier.fillMaxWidth().height(d(46f)), RoundedCornerShape(d(3f)))
                        Spacer(Modifier.height(d(3f)))
                        TitleLine(p.muted, d(28f), d(3f), RoundedCornerShape(d(1f)))
                    }
                    Spacer(Modifier.weight(1f))
                    ButtonShape(accentColour, onAccent, edge, RoundedCornerShape(d(4f)), d(14f), sz(5f))
                }
            }

            ClientTemplate.REPORT -> Column(Modifier.padding(d(7f))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), style = TextStyle(fontFamily = Serif, fontSize = sz(8f)), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("REPORT", style = TextStyle(fontFamily = Mono, fontSize = sz(4f), letterSpacing = 0.1.em), color = p.muted)
                }
                Spacer(Modifier.height(d(3f)))
                Box(Modifier.fillMaxWidth().height(d(2f)).background(accentColour))
                Spacer(Modifier.height(d(5f)))
                repeat(3) {
                    Row(Modifier.padding(vertical = d(1.2f)), verticalAlignment = Alignment.CenterVertically) {
                        TitleLine(p.muted, d(16f), d(2.5f), RoundedCornerShape(0.dp))
                        Spacer(Modifier.width(d(6f)))
                        TitleLine(p.ink, d(30f), d(2.5f), RoundedCornerShape(0.dp))
                    }
                }
                Spacer(Modifier.height(d(5f)))
                Row(Modifier.fillMaxWidth().height(d(40f)), horizontalArrangement = Arrangement.spacedBy(d(3f))) {
                    Half(pair, before = true, Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(d(1f))))
                    Half(pair, before = false, Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(d(1f))))
                }
                Spacer(Modifier.height(d(3f)))
                TitleLine(p.muted, d(34f), d(2.5f), RoundedCornerShape(0.dp))
                Spacer(Modifier.weight(1f))
                ButtonShape(accentColour, onAccent, edge, RoundedCornerShape(d(1f)), d(14f), sz(5f))
            }
        }
    }
}

/** A bar standing in for a line of text. */
@Composable
private fun TitleLine(colour: Color, width: Dp, height: Dp, shape: androidx.compose.ui.graphics.Shape) {
    Box(Modifier.size(width, height).clip(shape).background(colour))
}

/** One photo, or its neutral stand-in. */
@Composable
private fun Half(pair: PairRow?, before: Boolean, modifier: Modifier) {
    val file = if (before) pair?.beforeFile else pair?.afterFile
    val rot = if (before) pair?.beforeRotation ?: 0 else pair?.afterRotation ?: 0
    Box(modifier.background(hex(if (before) "#8B8478" else "#CBD3C6"))) {
        if (file != null) PhotoImage(file, rot, Modifier.fillMaxWidth().fillMaxHeight())
    }
}

/** The slider look: before on the left, after on the right, a thin white seam. */
@Composable
private fun Split(pair: PairRow?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    Row(modifier.clip(shape)) {
        Half(pair, before = true, Modifier.weight(1f).fillMaxHeight())
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White))
        Half(pair, before = false, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun ButtonShape(
    fill: Color,
    text: Color,
    edge: Color?,
    shape: androidx.compose.ui.graphics.Shape,
    height: Dp,
    size: TextUnit,
) {
    Box(
        Modifier.fillMaxWidth().height(height).clip(shape).background(fill)
            .then(if (edge != null) Modifier.border(0.6.dp, edge, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text("Leave a review", style = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = size), color = text, maxLines = 1)
    }
}

@PsPreviews
@Composable
private fun PsTemplateThumbPreview() = PreviewPaper {
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClientTemplate.entries.forEach { t -> PsTemplateThumb(t, "#1E3A5F", "Sparkle Wash", width = 76.dp) }
    }
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClientTemplate.entries.forEach { t -> PsTemplateThumb(t, null, "Sparkle Wash", width = 76.dp) }
    }
}
