package com.greasepencil.pairshot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greasepencil.pairshot.data.cloud.CloudSession
import com.greasepencil.pairshot.data.cloud.CloudState
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.data.prefs.GhostMode
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.data.prefs.ThemeMode
import com.greasepencil.pairshot.data.repo.LocalStorage
import com.greasepencil.pairshot.domain.Trade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val storage: LocalStorage,
    session: CloudSession,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Who's signed in and on which plan, as last saved. Read-only: the Settings tab never calls the server. */
    val account: StateFlow<CloudState?> = session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setName(n: String) = viewModelScope.launch { store.setBusinessName(n) }
    fun setTrade(t: Trade) = viewModelScope.launch { store.setTrade(t) }
    fun setTemplate(t: String) = viewModelScope.launch { store.setTemplate(t) }
    fun setGhost(o: Float, m: GhostMode) = viewModelScope.launch { store.setGhost(o, m) }
    fun setPhone(v: String) = viewModelScope.launch { store.setBusinessPhone(v) }
    fun setReview(v: String) = viewModelScope.launch { store.setReviewUrl(v) }
    fun setBooking(v: String) = viewModelScope.launch { store.setBookingUrl(v) }
    fun setWifiOnly(v: Boolean) = viewModelScope.launch { store.setWifiOnly(v) }
    fun setThemeMode(m: ThemeMode) = viewModelScope.launch { store.setThemeMode(m) }

    val usage = MutableStateFlow<LocalStorage.Usage?>(null)
    /** Jobs sent more than [cleanupDays] days ago, ready to delete. */
    val cleanup = MutableStateFlow<LocalStorage.Cleanup?>(null)
    val cleanupDays = MutableStateFlow(90)

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
