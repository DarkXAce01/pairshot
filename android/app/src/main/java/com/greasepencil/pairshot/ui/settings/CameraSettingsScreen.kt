package com.greasepencil.pairshot.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.prefs.GhostMode
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsSegmented
import com.greasepencil.pairshot.ui.components.ps.PsSlider
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.theme.Ps

internal fun ghostDescription(m: GhostMode) = when (m) {
    GhostMode.OUTLINE -> "Red edges of the before. Easiest to see in bright sun."
    GhostMode.PHOTO -> "The before, see-through, over the camera."
    GhostMode.OFF -> "Nothing over the camera. Hold Peek to check the before."
}

/** Settings → Camera: how the before shows while shooting the after. */
@Composable
fun CameraSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    PsScaffold(topBar = { PsTopBar("Camera", onBack = onBack) }) {
        val s = loaded ?: return@PsScaffold
        var strength by remember { mutableFloatStateOf(s.ghostStrength) }
        LaunchedEffect(s.ghostStrength) { strength = s.ghostStrength }
        val gutter = Modifier.padding(horizontal = Ps.space.gutter)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            PsSectionHeader("When you shoot an after, show the before as")
            PsSegmented(
                GhostMode.entries.map { it.label },
                selected = s.ghostMode.ordinal,
                onSelect = { vm.setGhost(strength, GhostMode.entries[it]) },
                modifier = gutter,
            )
            Text(ghostDescription(s.ghostMode), gutter.padding(top = 8.dp), style = Ps.type.bodyS, color = Ps.colors.inkSoft)
            GhostSample(s.ghostMode, strength, gutter.padding(top = 16.dp))
            if (s.ghostMode != GhostMode.OFF) {
                Row(gutter.padding(top = 20.dp)) {
                    Text("Strength", Modifier.weight(1f), style = Ps.type.rowTitle, color = Ps.colors.ink)
                    Text("${(strength * 100).toInt()}%", style = Ps.type.mono, color = Ps.colors.inkSoft)
                }
                PsSlider(
                    value = strength,
                    onValueChange = { strength = it },
                    contentDescription = "Ghost strength",
                    modifier = gutter,
                    valueRange = 0.1f..1f,
                    onValueChangeFinished = { vm.setGhost(strength, s.ghostMode) },
                )
            }
            Text(
                "You can change this while shooting too. The camera remembers.",
                gutter.padding(top = 12.dp),
                style = Ps.type.caption,
                color = Ps.colors.inkSoft,
            )
        }
    }
}

/** A small drawing of the overlay at this strength. Decoration only. */
@Composable
private fun GhostSample(mode: GhostMode, strength: Float, modifier: Modifier) {
    val c = Ps.colors
    Box(
        modifier.width(180.dp).aspectRatio(3f / 4f).clip(Ps.shape.sm).background(c.sunken).clearAndSetSemantics {},
    ) {
        when (mode) {
            GhostMode.OUTLINE -> Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height; val sw = 2.5.dp.toPx()
                val col = c.pencil.copy(alpha = strength)
                // A roof line, a wall and a door: enough to read as "the edges of a before".
                drawLine(col, Offset(w * .15f, h * .45f), Offset(w * .5f, h * .22f), sw, StrokeCap.Round)
                drawLine(col, Offset(w * .5f, h * .22f), Offset(w * .85f, h * .45f), sw, StrokeCap.Round)
                drawLine(col, Offset(w * .22f, h * .45f), Offset(w * .22f, h * .8f), sw, StrokeCap.Round)
                drawLine(col, Offset(w * .78f, h * .45f), Offset(w * .78f, h * .8f), sw, StrokeCap.Round)
                drawLine(col, Offset(w * .1f, h * .8f), Offset(w * .9f, h * .8f), sw, StrokeCap.Round)
                drawLine(col, Offset(w * .44f, h * .8f), Offset(w * .44f, h * .6f), sw, StrokeCap.Round)
                drawLine(col, Offset(w * .44f, h * .6f), Offset(w * .56f, h * .6f), sw, StrokeCap.Round)
                drawLine(col, Offset(w * .56f, h * .6f), Offset(w * .56f, h * .8f), sw, StrokeCap.Round)
            }
            GhostMode.PHOTO -> Box(
                Modifier.fillMaxWidth().fillMaxSize().padding(18.dp).clip(Ps.shape.xs).background(c.inkSoft.copy(alpha = strength)),
            )
            GhostMode.OFF -> Unit
        }
    }
}
