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

data class AppSettings(
    val onboarded: Boolean = false,
    val businessName: String = "",
    val trade: Trade = Trade.PRESSURE_WASHING,
    val messageTemplate: String = DEFAULT_TEMPLATE,
    val ghostOpacity: Float = 0.35f,
    val ghostEdges: Boolean = false,
    val isPro: Boolean = false,
) {
    companion object {
        const val DEFAULT_TEMPLATE = "Hi {first_name}, here's your {service} before & after: {link}"
    }
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val businessName = stringPreferencesKey("business_name")
        val trade = stringPreferencesKey("trade")
        val template = stringPreferencesKey("message_template")
        val ghostOpacity = floatPreferencesKey("ghost_opacity")
        val ghostEdges = booleanPreferencesKey("ghost_edges")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboarded = p[Keys.onboarded] ?: false,
            businessName = p[Keys.businessName] ?: "",
            trade = p[Keys.trade]?.let { runCatching { Trade.valueOf(it) }.getOrNull() } ?: Trade.PRESSURE_WASHING,
            messageTemplate = p[Keys.template] ?: AppSettings.DEFAULT_TEMPLATE,
            ghostOpacity = p[Keys.ghostOpacity] ?: 0.35f,
            ghostEdges = p[Keys.ghostEdges] ?: false,
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
    suspend fun setGhost(opacity: Float, edges: Boolean) = context.dataStore.edit {
        it[Keys.ghostOpacity] = opacity.coerceIn(0.1f, 0.9f)
        it[Keys.ghostEdges] = edges
    }
}
