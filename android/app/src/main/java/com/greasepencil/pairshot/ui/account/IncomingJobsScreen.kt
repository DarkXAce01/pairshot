package com.greasepencil.pairshot.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.domain.Ago
import com.greasepencil.pairshot.ui.cloud.CloudViewModel
import com.greasepencil.pairshot.ui.cloud.copyText
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsCodeWell
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.theme.Ps

/** Connections → Jobs from your CRM: the inbound address, sync now, and a new address. */
@Composable
fun IncomingJobsScreen(vm: CloudViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val x by vm.extras.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val copy = { url: String -> copyText(context, url); vm.copied("Address") }

    AccountPage("Jobs from your CRM", vm, onBack, confirm = confirm, onConfirmDone = { confirm = null }) {
        val s = st ?: return@AccountPage
        val gutter = Modifier.padding(horizontal = Ps.space.gutter)
        Text(
            "Send jobs to this address from a Zapier or Make step, or your CRM's webhook. Each one appears in Jobs.",
            gutter.padding(bottom = 8.dp),
            style = Ps.type.body,
            color = Ps.colors.inkSoft,
        )
        val url = s.inboundUrl
        if (url == null) {
            Text("Sign in again to get your address.", gutter.padding(top = 8.dp), style = Ps.type.caption, color = Ps.colors.inkSoft)
            return@AccountPage
        }
        PsSectionHeader("Your address", Modifier.padding(top = 16.dp))
        PsCodeWell(url, onCopy = { copy(url) }, gutter, copyLabel = "Copy address")
        PsButton("Copy address", { copy(url) }, gutter.padding(top = 12.dp), icon = Icons.Outlined.ContentCopy, fullWidth = true)
        PsListBlock(Modifier.padding(top = 24.dp)) {
            row {
                PsListRow(
                    "Sync now",
                    supporting = (if (s.lastCrmSync > 0) "Last synced ${Ago.past(s.lastCrmSync, System.currentTimeMillis())}. " else "") +
                        "Syncs by itself every 30 minutes.",
                    showChevron = false,
                    enabled = !x.busy,
                    onClick = vm::syncNow,
                )
            }
            row {
                PsListRow(
                    "Make a new address…",
                    supporting = "The old one stops working.",
                    showChevron = false,
                    enabled = !x.busy,
                    onClick = {
                        confirm = Confirm(
                            "Make a new address?",
                            "The old address stops working right away. Anything sending jobs to it (Zapier, Make, your CRM) needs the new one.",
                            "Make new address", destructive = true, action = vm::rotate,
                        )
                    },
                )
            }
        }
    }
}
