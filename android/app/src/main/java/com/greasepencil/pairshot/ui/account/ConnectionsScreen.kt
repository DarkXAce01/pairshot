package com.greasepencil.pairshot.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.domain.Ago
import com.greasepencil.pairshot.ui.cloud.CloudViewModel
import com.greasepencil.pairshot.ui.components.ps.PsBottomBar
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsEmptyState
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.theme.Ps

/** Settings → Connections: jobs in from the CRM, updates out to it, API keys, and the setup guide. */
@Composable
fun ConnectionsScreen(
    vm: CloudViewModel,
    onBack: () -> Unit,
    onIncoming: () -> Unit,
    onUpdates: () -> Unit,
    onKeys: () -> Unit,
    onSeePro: () -> Unit,
    onAccount: () -> Unit,
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val x by vm.extras.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = st
    val locked = s != null && s.signedIn && !s.isPro

    AccountPage(
        "Connections", vm, onBack,
        bottomBar = if (locked) {
            { PsBottomBar { PsButton("See Pro", onSeePro, Modifier.weight(1f), fullWidth = true) } }
        } else {
            null
        },
    ) {
        if (s == null) return@AccountPage
        if (!s.signedIn) {
            PsEmptyState("Sign in first", "Connections belong to your account.", art = null)
            PsButton("Go to Account", onAccount, Modifier.padding(horizontal = Ps.space.gutter), variant = PsButtonVariant.Secondary, fullWidth = true)
            return@AccountPage
        }
        Text(
            "Pairshot can take jobs from your CRM and tell it when a client opens a link. Works with Jobber, Housecall Pro, and anything Zapier or Make can reach.",
            Modifier.padding(horizontal = Ps.space.gutter).padding(bottom = 16.dp),
            style = Ps.type.body,
            color = Ps.colors.inkSoft,
        )
        val now = System.currentTimeMillis()
        val hooks = x.hooks?.webhooks?.size ?: 0
        PsListBlock {
            row {
                HubRow(
                    Icons.Outlined.MoveToInbox, "Jobs from your CRM",
                    if (s.lastCrmSync > 0) "Last synced ${Ago.past(s.lastCrmSync, now)}" else "Not synced yet",
                    locked, onIncoming,
                )
            }
            row {
                HubRow(
                    Icons.Outlined.NotificationsActive, "Updates to your CRM",
                    when (hooks) { 0 -> "None yet"; 1 -> "1 address"; else -> "$hooks addresses" },
                    locked, onUpdates,
                )
            }
            row {
                HubRow(
                    Icons.Outlined.Key, "API keys",
                    when (val n = x.keys.size) { 0 -> "None yet"; 1 -> "1 key"; else -> "$n keys" },
                    locked, onKeys,
                )
            }
        }
        crmGuideUrl(s.serverUrl)?.let { guide ->
            PsListBlock(Modifier.padding(top = 16.dp)) {
                row {
                    PsListRow(
                        "Step-by-step setup guide",
                        trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = Ps.colors.inkFaint) },
                        showChevron = false,
                        onClick = { openUrl(context, guide) },
                    )
                }
            }
        }
    }
}

/** Locked (free plan) rows show what Pro gives, dimmed and not clickable. */
@Composable
private fun HubRow(icon: ImageVector, title: String, supporting: String, locked: Boolean, onClick: () -> Unit) {
    PsListRow(
        title,
        supporting = if (locked) null else supporting,
        leading = { Icon(icon, contentDescription = null, tint = Ps.colors.inkSoft) },
        enabled = !locked,
        onClick = if (locked) null else onClick,
    )
}
