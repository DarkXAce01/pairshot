package com.greasepencil.pairshot.ui.brand

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greasepencil.pairshot.data.cloud.CloudSession
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import kotlinx.coroutines.flow.map
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.domain.ClientMessage
import com.greasepencil.pairshot.domain.Trade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the client sees: the business's name, trade, look, page buttons and message. Everything saves at once. */
@HiltViewModel
class BrandViewModel @Inject constructor(
    private val store: SettingsStore,
    repo: JobRepository,
    cloud: CloudSession,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A recent finished pair, so the page-style pictures show the business's own work. */
    val coverPair: StateFlow<PairRow?> = repo.observeLatestFinishedPair()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** For the free plan's "Made with Pairshot" note. Read-only; nothing here calls the server. */
    val isPro: StateFlow<Boolean> = cloud.state.map { it.isPro }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAccent(hex: String?) = viewModelScope.launch { store.setAccent(hex) }
    fun setClientTemplate(t: ClientTemplate) = viewModelScope.launch { store.setClientTemplate(t) }

    fun setName(v: String) = viewModelScope.launch { store.setBusinessName(v) }
    fun setTrade(t: Trade) = viewModelScope.launch { store.setTrade(t) }
    fun setPhone(v: String) = viewModelScope.launch { store.setBusinessPhone(v) }
    fun setReview(v: String) = viewModelScope.launch { store.setReviewUrl(v) }
    fun setBooking(v: String) = viewModelScope.launch { store.setBookingUrl(v) }
    fun setMessage(v: String) = viewModelScope.launch { store.setTemplate(v) }
}

/** Example values for showing the message the way a client would get it. */
object MessageSample {
    const val CLIENT = "Dana"
    const val LINK = "pairshot.app/g/x7Kq2"

    /** A typical job for the trade, as it would be typed in the Service field. */
    fun serviceFor(trade: Trade): String = when (trade) {
        Trade.PRESSURE_WASHING -> "House wash"
        Trade.WINDOW_CLEANING -> "Window clean"
        Trade.GUTTER_CLEANING -> "Gutter clean"
        Trade.CAR_DETAILING -> "Full detail"
        Trade.CARPET_CLEANING -> "Carpet clean"
        Trade.CLEANING -> "Deep clean"
        Trade.LANDSCAPING -> "Yard cleanup"
        Trade.OTHER -> "Clean-up"
    }

    /** [template] filled with the example client, the trade's sample service and a sample link. */
    fun fill(template: String, trade: Trade): String =
        ClientMessage.fill(template, CLIENT, serviceFor(trade), LINK)
}
