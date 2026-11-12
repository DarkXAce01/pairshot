package com.greasepencil.pairshot

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.data.prefs.SettingsStore
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
        setContent {
            PairshotTheme {
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
}
