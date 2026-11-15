package com.greasepencil.pairshot.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.BuildConfig
import com.greasepencil.pairshot.brand.PromiseLine
import com.greasepencil.pairshot.brand.StudioLockup
import com.greasepencil.pairshot.data.prefs.ThemeMode
import com.greasepencil.pairshot.ui.components.ps.PsAvatar
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsPlanPill
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsSegmented
import com.greasepencil.pairshot.ui.components.ps.PsTabHeader
import com.greasepencil.pairshot.ui.theme.Mono
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.studioFonts

/** Where each Settings row goes. [onDeveloper] is null in release builds, and the row isn't shown. */
class SettingsNav(
    val onAccount: () -> Unit,
    val onPro: () -> Unit,
    val onCamera: () -> Unit,
    val onStorage: () -> Unit,
    val onConnections: () -> Unit,
    val onAbout: () -> Unit,
    val onDeveloper: (() -> Unit)?,
)

/** The Settings tab: a short map. The only input here is Appearance. */
@Composable
fun SettingsScreen(nav: SettingsNav, vm: SettingsViewModel = hiltViewModel()) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    PsScaffold {
        val s = loaded ?: return@PsScaffold
        val a = account
        val pro = a?.isPro == true
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            PsTabHeader("Settings", eyebrow = "Pairshot ${BuildConfig.VERSION_NAME}")

            PsListBlock(Modifier.padding(top = 12.dp)) {
                row {
                    if (a?.signedIn == true) {
                        val name = a.email ?: "Test account"
                        PsListRow(
                            name,
                            leading = { PsAvatar(name) },
                            trailing = { PsPlanPill(pro) },
                            onClick = nav.onAccount,
                        )
                    } else {
                        PsListRow("Sign in", supporting = "Only needed to send links.", onClick = nav.onAccount)
                    }
                }
                row {
                    PsListRow("Pairshot Pro", value = if (pro) "On" else "One payment", onClick = nav.onPro)
                }
            }

            PsSectionHeader("Work", Modifier.padding(top = Ps.space.sectionGap))
            PsListBlock {
                row { PsListRow("Camera", value = s.ghostMode.label, onClick = nav.onCamera) }
                row { PsListRow("Uploads & storage", value = usage?.let { bytesText(it.photoBytes) }, onClick = nav.onStorage) }
                row {
                    PsListRow(
                        "Connections",
                        trailing = if (!pro) ({ PsPlanPill(pro = true) }) else null,
                        onClick = nav.onConnections,
                    )
                }
            }

            PsSectionHeader("App", Modifier.padding(top = Ps.space.sectionGap))
            PsListBlock {
                row {
                    // Title above the control, so neither is squeezed at large font sizes.
                    Column(Modifier.fillMaxWidth().padding(horizontal = Ps.space.gutter, vertical = 12.dp)) {
                        Text("Appearance", Modifier.semantics { heading() }, style = Ps.type.rowTitle, color = Ps.colors.ink)
                        Spacer(Modifier.height(8.dp))
                        PsSegmented(
                            ThemeMode.entries.map { it.label },
                            selected = s.themeMode.ordinal,
                            onSelect = { vm.setThemeMode(ThemeMode.entries[it]) },
                        )
                    }
                }
                row { PsListRow("Help & about", onClick = nav.onAbout) }
                nav.onDeveloper?.let { dev -> row { PsListRow("Developer", onClick = dev) } }
            }

            Spacer(Modifier.height(48.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                StudioLockup(ink = Ps.colors.ink, fonts = studioFonts)
                Spacer(Modifier.height(14.dp))
                PromiseLine(
                    listOf("Works offline", "No ads", "No trackers"),
                    version = BuildConfig.VERSION_NAME,
                    appName = "Pairshot",
                    color = Ps.colors.inkSoft,
                    showLoop = false,
                    style = TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 0.8.sp),
                )
            }
        }
    }
}
