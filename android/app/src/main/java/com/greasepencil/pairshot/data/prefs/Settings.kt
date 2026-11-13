package com.greasepencil.pairshot.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greasepencil.pairshot.domain.Trade
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** How the before is shown while shooting the after. */
enum class GhostMode(val label: String) { OUTLINE("Outline"), PHOTO("Photo"), OFF("Off") }

data class AppSettings(
    val onboarded: Boolean = false,
    val businessName: String = "",
    val trade: Trade = Trade.PRESSURE_WASHING,
    val messageTemplate: String = DEFAULT_TEMPLATE,
    val ghostStrength: Float = 0.5f,
    val ghostMode: GhostMode = GhostMode.OUTLINE,
    val businessPhone: String = "",
    val reviewUrl: String = "",
    val bookingUrl: String = "",
    /** Links upload only on Wi-Fi (they wait otherwise). */
    val wifiOnly: Boolean = false,
    /** Not stored here: filled in from the account's plan (CloudSession) where it matters. */
    val isPro: Boolean = false,
) {
    companion object {
        const val DEFAULT_TEMPLATE = "Hi {first_name}, here's your {service} before & after: {link}"
    }
}

/** Read-only view of the settings, for code that only needs to read them (and for tests). */
interface SettingsSource {
    val settings: Flow<AppSettings>
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) : SettingsSource {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val businessName = stringPreferencesKey("business_name")
        val trade = stringPreferencesKey("trade")
        val template = stringPreferencesKey("message_template")
        val ghostStrength = floatPreferencesKey("ghost_strength")
        val ghostMode = stringPreferencesKey("ghost_mode")
        val phone = stringPreferencesKey("business_phone")
        val review = stringPreferencesKey("review_url")
        val booking = stringPreferencesKey("booking_url")
        val wifiOnly = booleanPreferencesKey("wifi_only")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboarded = p[Keys.onboarded] ?: false,
            businessName = p[Keys.businessName] ?: "",
            trade = p[Keys.trade]?.let { runCatching { Trade.valueOf(it) }.getOrNull() } ?: Trade.PRESSURE_WASHING,
            messageTemplate = p[Keys.template] ?: AppSettings.DEFAULT_TEMPLATE,
            ghostStrength = p[Keys.ghostStrength] ?: 0.5f,
            ghostMode = p[Keys.ghostMode]?.let { runCatching { GhostMode.valueOf(it) }.getOrNull() } ?: GhostMode.OUTLINE,
            businessPhone = p[Keys.phone] ?: "",
            reviewUrl = p[Keys.review] ?: "",
            bookingUrl = p[Keys.booking] ?: "",
            wifiOnly = p[Keys.wifiOnly] ?: false,
        )
    }

    suspend fun finishOnboarding(businessName: String, trade: Trade) = context.dataStore.edit {
        it[Keys.businessName] = businessName.trim()
        it[Keys.trade] = trade.name
        it[Keys.onboarded] = true
    }

    suspend fun setBusinessName(name: String) = context.dataStore.edit { it[Keys.businessName] = name.trim() }
    suspend fun setTrade(trade: Trade) = context.dataStore.edit { it[Keys.trade] = trade.name }
    suspend fun setTemplate(t: String) = context.dataStore.edit { it[Keys.template] = t }
    suspend fun setGhost(strength: Float, mode: GhostMode) = context.dataStore.edit {
        it[Keys.ghostStrength] = strength.coerceIn(0.1f, 1f)
        it[Keys.ghostMode] = mode.name
    }
    suspend fun setBusinessPhone(v: String) = context.dataStore.edit { it[Keys.phone] = v.trim() }
    suspend fun setReviewUrl(v: String) = context.dataStore.edit { it[Keys.review] = v.trim() }
    suspend fun setBookingUrl(v: String) = context.dataStore.edit { it[Keys.booking] = v.trim() }
    suspend fun setWifiOnly(v: Boolean) = context.dataStore.edit { it[Keys.wifiOnly] = v }
}
