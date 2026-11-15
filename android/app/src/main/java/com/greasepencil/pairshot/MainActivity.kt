package com.greasepencil.pairshot

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.data.prefs.ThemeMode
import com.greasepencil.pairshot.ui.camera.VolumeKeys
import com.greasepencil.pairshot.ui.nav.AppNav
import com.greasepencil.pairshot.ui.theme.PairshotTheme
import com.greasepencil.pairshot.work.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore

    /** A job to open, from tapping a "link ready" notification. */
    private val openJob = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openJob.value = intent?.getStringExtra(Notifier.EXTRA_JOB)
        // null until DataStore has answered, so we never flash onboarding at someone who's done it.
        val onboarded = settingsStore.settings.map<AppSettings, Boolean?> { it.onboarded }
        val themeMode = settingsStore.settings.map { it.themeMode }
        setContent {
            val mode by themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dark = mode.isDark(isSystemInDarkTheme())
            // Status and navigation bar icons follow the app's theme, not the phone's, when forced.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            PairshotTheme(dark = dark) {
                val ready by onboarded.collectAsStateWithLifecycle(initialValue = null)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ready?.let {
                        AppNav(onboarded = it, openJob = openJob.value, onOpenedJob = { openJob.value = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_JOB)?.let { openJob.value = it }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (volume && VolumeKeys.cameraOpen) {
            if (event?.repeatCount == 0) VolumeKeys.press()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        // The same scrims enableEdgeToEdge() uses by default (for three-button navigation).
        val LIGHT_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
