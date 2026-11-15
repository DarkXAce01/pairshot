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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.greasepencil.pairshot.BuildConfig
import com.greasepencil.pairshot.brand.PromiseLine
import com.greasepencil.pairshot.brand.StudioLockup
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.data.prefs.GhostMode
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.domain.Trade
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.components.ps.PsConfirmDialog
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsSlider
import com.greasepencil.pairshot.ui.components.ps.PsSwitchRow
import com.greasepencil.pairshot.ui.components.ps.PsTabHeader
import com.greasepencil.pairshot.ui.theme.Mono
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.studioFonts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val storage: com.greasepencil.pairshot.data.repo.LocalStorage,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setName(n: String) = viewModelScope.launch { store.setBusinessName(n) }
    fun setTrade(t: Trade) = viewModelScope.launch { store.setTrade(t) }
    fun setTemplate(t: String) = viewModelScope.launch { store.setTemplate(t) }
    fun setGhost(o: Float, m: GhostMode) = viewModelScope.launch { store.setGhost(o, m) }
    fun setPhone(v: String) = viewModelScope.launch { store.setBusinessPhone(v) }
    fun setReview(v: String) = viewModelScope.launch { store.setReviewUrl(v) }
    fun setBooking(v: String) = viewModelScope.launch { store.setBookingUrl(v) }
    fun setWifiOnly(v: Boolean) = viewModelScope.launch { store.setWifiOnly(v) }

    val usage = kotlinx.coroutines.flow.MutableStateFlow<com.greasepencil.pairshot.data.repo.LocalStorage.Usage?>(null)
    /** Jobs sent more than [cleanupDays] days ago, ready to delete. */
    val cleanup = kotlinx.coroutines.flow.MutableStateFlow<com.greasepencil.pairshot.data.repo.LocalStorage.Cleanup?>(null)
    val cleanupDays = kotlinx.coroutines.flow.MutableStateFlow(90)

    init { refreshStorage() }

    fun refreshStorage() = viewModelScope.launch {
        usage.value = storage.usage()
        cleanup.value = storage.sentOlderThan(cleanupDays.value)
    }

    fun setCleanupDays(d: Int) { cleanupDays.value = d; refreshStorage() }

    fun freeUp() = viewModelScope.launch {
        cleanup.value?.let { storage.delete(it) }
        refreshStorage().join()
    }
}

internal fun bytesText(b: Long): String = when {
    b >= 1_000_000_000 -> "%.1f GB".format(b / 1e9)
    b >= 1_000_000 -> "%.0f MB".format(b / 1e6)
    else -> "%.0f KB".format(b / 1e3)
}

/**
 * The Settings tab. The business, client page buttons and message now live in Brand; the rest
 * stays on this page until it is split into its own small pages.
 */
@Composable
fun SettingsScreen(onCloud: () -> Unit = {}, vm: SettingsViewModel = hiltViewModel()) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    PsScaffold {
        val s = loaded ?: return@PsScaffold
        var strength by remember { mutableFloatStateOf(s.ghostStrength) }
        val gutter = Modifier.padding(horizontal = Ps.space.gutter)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            PsTabHeader("Settings", eyebrow = "Pairshot ${BuildConfig.VERSION_NAME}")

            PsSectionHeader("Camera", Modifier.padding(top = 12.dp))
            Text("When you shoot an after, show the before as", gutter, style = Ps.type.body, color = Ps.colors.ink)
            PsChipRow(gutter.padding(top = 4.dp)) {
                GhostMode.entries.forEach { m ->
                    PsChip(m.label, selected = s.ghostMode == m, onClick = { vm.setGhost(strength, m) })
                }
            }
            Text(
                when (s.ghostMode) {
                    GhostMode.OUTLINE -> "Red edges of the before. Easiest to see in bright sun."
                    GhostMode.PHOTO -> "The before, see-through, over the camera."
                    GhostMode.OFF -> "Nothing over the camera. Hold Peek to check the before."
                },
                gutter.padding(top = 4.dp),
                style = Ps.type.caption,
                color = Ps.colors.inkSoft,
            )
            if (s.ghostMode != GhostMode.OFF) {
                Text("Strength: ${(strength * 100).toInt()}%", gutter.padding(top = 16.dp), style = Ps.type.body, color = Ps.colors.ink)
                PsSlider(
                    value = strength,
                    onValueChange = { strength = it },
                    contentDescription = "Ghost strength",
                    modifier = gutter,
                    valueRange = 0.1f..1f,
                    onValueChangeFinished = { vm.setGhost(strength, s.ghostMode) },
                )
            }

            PhoneSection(
                wifiOnly = s.wifiOnly, onWifiOnly = vm::setWifiOnly,
                usage = vm.usage.collectAsStateWithLifecycle().value,
                cleanup = vm.cleanup.collectAsStateWithLifecycle().value,
                days = vm.cleanupDays.collectAsStateWithLifecycle().value,
                onDays = vm::setCleanupDays,
                onFreeUp = vm::freeUp,
            )

            PsSectionHeader("Links & CRM", Modifier.padding(top = Ps.space.sectionGap))
            PsButton(
                "Account & CRM: sign in, usage, Zapier, webhooks",
                onCloud,
                gutter,
                variant = PsButtonVariant.Secondary,
                fullWidth = true,
            )

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

/** Wi-Fi only uploads, and how much room photos take (with a way to clear old sent jobs). */
@Composable
private fun PhoneSection(
    wifiOnly: Boolean,
    onWifiOnly: (Boolean) -> Unit,
    usage: com.greasepencil.pairshot.data.repo.LocalStorage.Usage?,
    cleanup: com.greasepencil.pairshot.data.repo.LocalStorage.Cleanup?,
    days: Int,
    onDays: (Int) -> Unit,
    onFreeUp: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val gutter = Modifier.padding(horizontal = Ps.space.gutter)
    PsSectionHeader("Uploads & phone storage", Modifier.padding(top = Ps.space.sectionGap))
    PsListBlock {
        row {
            PsSwitchRow(
                "Upload on Wi-Fi only",
                checked = wifiOnly,
                onChange = onWifiOnly,
                supporting = "Links wait for Wi-Fi to save mobile data. You can still send one right away from the job.",
            )
        }
    }
    usage?.let { u ->
        val low = u.freeBytes < com.greasepencil.pairshot.data.repo.LocalStorage.LOW_BYTES
        Text(
            "Photos on this phone: ${bytesText(u.photoBytes)} (${u.photoCount}). Free space: ${bytesText(u.freeBytes)}.",
            gutter.padding(top = 12.dp),
            style = Ps.type.bodyS,
            color = if (low) Ps.colors.pencilText else Ps.colors.ink,
        )
    }
    Text(
        "Free up space: delete jobs whose link was sent more than",
        gutter.padding(top = 16.dp),
        style = Ps.type.bodyS,
        color = Ps.colors.ink,
    )
    PsChipRow(gutter.padding(top = 4.dp)) {
        listOf(30, 90, 180, 365).forEach { d ->
            PsChip(if (d == 365) "1 year" else "$d days", selected = days == d, onClick = { onDays(d) })
        }
    }
    val n = cleanup?.jobIds?.size ?: 0
    PsButton(
        if (n == 0) "No jobs that old" else "Delete $n ${if (n == 1) "job" else "jobs"} (frees ${bytesText(cleanup!!.bytes)})…",
        { confirm = true },
        gutter.padding(top = 8.dp),
        variant = PsButtonVariant.Secondary,
        enabled = n > 0,
        fullWidth = true,
    )
    if (confirm && cleanup != null) {
        PsConfirmDialog(
            title = "Delete $n old ${if (n == 1) "job" else "jobs"} from this phone?",
            text = "Their photos are deleted from this phone. Links you sent stay online for clients until they expire, " +
                "but you won't be able to update or close them from here. This can't be undone.",
            confirm = "Delete",
            onConfirm = { confirm = false; onFreeUp() },
            onDismiss = { confirm = false },
            destructive = true,
        )
    }
}
