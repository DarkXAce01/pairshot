package com.greasepencil.pairshot.ui.pair

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.psSheetBleed
import com.greasepencil.pairshot.ui.theme.Ps

/** One line of the More sheet. */
enum class MoreItem(val label: String) {
    EARLIER("Move earlier"),
    LATER("Move later"),
    RE_PAIR("Wrong after? Re-pair"),
    SWAP("Swap before and after"),
    PICK_AFTER("Pick the after from your gallery"),
    DELETE("Delete pair…"),
}

/** What the More sheet offers, in order, and which of those can be used right now. */
data class MoreMenu(val items: List<MoreItem>, val disabled: Set<MoreItem>)

/**
 * The More sheet for a pair at [index] among [siblings] pairs of its label, in a job of [jobPairs]
 * pairs. A pair still waiting for its after can't be swapped, and picks its after from the screen
 * itself rather than here.
 */
fun moreMenu(siblings: Int, index: Int, jobPairs: Int, hasAfter: Boolean): MoreMenu {
    val items = buildList {
        if (siblings > 1) { add(MoreItem.EARLIER); add(MoreItem.LATER) }
        if (jobPairs > 1) add(MoreItem.RE_PAIR)
        if (hasAfter) { add(MoreItem.SWAP); add(MoreItem.PICK_AFTER) }
        add(MoreItem.DELETE)
    }
    val disabled = buildSet {
        if (index <= 0) add(MoreItem.EARLIER)
        if (index !in 0 until siblings - 1) add(MoreItem.LATER)
    }
    return MoreMenu(items, disabled intersect items.toSet())
}

private val MoreItem.icon: ImageVector
    get() = when (this) {
        MoreItem.EARLIER -> Icons.Outlined.ArrowUpward
        MoreItem.LATER -> Icons.Outlined.ArrowDownward
        MoreItem.RE_PAIR -> Icons.AutoMirrored.Outlined.CompareArrows
        MoreItem.SWAP -> Icons.Outlined.SwapHoriz
        MoreItem.PICK_AFTER -> Icons.Outlined.PhotoLibrary
        MoreItem.DELETE -> Icons.Outlined.Delete
    }

/** The rarer pair fixes, one row each; "Delete pair…" last, under a hairline, in pencilText. */
@Composable
fun MoreSheet(menu: MoreMenu, onPick: (MoreItem) -> Unit, onDismiss: () -> Unit) {
    val c = Ps.colors
    PsSheet("More", onDismiss = onDismiss) {
        Column(Modifier.psSheetBleed()) {
            menu.items.forEach { item ->
                val delete = item == MoreItem.DELETE
                if (delete && menu.items.size > 1) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = c.hairline)
                }
                PsListRow(
                    item.label,
                    leading = { Icon(item.icon, contentDescription = null, tint = if (delete) c.pencilText else c.inkSoft) },
                    titleColor = if (delete) c.pencilText else c.ink,
                    showChevron = false,
                    enabled = item !in menu.disabled,
                    onClick = { onPick(item) },
                )
            }
        }
    }
}
