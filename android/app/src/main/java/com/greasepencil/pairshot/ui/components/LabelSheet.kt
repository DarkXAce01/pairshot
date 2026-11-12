package com.greasepencil.pairshot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.data.db.AreaEntity

/**
 * Pick, type or clear a pair's label. [onPickExisting] null = no label; [onNew] adds a label to the
 * job and uses it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LabelSheet(
    current: String?,
    jobLabels: List<AreaEntity>,
    suggestions: List<String>,
    onPickExisting: (String?) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var custom by remember { mutableStateOf("") }
    val taken = jobLabels.map { it.name.lowercase() }.toSet()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Label", style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = current == null, onClick = { onPickExisting(null) }, label = { Text("No label") })
                jobLabels.forEach { a ->
                    FilterChip(selected = current == a.id, onClick = { onPickExisting(a.id) }, label = { Text(a.name) })
                }
            }
            val extra = suggestions.filter { it.lowercase() !in taken }
            if (extra.isNotEmpty()) {
                Text("Suggestions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    extra.forEach { name -> AssistChip(onClick = { onNew(name) }, label = { Text(name) }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    custom, { custom = it },
                    placeholder = { Text("Type a label") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (custom.isNotBlank()) onNew(custom) }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onNew(custom) }, enabled = custom.isNotBlank()) { Text("Use") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
