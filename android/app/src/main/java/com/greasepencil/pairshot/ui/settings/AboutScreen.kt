package com.greasepencil.pairshot.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.BuildConfig
import com.greasepencil.pairshot.ui.account.crmGuideUrl
import com.greasepencil.pairshot.ui.account.openUrl
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.components.ps.PsValueRow
import com.greasepencil.pairshot.ui.theme.Ps

private enum class AboutSheet { STORES, LICENCES }

/** The fonts the app ships, and their licence files in assets/licences. */
private val licences = listOf(
    "Instrument Serif" to "licences/InstrumentSerif-OFL.txt",
    "Geist" to "licences/Geist-OFL.txt",
    "Geist Mono" to "licences/GeistMono-OFL.txt",
)

/** Settings → Help & about. */
@Composable
fun AboutScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val account by vm.account.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<AboutSheet?>(null) }
    PsScaffold(topBar = { PsTopBar("Help & about", onBack = onBack) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            val server = account?.serverUrl?.takeIf { it.isNotBlank() }
            PsListBlock {
                if (server != null) {
                    row {
                        PsListRow(
                            "Set up your CRM",
                            trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = Ps.colors.inkFaint) },
                            showChevron = false,
                            onClick = { crmGuideUrl(server)?.let { openUrl(context, it) } },
                        )
                    }
                }
                row { PsListRow("What Pairshot stores", onClick = { sheet = AboutSheet.STORES }) }
                row { PsListRow("Licences", onClick = { sheet = AboutSheet.LICENCES }) }
            }
            PsListBlock(Modifier.padding(top = 16.dp)) {
                row { PsValueRow("Version", BuildConfig.VERSION_NAME) }
            }
        }
    }
    when (sheet) {
        AboutSheet.STORES -> PsSheet("What Pairshot stores", onDismiss = { sheet = null }) {
            listOf(
                "Your jobs and photos live on this phone.",
                "The server holds only what you send to a client, and only until the link expires.",
                "Location and other camera details (EXIF) are removed from photos before they upload.",
            ).forEach { Text(it, Modifier.padding(bottom = 12.dp), style = Ps.type.body, color = Ps.colors.ink) }
            Spacer(Modifier.height(8.dp))
            PsButton("Done", { sheet = null }, fullWidth = true)
        }
        AboutSheet.LICENCES -> PsSheet("Licences", onDismiss = { sheet = null }) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Instrument Serif, Geist and Geist Mono are used under the SIL Open Font License 1.1.",
                    Modifier.padding(bottom = 16.dp),
                    style = Ps.type.body,
                    color = Ps.colors.ink,
                )
                licences.forEach { (name, path) ->
                    val text = remember(path) {
                        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")
                    }
                    Text(name, Modifier.semantics { heading() }.padding(top = 8.dp, bottom = 4.dp), style = Ps.type.rowTitle, color = Ps.colors.ink)
                    Text(text, style = Ps.type.caption, color = Ps.colors.inkSoft)
                }
            }
            Spacer(Modifier.height(16.dp))
            PsButton("Done", { sheet = null }, fullWidth = true)
        }
        null -> Unit
    }
}
