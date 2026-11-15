package com.greasepencil.pairshot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.data.db.AreaEntity
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipKind
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.theme.Ps

/**
 * Pick, type or clear a pair's label. [onPickExisting] null = no label; [onNew] adds a label to the
 * job and uses it.
 */
@Composable
fun LabelSheet(
    current: String?,
    jobLabels: List<AreaEntity>,
    suggestions: List<String>,
    onPickExisting: (String?) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    val taken = jobLabels.map { it.name.lowercase() }.toSet()
    PsSheet("Label", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PsChipRow {
                PsChip("No label", selected = current == null, onClick = { onPickExisting(null) })
                jobLabels.forEach { a ->
                    PsChip(a.name, selected = current == a.id, onClick = { onPickExisting(a.id) })
                }
            }
            val extra = suggestions.filter { it.lowercase() !in taken }
            if (extra.isNotEmpty()) {
                Text(
                    "SUGGESTIONS",
                    style = Ps.type.eyebrow,
                    color = Ps.colors.inkFaint,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                )
                PsChipRow {
                    extra.forEach { name ->
                        PsChip(name, selected = false, onClick = { onNew(name) }, kind = PsChipKind.Action, leadingIcon = Icons.Outlined.Add)
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                PsTextField(
                    custom, { custom = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "Type a label",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (custom.isNotBlank()) onNew(custom) }),
                )
                Spacer(Modifier.width(12.dp))
                PsButton("Use", { onNew(custom) }, enabled = custom.isNotBlank())
            }
        }
    }
}
