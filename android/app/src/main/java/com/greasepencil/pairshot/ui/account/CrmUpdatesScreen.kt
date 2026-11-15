package com.greasepencil.pairshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.cloud.WebhookDto
import com.greasepencil.pairshot.ui.cloud.CloudViewModel
import com.greasepencil.pairshot.ui.cloud.eventName
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsEmptyState
import com.greasepencil.pairshot.ui.components.ps.PsIconButton
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsMenu
import com.greasepencil.pairshot.ui.components.ps.PsMenuItem
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.theme.Ps
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Connections → Updates to your CRM: the addresses Pairshot tells about links, and recent deliveries. */
@Composable
fun CrmUpdatesScreen(vm: CloudViewModel, onBack: () -> Unit, onDeliveries: () -> Unit) {
    val x by vm.extras.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    AccountPage("Updates to your CRM", vm, onBack, confirm = confirm, onConfirmDone = { confirm = null }) {
        val gutter = Modifier.padding(horizontal = Ps.space.gutter)
        Text(
            "Pairshot tells these addresses when a link is sent, updated or first opened.",
            gutter.padding(bottom = 16.dp),
            style = Ps.type.body,
            color = Ps.colors.inkSoft,
        )
        val hooks = x.hooks?.webhooks.orEmpty()
        if (hooks.isEmpty()) {
            PsEmptyState("No addresses yet", "Add one and Pairshot will tell it about your links.", art = null)
        } else {
            PsListBlock {
                hooks.forEach { h ->
                    row {
                        HookRow(
                            h, busy = x.busy,
                            onTest = { vm.testHook(h.id) },
                            onRemove = {
                                confirm = Confirm("Remove this address?", "Pairshot stops telling ${h.url} about your links.", "Remove", destructive = true) {
                                    vm.deleteHook(h.id)
                                }
                            },
                        )
                    }
                }
            }
        }
        PsButton(
            "Add an address", { adding = true }, gutter.padding(top = 16.dp),
            variant = PsButtonVariant.Secondary, icon = Icons.Outlined.Add, enabled = !x.busy, fullWidth = true,
        )
        val deliveries = x.hooks?.deliveries.orEmpty()
        if (deliveries.isNotEmpty()) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val n = deliveries.count { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == today }
            PsListBlock(Modifier.padding(top = 24.dp)) {
                row { PsListRow("Recent deliveries", value = if (n > 0) "$n today" else null, onClick = onDeliveries) }
            }
        }
    }

    if (adding) {
        AddHookSheet(onAdd = { vm.addHook(it); adding = false }, onDismiss = { adding = false })
    }
}

@Composable
private fun HookRow(h: WebhookDto, busy: Boolean, onTest: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Ps.space.row2).padding(start = Ps.space.gutter, end = 8.dp, top = Ps.space.sm, bottom = Ps.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(h.url.removePrefix("https://"), style = Ps.type.mono, color = Ps.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(h.events.split(",").joinToString(", ") { eventName(it.trim()) }, style = Ps.type.bodyS, color = Ps.colors.inkSoft)
        }
        Box {
            PsIconButton(Icons.Outlined.MoreVert, "More for this address", { menu = true }, enabled = !busy)
            PsMenu(menu, onDismiss = { menu = false }) {
                PsMenuItem("Send a test", { menu = false; onTest() })
                PsMenuItem("Remove…", { menu = false; onRemove() }, destructive = true)
            }
        }
    }
}

@Composable
private fun AddHookSheet(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val trimmed = url.trim()
    val invalid = trimmed.isNotEmpty() && !trimmed.startsWith("https://")
    val ok = trimmed.isNotEmpty() && !invalid
    PsSheet("Add an address", onDismiss = onDismiss) {
        PsTextField(
            url, { url = it },
            label = "Webhook address",
            placeholder = "https://hooks.zapier.com/…",
            helper = "From Zapier's “Catch Hook” step, Make, or your CRM.",
            error = if (invalid) "It has to start with https://" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (ok) onAdd(trimmed) }),
        )
        Spacer(Modifier.height(Ps.space.xl))
        PsButton("Add", { onAdd(trimmed) }, enabled = ok, fullWidth = true)
    }
}
