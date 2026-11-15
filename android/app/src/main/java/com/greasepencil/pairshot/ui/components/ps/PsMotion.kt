package com.greasepencil.pairshot.ui.components.ps

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.ui.theme.Ps

/** True when Android's animator duration scale is 0: show end states, skip custom animation. */
fun isReducedMotion(animatorScale: Float): Boolean = animatorScale == 0f

/** Reads the system "remove animations" setting once per composition site (spec 4.6). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        isReducedMotion(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}

/** The press scale plus the keyboard focus ring, shared by every pressable. */
@Composable
private fun Modifier.pressFeel(interaction: MutableInteractionSource, focusShape: Shape): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val reduced = rememberReducedMotion()
    val motion = Ps.motion
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) motion.pressScale else 1f,
        animationSpec = spring(dampingRatio = motion.pressDamping, stiffness = motion.pressStiffness),
        label = "pressScale",
    )
    val ring = Ps.colors.ink
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                // 2 dp ink outline, offset 2 dp outside the control (spec 4.5).
                val gap = 2.dp.toPx()
                val width = 2.dp.toPx()
                val grow = gap + width / 2
                val outline = focusShape.createOutline(
                    Size(size.width + grow * 2, size.height + grow * 2),
                    layoutDirection,
                    this,
                )
                translate(-grow, -grow) { drawOutline(outline, ring, style = Stroke(width)) }
            }
        }
}

/**
 * Swipe's press feel: the thing shrinks to 0.97 on a soft spring while held. No ripple. Keyboard
 * focus shows a 2 dp ink ring in [focusShape].
 */
@Composable
fun Modifier.psPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    focusShape: Shape = Ps.shape.md,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressFeel(interaction, focusShape)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

/** [psPressable] for one choice of several (radio buttons, tabs, segments). */
@Composable
fun Modifier.psSelectable(
    selected: Boolean,
    onClick: () -> Unit,
    role: Role = Role.RadioButton,
    enabled: Boolean = true,
    focusShape: Shape = Ps.shape.md,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressFeel(interaction, focusShape)
        .selectable(selected = selected, interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** [psPressable] for an on/off thing (switch rows, checkbox chips). */
@Composable
fun Modifier.psToggleable(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    role: Role = Role.Switch,
    enabled: Boolean = true,
    focusShape: Shape = Ps.shape.md,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressFeel(interaction, focusShape)
        .toggleable(value = value, interactionSource = interaction, indication = null, enabled = enabled, role = role, onValueChange = onValueChange)
}
