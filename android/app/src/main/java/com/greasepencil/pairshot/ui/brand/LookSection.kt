package com.greasepencil.pairshot.ui.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.domain.gallery.BrandLook
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsSelectableFrame
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.PsSwatch
import com.greasepencil.pairshot.ui.components.ps.PsTemplateThumb
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.theme.Ps

internal fun colourOf(hex: String): Color = Color(hex.toColorInt())

/** "Navy", or "your own colour" for anything off the list; "Classic red" when nothing is picked. */
internal fun accentName(accent: String?): String = when {
    accent == null -> "the standard red"
    else -> BrandLook.named(accent)?.name ?: "your own colour"
}

/** The big live picture at the top of Brand: the page in its style, name and colour. */
@Composable
fun BrandHero(template: ClientTemplate, accent: String?, businessName: String, pair: PairRow?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = Ps.space.gutter)
            .fillMaxWidth()
            .clip(Ps.shape.lg)
            .background(Ps.colors.sunken)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        PsTemplateThumb(
            template, accent, businessName,
            modifier = Modifier.clip(Ps.shape.sm),
            width = 168.dp,
            pair = pair,
            description = "Preview of your client page in ${template.label}, ${accentName(accent)}",
        )
    }
}

/** Brand → Look: the colour swatches, the four page styles and the selected style's blurb. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookSection(
    accent: String?,
    template: ClientTemplate,
    businessName: String,
    pair: PairRow?,
    isPro: Boolean,
    onAccent: (String?) -> Unit,
    onTemplate: (ClientTemplate) -> Unit,
    onSeePro: () -> Unit,
) {
    var ownColour by rememberSaveable { mutableStateOf(false) }
    val gutter = Modifier.padding(horizontal = Ps.space.gutter)
    val named = BrandLook.named(accent)
    val custom = accent != null && named == null

    PsSectionHeader("Look", Modifier.padding(top = Ps.space.sectionGap))
    Text("Colour", gutter.semantics { heading() }, style = Ps.type.rowTitle, color = Ps.colors.ink)
    FlowRow(
        gutter.padding(top = 4.dp).fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BrandLook.colours.forEach { col ->
            val selected = named == col
            PsSwatch(
                colourOf(col.hex),
                selected = selected,
                label = col.name,
                onClick = { onAccent(col.hex) },
            )
        }
        PsSwatch(
            if (custom) colourOf(accent!!) else Ps.colors.surface,
            selected = custom,
            label = if (custom) "Your own colour, $accent" else "Your own colour",
            onClick = { ownColour = true },
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = if (custom) colourOf(BrandLook.onAccent(accent!!)) else Ps.colors.ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    Text("Page style", gutter.padding(top = 20.dp).semantics { heading() }, style = Ps.type.rowTitle, color = Ps.colors.ink)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Ps.space.gutter).padding(top = 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ClientTemplate.entries.forEach { t ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PsSelectableFrame(
                    selected = t == template,
                    onClick = { onTemplate(t) },
                    label = "${t.label}. ${t.blurb}",
                ) {
                    PsTemplateThumb(t, accent, businessName, pair = pair)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    t.label,
                    Modifier.width(108.dp).clearAndSetSemantics {},
                    style = Ps.type.caption,
                    color = if (t == template) Ps.colors.ink else Ps.colors.inkSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    Text(template.blurb, gutter.padding(top = 8.dp), style = Ps.type.bodyS, color = Ps.colors.inkSoft)

    if (!isPro) {
        Row(gutter.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Free pages carry a small “Made with Pairshot” line. Pro removes it.",
                Modifier.weight(1f),
                style = Ps.type.caption,
                color = Ps.colors.inkSoft,
            )
            Spacer(Modifier.width(8.dp))
            PsButton("See Pro", onSeePro, variant = PsButtonVariant.Quiet)
        }
    }

    if (ownColour) {
        OwnColourSheet(initial = if (custom) accent!! else null, onPick = { onAccent(it); ownColour = false }, onDismiss = { ownColour = false })
    }
}

/** A hex code of their own. Any colour is accepted; the page keeps text readable on it. */
@Composable
private fun OwnColourSheet(initial: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial ?: "#") }
    val parsed = BrandLook.normalise(text)
    val typedEnough = text.removePrefix("#").length >= 6
    val error = if (typedEnough && parsed == null) "Six letters or numbers, like 1F5FBF." else null
    PsSheet("Your colour", onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.Top) {
            PsTextField(
                text,
                { v -> text = if (v.startsWith("#")) v.take(7) else "#" + v.take(6) },
                Modifier.weight(1f),
                label = "Colour code",
                error = error,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { parsed?.let(onPick) }),
            )
            Spacer(Modifier.width(12.dp))
            // Live preview; decoration only, the field says the value.
            Box(
                Modifier
                    .padding(top = 24.dp)
                    .size(44.dp)
                    .clip(Ps.shape.pill)
                    .background(parsed?.let(::colourOf) ?: Ps.colors.sunken)
                    .border(1.dp, Ps.colors.hairline, Ps.shape.pill)
                    .clearAndSetSemantics {},
            )
        }
        Spacer(Modifier.height(Ps.space.xl))
        PsButton("Use this colour", { parsed?.let(onPick) }, enabled = parsed != null, fullWidth = true)
    }
}
