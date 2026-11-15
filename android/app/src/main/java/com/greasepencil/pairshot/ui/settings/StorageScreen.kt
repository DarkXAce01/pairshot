package com.greasepencil.pairshot.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.repo.LocalStorage
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.components.ps.PsConfirmDialog
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsSwitchRow
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.components.ps.PsValueRow
import com.greasepencil.pairshot.ui.theme.Ps

/** Settings → Uploads & storage: Wi-Fi only, what photos take on the phone, and clearing old sent jobs. */
@Composable
fun StorageScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val cleanup by vm.cleanup.collectAsStateWithLifecycle()
    val days by vm.cleanupDays.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    PsScaffold(topBar = { PsTopBar("Uploads & storage", onBack = onBack) }) {
        val s = loaded ?: return@PsScaffold
        val gutter = Modifier.padding(horizontal = Ps.space.gutter)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            PsListBlock {
                row {
                    PsSwitchRow(
                        "Upload on Wi-Fi only",
                        checked = s.wifiOnly,
                        onChange = vm::setWifiOnly,
                        supporting = "Links wait for Wi-Fi to save mobile data. You can still send one right away from the job.",
                    )
                }
            }

            PsSectionHeader("On this phone", Modifier.padding(top = Ps.space.sectionGap))
            usage?.let { u ->
                val low = u.freeBytes < LocalStorage.LOW_BYTES
                PsListBlock {
                    row { PsValueRow("Photos", "${bytesText(u.photoBytes)} (${u.photoCount})") }
                    row {
                        if (low) {
                            // Said in words too, not by the colour alone.
                            LowSpaceRow(bytesText(u.freeBytes))
                        } else {
                            PsValueRow("Free space", bytesText(u.freeBytes))
                        }
                    }
                }
            }

            PsSectionHeader("Free up space", Modifier.padding(top = Ps.space.sectionGap))
            Text("Delete jobs whose link was sent more than", gutter, style = Ps.type.bodyS, color = Ps.colors.ink)
            PsChipRow(gutter.padding(top = 8.dp)) {
                listOf(30, 90, 180, 365).forEach { d ->
                    PsChip(if (d == 365) "1 year" else "$d days", selected = days == d, onClick = { vm.setCleanupDays(d) })
                }
            }
            val c = cleanup
            val n = c?.jobIds?.size ?: 0
            PsButton(
                if (n == 0 || c == null) "No jobs that old" else "Delete $n ${if (n == 1) "job" else "jobs"} · frees ${bytesText(c.bytes)}…",
                { confirm = true },
                gutter.padding(top = 12.dp),
                variant = PsButtonVariant.Secondary,
                enabled = n > 0,
                fullWidth = true,
            )
            if (confirm && c != null) {
                PsConfirmDialog(
                    title = "Delete $n old ${if (n == 1) "job" else "jobs"} from this phone?",
                    text = "Their photos are deleted from this phone. Links you sent stay online for clients until they expire, " +
                        "but you won't be able to update or close them from here. This can't be undone.",
                    confirm = "Delete",
                    onConfirm = { confirm = false; vm.freeUp() },
                    onDismiss = { confirm = false },
                    destructive = true,
                )
            }
        }
    }
}

/** [PsValueRow] with the value in pencilText and the word "low", for a nearly full phone. */
@Composable
private fun LowSpaceRow(value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Ps.space.row).semantics(mergeDescendants = true) {}
            .padding(horizontal = Ps.space.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Free space", Modifier.weight(1f), style = Ps.type.rowTitle, color = Ps.colors.ink)
        Text("$value · low", style = Ps.type.mono, color = Ps.colors.pencilText)
    }
}
