package com.greasepencil.pairshot.ui.components.ps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.PsCameraColors
import com.greasepencil.pairshot.ui.theme.PsDarkColors

enum class PsButtonVariant {
    /** Ink fill. The normal main action. */
    Primary,

    /** Pencil fill. Reserved for Send link / Update link and Get Pro: one per screen at most. */
    Accent,

    /** Outlined, ink text. */
    Secondary,

    /** Text only, ink. */
    Quiet,

    /** Text only, pencil red: destructive actions. */
    Danger,
}

private data class ButtonColors(val fill: Color, val text: Color, val border: Color?)

@Composable
private fun buttonColors(variant: PsButtonVariant, onDark: Boolean): ButtonColors {
    val c = Ps.colors
    return if (onDark) {
        when (variant) {
            PsButtonVariant.Primary -> ButtonColors(PsCameraColors.ink, PsCameraColors.background, null)
            PsButtonVariant.Accent -> ButtonColors(PsCameraColors.ghost, PsDarkOnPencil, null)
            PsButtonVariant.Secondary -> ButtonColors(Color.Transparent, PsCameraColors.ink, PsCameraColors.muted)
            PsButtonVariant.Quiet -> ButtonColors(Color.Transparent, PsCameraColors.ink, null)
            PsButtonVariant.Danger -> ButtonColors(Color.Transparent, PsCameraDanger, null)
        }
    } else {
        when (variant) {
            PsButtonVariant.Primary -> ButtonColors(c.ink, c.onInk, null)
            PsButtonVariant.Accent -> ButtonColors(c.pencil, c.onPencil, null)
            PsButtonVariant.Secondary -> ButtonColors(Color.Transparent, c.ink, c.outline)
            PsButtonVariant.Quiet -> ButtonColors(Color.Transparent, c.ink, null)
            PsButtonVariant.Danger -> ButtonColors(Color.Transparent, c.pencilText, null)
        }
    }
}

// The dark theme's values, for buttons on the always-dark camera chrome.
private val PsDarkOnPencil = PsDarkColors.onPencil
private val PsCameraDanger = PsDarkColors.pencilText

/**
 * The one button. 52 dp high (Quiet and Danger 48 dp), radius md. Disabled shows at 40 %. [busy]
 * swaps the icon for a spinner and disables it. [onDark] is for the camera and crop editor. [compact]
 * narrows the side padding for three buttons sharing one row.
 */
@Composable
fun PsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PsButtonVariant = PsButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    fullWidth: Boolean = false,
    onDark: Boolean = false,
    height: Dp? = null,
    compact: Boolean = false,
) {
    val colors = buttonColors(variant, onDark)
    val quiet = variant == PsButtonVariant.Quiet || variant == PsButtonVariant.Danger
    val shape = Ps.shape.md
    val active = enabled && !busy
    Box(
        modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = height ?: if (quiet) 48.dp else 52.dp)
            .widthIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .psPressable(onClick = onClick, enabled = active, focusShape = shape)
            .clip(shape)
            .background(colors.fill, shape)
            .then(if (colors.border != null) Modifier.border(1.dp, colors.border, shape) else Modifier)
            .semantics { if (busy) stateDescription = "Busy" }
            .padding(horizontal = if (quiet || compact) 12.dp else 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), color = colors.text, strokeWidth = 2.dp, trackColor = Color.Transparent)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
            }
            Text(text, style = Ps.type.button, color = colors.text, textAlign = TextAlign.Center)
        }
    }
}

/** A bare icon in a 48 dp target. [label] is what TalkBack says, and is required. */
@Composable
fun PsIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Ps.colors.ink,
) {
    Box(
        modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .psPressable(onClick = onClick, enabled = enabled, focusShape = Ps.shape.pill)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/**
 * Jobs' "New job": an ink pill, no shadow. The caller places it 16 dp above the tab bar and 20 dp
 * from the end.
 */
@Composable
fun PsFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ps.colors
    Row(
        modifier
            .heightIn(min = 56.dp)
            .psPressable(onClick = onClick, focusShape = Ps.shape.pill)
            .clip(Ps.shape.pill)
            .background(c.ink)
            .padding(start = 18.dp, end = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.onInk, modifier = Modifier.size(24.dp))
        Text(text, style = Ps.type.button, color = c.onInk)
    }
}

@PsPreviews
@Composable
private fun PsButtonPreview() = PreviewPaper {
    val m = Modifier.padding(horizontal = 20.dp)
    PsButton("Create job", {}, m, fullWidth = true)
    PsButton("Send link", {}, m, variant = PsButtonVariant.Accent, fullWidth = true)
    PsButton("Preview", {}, m, variant = PsButtonVariant.Secondary, icon = Icons.Outlined.CameraAlt)
    PsButton("Not now", {}, m, variant = PsButtonVariant.Quiet)
    PsButton("Delete job", {}, m, variant = PsButtonVariant.Danger, icon = Icons.Outlined.Delete)
    PsButton("Sending…", {}, m, variant = PsButtonVariant.Accent, busy = true)
    PsButton("After", {}, m, variant = PsButtonVariant.Secondary, enabled = false)
}

@PsPreviews
@Composable
private fun PsIconButtonPreview() = PreviewPaper {
    Row(Modifier.padding(horizontal = 12.dp)) {
        PsIconButton(Icons.Outlined.MoreVert, "More", {})
        PsIconButton(Icons.Outlined.Delete, "Delete", {}, enabled = false)
    }
}

@PsPreviews
@Composable
private fun PsFabPreview() = PreviewPaper {
    PsFab("New job", Icons.Outlined.Add, {}, Modifier.padding(horizontal = 20.dp))
}
