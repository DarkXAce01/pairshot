package com.greasepencil.pairshot.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.cloud.DeliveryDto
import com.greasepencil.pairshot.domain.Ago
import com.greasepencil.pairshot.ui.cloud.CloudViewModel
import com.greasepencil.pairshot.ui.cloud.eventName
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.theme.Ps

/** Updates to your CRM → Recent deliveries. Status is said by icon and words, never by colour alone. */
@Composable
fun DeliveryLogScreen(vm: CloudViewModel, onBack: () -> Unit) {
    val x by vm.extras.collectAsStateWithLifecycle()
    AccountPage("Recent deliveries", vm, onBack) {
        val deliveries = x.hooks?.deliveries.orEmpty()
        if (deliveries.isEmpty()) {
            Text("No deliveries yet.", Modifier.padding(horizontal = Ps.space.gutter), style = Ps.type.body, color = Ps.colors.inkSoft)
            return@AccountPage
        }
        val now = System.currentTimeMillis()
        PsListBlock {
            deliveries.forEach { d -> row { DeliveryRow(d, now) } }
        }
    }
}

@Composable
private fun DeliveryRow(d: DeliveryDto, now: Long) {
    val c = Ps.colors
    val (icon, tint, result) = when (d.status) {
        "ok" -> Triple(Icons.Outlined.CheckCircle, c.ink, "Delivered")
        "failed" -> Triple(Icons.Outlined.ErrorOutline, c.pencilText, "Gave up after ${d.attempts} tries")
        else -> Triple(
            Icons.Outlined.Schedule, c.inkSoft,
            "Retrying (${d.attempts} ${if (d.attempts == 1) "try" else "tries"}${d.lastStatus?.let { ", got $it" } ?: ""})",
        )
    }
    PsListRow(
        "${eventName(d.event)}: $result",
        supporting = Ago.past(d.createdAt, now),
        leading = { Icon(icon, contentDescription = null, tint = tint) },
    )
}
