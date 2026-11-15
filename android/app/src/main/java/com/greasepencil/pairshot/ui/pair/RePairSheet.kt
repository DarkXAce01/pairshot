package com.greasepencil.pairshot.ui.pair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.ui.components.ps.PsPhotoThumb
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.psPressable
import com.greasepencil.pairshot.ui.components.ps.psSheetBleed
import com.greasepencil.pairshot.ui.theme.Ps

/**
 * Afters shot in the wrong order: pick the before this pair's after really belongs to. The two pairs
 * swap afters (a pair still waiting for its after hands over "nothing").
 */
@Composable
fun RePairSheet(pairs: List<PairRow>, hasAfter: Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Ps.colors
    PsSheet(
        if (hasAfter) "Which before does this after belong to?" else "Take the after from which pair?",
        onDismiss = onDismiss,
    ) {
        Text("The two pairs swap afters.", style = Ps.type.caption, color = c.inkSoft)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.psSheetBleed()) {
            pairs.forEach { r ->
                val name = r.areaName ?: "No label"
                val state = if (r.needsAfter) "Waiting for its after" else "Has an after"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .psPressable(onClick = { onPick(r.pair.id) }, focusShape = Ps.shape.xs)
                        .clearAndSetSemantics { contentDescription = "$name, $state" }
                        .padding(horizontal = Ps.space.gutter, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PsPhotoThumb(r.beforeFile, r.beforeRotation, 56.dp)
                    PsPhotoThumb(r.afterFile, r.afterRotation ?: 0, 56.dp)
                    Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, style = Ps.type.rowTitle, color = c.ink)
                        Text(state, style = Ps.type.bodyS, color = c.inkSoft)
                    }
                }
            }
        }
    }
}
