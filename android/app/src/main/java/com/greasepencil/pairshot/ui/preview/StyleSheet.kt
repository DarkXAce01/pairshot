package com.greasepencil.pairshot.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import com.greasepencil.pairshot.ui.components.ps.PsSelectableFrame
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.PsTemplateThumb
import com.greasepencil.pairshot.ui.components.ps.psSheetBleed
import com.greasepencil.pairshot.ui.theme.Ps

/**
 * Page style for this job only. The first choice follows the business default; picking one saves at
 * once and the preview behind re-renders.
 */
@Composable
fun StyleSheet(state: PreviewState, onPick: (ClientTemplate?) -> Unit, onDismiss: () -> Unit) {
    // null = "Your default"
    val choices: List<ClientTemplate?> = listOf(null) + ClientTemplate.entries
    PsSheet("Page style", onDismiss = onDismiss) {
        LazyRow(
            Modifier.psSheetBleed().selectableGroup(),
            contentPadding = PaddingValues(horizontal = Ps.space.sheetGutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(choices.size) { i ->
                val choice = choices[i]
                val shownAs = choice ?: state.default
                val name = if (choice == null) "Your default (${state.default.label})" else choice.label
                Column(Modifier.width(108.dp)) {
                    PsSelectableFrame(
                        selected = state.override == choice,
                        onClick = { onPick(choice) },
                        label = "$name. ${shownAs.blurb}",
                    ) {
                        PsTemplateThumb(shownAs, state.accent, state.businessName, pair = state.cover)
                    }
                    Spacer(Modifier.height(Ps.space.xs))
                    Text(
                        name,
                        style = Ps.type.caption,
                        color = Ps.colors.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        // The frame already says the name.
                        modifier = Modifier.width(108.dp).clearAndSetSemantics {},
                    )
                }
            }
        }
        Text(
            "Changes this job only. Your default is in Brand." +
                if (state.linkLive) " Tap Update link on the job to show the client." else "",
            Modifier.padding(top = 16.dp, bottom = 8.dp),
            style = Ps.type.caption,
            color = Ps.colors.inkSoft,
        )
    }
}
