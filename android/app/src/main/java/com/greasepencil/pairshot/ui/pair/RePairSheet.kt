package com.greasepencil.pairshot.ui.pair

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.ui.components.PhotoImage

/**
 * Afters shot in the wrong order: pick the before this pair's after really belongs to. The two pairs
 * swap afters (a pair still waiting for its after hands over "nothing").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RePairSheet(pairs: List<PairRow>, hasAfter: Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (hasAfter) "Which before does this after belong to?" else "Take the after from which pair?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "The two pairs swap afters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.padding(vertical = 8.dp)) {
            items(pairs, key = { it.pair.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onPick(r.pair.id) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Thumb(r.beforeFile, r.beforeRotation)
                    Thumb(r.afterFile, r.afterRotation ?: 0)
                    Column(Modifier.weight(1f)) {
                        Text(r.areaName ?: "No label", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (r.needsAfter) "Waiting for its after" else "Has an after",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(file: String?, rotation: Int) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (file != null) PhotoImage(file, rotation, Modifier.fillMaxSize())
    }
}
