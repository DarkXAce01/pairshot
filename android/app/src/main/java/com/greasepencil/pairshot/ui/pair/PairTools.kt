package com.greasepencil.pairshot.ui.pair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.ui.components.ps.psPressable
import com.greasepencil.pairshot.ui.components.ps.psToggleable
import com.greasepencil.pairshot.ui.theme.Ps

/** The words under each tool, by the pair's state. Pure, so the copy is tested. */
object PairToolText {
    fun crop(cropped: Boolean) = if (cropped) "Cropped" else "Crop"
    fun cover(cover: Boolean) = if (cover) "Cover" else "Make cover"
    fun shown(hidden: Boolean) = if (hidden) "Hidden" else "Shown"
    fun coverState(cover: Boolean) = if (cover) "Cover photo" else "Not the cover"
    fun shownState(hidden: Boolean) = if (hidden) "Hidden from client" else "Shown to client"
}

/**
 * The Pair screen's four tools in equal columns: Label, Crop, Cover (toggle), Shown/Hidden (toggle).
 */
@Composable
fun PairTools(
    cropped: Boolean,
    cover: Boolean,
    hidden: Boolean,
    onLabel: () -> Unit,
    onCrop: () -> Unit,
    onCover: () -> Unit,
    onHidden: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ps.colors
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tool(Icons.AutoMirrored.Outlined.Label, "Label", c.ink, Modifier.weight(1f).psPressable(onLabel, focusShape = Ps.shape.md))
        Tool(Icons.Outlined.Crop, PairToolText.crop(cropped), c.ink, Modifier.weight(1f).psPressable(onCrop, focusShape = Ps.shape.md))
        Tool(
            if (cover) Icons.Filled.Star else Icons.Outlined.StarOutline,
            PairToolText.cover(cover),
            if (cover) c.ink else c.inkSoft,
            Modifier
                .weight(1f)
                .psToggleable(cover, { onCover() }, Role.Switch, focusShape = Ps.shape.md)
                .semantics { stateDescription = PairToolText.coverState(cover) },
        )
        Tool(
            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            PairToolText.shown(hidden),
            if (hidden) c.inkSoft else c.ink,
            Modifier
                .weight(1f)
                .psToggleable(!hidden, { shown -> onHidden(!shown) }, Role.Switch, focusShape = Ps.shape.md)
                .semantics { stateDescription = PairToolText.shownState(hidden) },
        )
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, tint: Color, modifier: Modifier) {
    Column(
        modifier.heightIn(min = 64.dp).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = Ps.type.chip, color = tint, textAlign = TextAlign.Center)
    }
}
