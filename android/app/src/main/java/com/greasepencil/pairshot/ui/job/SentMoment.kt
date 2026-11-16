package com.greasepencil.pairshot.ui.job

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.brand.greasePencilLoop
import com.greasepencil.pairshot.ui.components.PhotoImage
import com.greasepencil.pairshot.ui.components.ps.rememberReducedMotion
import com.greasepencil.pairshot.ui.theme.Ps
import kotlinx.coroutines.delay

/** The first time a link goes out: the cover photo, the red loop drawn round it, "Sent." */
data class Moment(val coverFile: String?, val coverRotation: Int, val message: String)

/** How long the moment stays once drawn, before the share sheet opens. */
private const val HOLD_MILLIS = 700L

/**
 * The brand's loop moment (PLAN.md §11). Tapping anywhere skips it. With animations turned off in
 * Android's settings the loop is shown already drawn.
 */
@Composable
fun SentMoment(moment: Moment, onDone: () -> Unit) {
    val still = rememberReducedMotion()
    val done by rememberUpdatedState(onDone)
    val draw = remember { Animatable(if (still) 1f else 0f) }
    val word = remember { Animatable(if (still) 1f else 0f) }
    val motion = Ps.motion
    LaunchedEffect(moment) {
        if (!still) {
            draw.animateTo(1f, tween(motion.loop, easing = motion.easing))
            word.animateTo(1f, tween(motion.standard))
        }
        delay(HOLD_MILLIS)
        done()
    }
    val c = Ps.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.paper.copy(alpha = 0.96f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() }
            .clearAndSetSemantics {
                contentDescription = "Sent. Your client link is ready."
                liveRegion = LiveRegionMode.Assertive
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(300.dp, 250.dp).greasePencilLoop(c.pencil, { draw.value }, strokeWidth = 0.035f)) {}
                val photo = Modifier.size(200.dp, 150.dp).clip(Ps.shape.md)
                if (moment.coverFile != null) PhotoImage(moment.coverFile, moment.coverRotation, photo)
                else Box(photo.background(c.sunken))
            }
            Spacer(Modifier.height(Ps.space.xl))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = word.value; translationY = (1f - word.value) * 24.dp.toPx() },
            ) {
                Text("Sent.", style = Ps.type.display, color = c.ink)
                Spacer(Modifier.height(8.dp))
                Text("Pick how to send it.", style = Ps.type.caption, color = c.inkSoft)
            }
        }
    }
}
