package com.greasepencil.pairshot.ui.job

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.data.db.GalleryEntity
import com.greasepencil.pairshot.data.db.SendState
import com.greasepencil.pairshot.domain.Ago
import com.greasepencil.pairshot.domain.JobStatus
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsIconButton
import com.greasepencil.pairshot.ui.components.ps.PsLoopMark
import com.greasepencil.pairshot.ui.components.ps.PsMenu
import com.greasepencil.pairshot.ui.components.ps.PsMenuItem
import com.greasepencil.pairshot.ui.components.ps.PsStatusPill
import com.greasepencil.pairshot.ui.theme.Ps

/** What the job screen knows about the client link. */
data class LinkState(
    val gallery: GalleryEntity? = null,
    /** Photos, labels or business details changed since the link was last sent. */
    val changed: Boolean = false,
    val canSend: Boolean = false,
    val now: Long = System.currentTimeMillis(),
    /** Sends wait for Wi-Fi (Settings). */
    val wifiOnly: Boolean = false,
)

/** After this long queued or sending, a battery saver may be holding the upload back. */
const val STALL_MILLIS = 10 * 60_000L

/** True when a send has sat for a while for no reason the user already sees (Wi-Fi only). */
fun looksStalled(g: GalleryEntity?, now: Long, wifiOnly: Boolean): Boolean =
    g != null && (g.sendState == SendState.SENDING || (g.sendState == SendState.QUEUED && !wifiOnly)) &&
        now - g.updatedAt >= STALL_MILLIS

/** What the link panel shows. */
enum class LinkPanelMode {
    /** No link yet: the panel is left out. */
    ABSENT,

    /** The last link was closed or expired, and nothing new is on its way. */
    CLOSED,

    /** Queued or sending. */
    WORKING,
    FAILED,
    LIVE,
}

/** True when the server says the link was closed or ran out. */
internal fun GalleryEntity.isClosed() = remoteState == "revoked" || remoteState == "expired"

fun linkPanelMode(g: GalleryEntity?): LinkPanelMode = when {
    g == null -> LinkPanelMode.ABSENT
    g.sendState == SendState.QUEUED || g.sendState == SendState.SENDING -> LinkPanelMode.WORKING
    g.sendState == SendState.FAILED -> LinkPanelMode.FAILED
    g.isClosed() -> LinkPanelMode.CLOSED
    else -> LinkPanelMode.LIVE
}

/** The line next to the spinner while a link is on its way. */
fun workingText(g: GalleryEntity, wifiOnly: Boolean): String = when {
    g.sendState == SendState.SENDING -> if (g.url == null) "Sending…" else "Updating the link…"
    wifiOnly -> "Waiting for Wi-Fi. It'll go by itself."
    else -> "Waiting for signal. It'll go by itself."
}

/** "Opened 2 times, first 3 h ago · expires in 9 days". */
fun seenLine(g: GalleryEntity, now: Long): String {
    val views = when {
        g.firstViewedAt == null -> "Not opened yet"
        g.viewCount <= 1 -> "Opened ${Ago.past(g.firstViewedAt, now)}"
        else -> "Opened ${g.viewCount} times, first ${Ago.past(g.firstViewedAt, now)}"
    }
    val expiry = g.expiresAt?.let { " · expires ${Ago.until(it, now)}" } ?: ""
    return views + expiry
}

/** Android's list where the user can let Pairshot keep running; shown only if it's being held back. */
private fun batteryLimited(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun StallTip(g: GalleryEntity, wifiOnly: Boolean) {
    val context = LocalContext.current
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { kotlinx.coroutines.delay(30_000); value = System.currentTimeMillis() }
    }
    if (!looksStalled(g, now, wifiOnly) || !batteryLimited(context)) return
    Text(
        "Taking a while? Some phones pause uploads to save battery. Letting Pairshot run in the background fixes it.",
        style = Ps.type.caption,
        color = Ps.colors.inkSoft,
    )
    PsButton(
        "Open battery settings",
        onClick = {
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .recoverCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        },
        variant = PsButtonVariant.Quiet,
        modifier = QuietEdge,
    )
}

/** Quiet buttons sit with their text on the panel's text edge (they pad 12 dp themselves). */
private val QuietEdge = Modifier.offset(x = (-12).dp)

/**
 * The client link, directly under the job's meta row whenever a link exists, is on its way or
 * failed. Send, Update and Try again live in the bottom bar, not here.
 */
@Composable
fun LinkPanel(
    s: LinkState,
    clientPhone: String?,
    onShare: () -> Unit,
    onText: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
    onSendOverMobile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = s.gallery ?: return
    val mode = linkPanelMode(g)
    if (mode == LinkPanelMode.ABSENT) return
    val c = Ps.colors
    val shape = Ps.shape.md
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Ps.space.gutter)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.hairline, shape)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (mode) {
            LinkPanelMode.CLOSED -> Text(
                if (g.remoteState == "expired") "The last link expired." else "You closed the last link.",
                style = Ps.type.bodyS,
                color = c.inkSoft,
                modifier = Modifier.padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
            )
            LinkPanelMode.WORKING -> Working(g, s.wifiOnly, onSendOverMobile)
            LinkPanelMode.FAILED -> Failed(g, onShare)
            LinkPanelMode.LIVE -> Live(g, s, clientPhone, onShare, onText, onCopy, onClose)
            LinkPanelMode.ABSENT -> Unit
        }
    }
}

@Composable
private fun Working(g: GalleryEntity, wifiOnly: Boolean, onSendOverMobile: () -> Unit) {
    val c = Ps.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(min = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), color = c.ink, strokeWidth = 2.dp, trackColor = Color.Transparent)
        Spacer(Modifier.width(12.dp))
        Text(workingText(g, wifiOnly), style = Ps.type.rowTitleS, color = c.ink, modifier = Modifier.padding(end = 8.dp))
    }
    if (g.sendState == SendState.QUEUED && g.error != null) {
        Text(g.error, style = Ps.type.caption, color = c.inkSoft, modifier = Modifier.padding(end = 8.dp))
    }
    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (g.sendState == SendState.QUEUED && wifiOnly) {
            PsButton("Send now using mobile data", onSendOverMobile, QuietEdge, variant = PsButtonVariant.Quiet)
        }
        StallTip(g, wifiOnly)
    }
}

@Composable
private fun Failed(g: GalleryEntity, onShare: () -> Unit) {
    val c = Ps.colors
    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (g.url == null) "Not sent" else "Not updated",
            style = Ps.type.rowTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            g.error ?: "Something went wrong.",
            style = Ps.type.bodyS,
            color = c.pencilText,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (g.url != null) PsButton("Share the old link", onShare, QuietEdge, variant = PsButtonVariant.Quiet)
    }
}

@Composable
private fun Live(
    g: GalleryEntity,
    s: LinkState,
    clientPhone: String?,
    onShare: () -> Unit,
    onText: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Ps.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "CLIENT LINK",
            style = Ps.type.eyebrow,
            color = c.inkFaint,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (g.firstViewedAt != null) {
            PsLoopMark { PsStatusPill(JobStatus.SEEN) }
        } else {
            PsStatusPill(JobStatus.SENT)
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            PsIconButton(Icons.Outlined.MoreVert, "Link options", { menu = true })
            PsMenu(menu, { menu = false }) {
                PsMenuItem("Close link…", { menu = false; onClose() }, icon = Icons.Outlined.LinkOff)
            }
        }
    }
    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(g.url ?: "", style = Ps.type.mono, color = c.ink, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        Text(seenLine(g, s.now), style = Ps.type.bodyS, color = c.inkSoft)
        if (s.changed) Text("You've changed things since sending.", style = Ps.type.caption, color = c.ink)
    }
    // Text / Share / Copy share the row; past font scale 1.5 they stack so no word breaks.
    @Composable
    fun Buttons(button: Modifier, fullWidth: Boolean) {
        if (clientPhone != null) {
            PsButton("Text", onText, button, variant = PsButtonVariant.Secondary, height = 48.dp, compact = true, fullWidth = fullWidth)
        }
        PsButton("Share", onShare, button, variant = PsButtonVariant.Secondary, height = 48.dp, compact = true, fullWidth = fullWidth)
        PsButton("Copy", onCopy, button, variant = PsButtonVariant.Secondary, height = 48.dp, compact = true, fullWidth = fullWidth)
    }
    if (LocalDensity.current.fontScale > 1.5f) {
        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Buttons(Modifier, fullWidth = true)
        }
    } else {
        Row(
            Modifier.padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Buttons(Modifier.weight(1f), fullWidth = false)
        }
    }
}

object LinkActions {
    fun share(context: Context, message: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
        context.startActivity(Intent.createChooser(send, "Send the link"))
    }

    fun text(context: Context, phone: String, message: String) {
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone))).putExtra("sms_body", message)
        runCatching { context.startActivity(sms) }.onFailure { share(context, message) }
    }

    fun copy(context: Context, link: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Client link", link))
    }
}
