package com.greasepencil.pairshot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import com.greasepencil.pairshot.ui.theme.Mono
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onCloud: () -> Unit = {}, vm: SettingsViewModel = hiltViewModel()) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    val s = loaded ?: return
    // Text fields keep their own state and save as you type, so the cursor never jumps.
    var name by remember { mutableStateOf(s.businessName) }
    var template by remember { mutableStateOf(s.messageTemplate) }
    var strength by remember { mutableFloatStateOf(s.ghostStrength) }
    var phone by remember { mutableStateOf(s.businessPhone) }
    var review by remember { mutableStateOf(s.reviewUrl) }
    var booking by remember { mutableStateOf(s.bookingUrl) }
    LaunchedEffect(name) { if (name != s.businessName) vm.setName(name) }
    LaunchedEffect(phone) { if (phone != s.businessPhone) vm.setPhone(phone) }
    LaunchedEffect(review) { if (review != s.reviewUrl) vm.setReview(review) }
    LaunchedEffect(booking) { if (booking != s.bookingUrl) vm.setBooking(booking) }
    LaunchedEffect(template) { if (template != s.messageTemplate) vm.setTemplate(template) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("BUSINESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text("Business name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Trade", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Trade.entries.forEach { t ->
                    FilterChip(selected = s.trade == t, onClick = { vm.setTrade(t) }, label = { Text(t.label) })
                }
            }
            Text("CLIENT PAGE BUTTONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Each one shows on the client's page only if you fill it in.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                phone, { phone = it }, label = { Text("Business phone (Call button)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                review, { review = it }, label = { Text("Google review link (Leave a review)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                booking, { booking = it }, label = { Text("Booking link (Book again)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                template, { template = it },
                label = { Text("Message to the client") },
                supportingText = { Text("{first_name}, {service} and {link} are filled in for you.") },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text("CAMERA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("When you shoot an after, show the before as")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostMode.entries.forEach { m ->
                    FilterChip(selected = s.ghostMode == m, onClick = { vm.setGhost(strength, m) }, label = { Text(m.label) })
                }
            }
            Text(
                when (s.ghostMode) {
                    GhostMode.OUTLINE -> "Red edges of the before. Easiest to see in bright sun."
                    GhostMode.PHOTO -> "The before, see-through, over the camera."
                    GhostMode.OFF -> "Nothing over the camera. Hold Peek to check the before."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.ghostMode != GhostMode.OFF) {
                Text("Strength: ${(strength * 100).toInt()}%")
                Slider(
                    value = strength,
                    onValueChange = { strength = it },
                    onValueChangeFinished = { vm.setGhost(strength, s.ghostMode) },
                    valueRange = 0.1f..1f,
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

            Text("LINKS & CRM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = onCloud, modifier = Modifier.fillMaxWidth()) {
                Text("Account & CRM: sign in, usage, Zapier, webhooks")
            }

            Spacer(Modifier.height(32.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                StudioLockup(ink = MaterialTheme.colorScheme.onBackground, fonts = studioFonts)
                Spacer(Modifier.height(14.dp))
                PromiseLine(
                    listOf("Works offline", "No ads", "No trackers"),
                    version = BuildConfig.VERSION_NAME,
                    appName = "Pairshot",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    showLoop = false,
                    style = TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 0.8.sp),
                )
            }
        }
    }
}

/** Wi-Fi only uploads, and how much room photos take (with a way to clear old sent jobs). */
@OptIn(ExperimentalLayoutApi::class)
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
    Text("UPLOADS & PHONE STORAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = wifiOnly, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onWifiOnly),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Upload on Wi-Fi only", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Links wait for Wi-Fi to save mobile data. You can still send one right away from the job.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(wifiOnly, onCheckedChange = null)
    }
    usage?.let { u ->
        Text(
            "Photos on this phone: ${bytesText(u.photoBytes)} (${u.photoCount}). Free space: ${bytesText(u.freeBytes)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (u.freeBytes < com.greasepencil.pairshot.data.repo.LocalStorage.LOW_BYTES) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
    Text("Free up space: delete jobs whose link was sent more than", style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30, 90, 180, 365).forEach { d ->
            FilterChip(selected = days == d, onClick = { onDays(d) }, label = { Text(if (d == 365) "1 year" else "$d days") })
        }
    }
    val n = cleanup?.jobIds?.size ?: 0
    androidx.compose.material3.OutlinedButton(
        onClick = { confirm = true },
        enabled = n > 0,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(if (n == 0) "No jobs that old" else "Delete $n ${if (n == 1) "job" else "jobs"} (frees ${bytesText(cleanup!!.bytes)})…")
    }
    if (confirm && cleanup != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete $n old ${if (n == 1) "job" else "jobs"} from this phone?") },
            text = {
                Text(
                    "Their photos are deleted from this phone. Links you sent stay online for clients until they expire, " +
                        "but you won't be able to update or close them from here. This can't be undone.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = false; onFreeUp() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
