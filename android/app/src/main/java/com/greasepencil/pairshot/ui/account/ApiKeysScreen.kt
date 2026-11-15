package com.greasepencil.pairshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.cloud.ApiKeyDto
import com.greasepencil.pairshot.domain.Ago
import com.greasepencil.pairshot.ui.cloud.CloudViewModel
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsIconButton
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsMenu
import com.greasepencil.pairshot.ui.components.ps.PsMenuItem
import com.greasepencil.pairshot.ui.theme.Ps

/** Connections → API keys: list, create (shown once), delete. */
@Composable
fun ApiKeysScreen(vm: CloudViewModel, onBack: () -> Unit) {
    val x by vm.extras.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    AccountPage("API keys", vm, onBack, confirm = confirm, onConfirmDone = { confirm = null }) {
        val gutter = Modifier.padding(horizontal = Ps.space.gutter)
        Text(
            "Give a key to Zapier or Make so they can look up a job's link, list sent links, or add jobs.",
            gutter.padding(bottom = 16.dp),
            style = Ps.type.body,
            color = Ps.colors.inkSoft,
        )
        if (x.keys.isEmpty()) {
            Text("No keys yet.", gutter, style = Ps.type.bodyS, color = Ps.colors.inkSoft)
        } else {
            val now = System.currentTimeMillis()
            PsListBlock {
                x.keys.forEach { k ->
                    row {
                        KeyRow(k, now, busy = x.busy, onDelete = {
                            confirm = Confirm(
                                "Delete key ${k.prefix}…?",
                                "Anything using this key (a Zap, a Make scenario) stops working right away.",
                                "Delete key", destructive = true,
                            ) { vm.deleteKey(k.id) }
                        })
                    }
                }
            }
        }
        PsButton("Create a key", vm::newKey, gutter.padding(top = 16.dp), enabled = !x.busy, fullWidth = true)
    }
}

@Composable
private fun KeyRow(k: ApiKeyDto, now: Long, busy: Boolean, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Ps.space.row2).padding(start = Ps.space.gutter, end = 8.dp, top = Ps.space.sm, bottom = Ps.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${k.prefix}…", style = Ps.type.mono, color = Ps.colors.ink)
            Text(
                k.lastUsedAt?.let { "Used ${Ago.past(it, now)}" } ?: "Never used",
                style = Ps.type.bodyS,
                color = Ps.colors.inkSoft,
            )
        }
        Box {
            PsIconButton(Icons.Outlined.MoreVert, "More for key ${k.prefix}", { menu = true }, enabled = !busy)
            PsMenu(menu, onDismiss = { menu = false }) {
                PsMenuItem("Delete…", { menu = false; onDelete() }, destructive = true)
            }
        }
    }
}
