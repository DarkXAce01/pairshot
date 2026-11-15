package com.greasepencil.pairshot.ui.job

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greasepencil.pairshot.data.db.SendState
import com.greasepencil.pairshot.ui.components.ps.PsBottomBar
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant

/** The money action on the job's bottom bar, next to Preview. */
enum class JobBarAction(val label: String, val accent: Boolean, val busy: Boolean = false) {
    SEND("Send link", accent = true),
    SEND_NEW("Send a new link", accent = true),
    SENDING("Sending…", accent = true, busy = true),
    UPDATE("Update link", accent = true),
    TRY_AGAIN("Try again", accent = false),
}

/** What the bar shows: Preview, plus [action] beside it (null = Preview alone, full width). */
data class JobBar(val action: JobBarAction?)

/**
 * The job's bottom bar for a link state (spec 7.3), or null when there is no finished pair to show
 * and nothing on its way.
 */
fun jobBar(s: LinkState): JobBar? {
    val g = s.gallery
    return when {
        g != null && (g.sendState == SendState.QUEUED || g.sendState == SendState.SENDING) -> JobBar(JobBarAction.SENDING)
        !s.canSend -> null
        g == null -> JobBar(JobBarAction.SEND)
        g.sendState == SendState.FAILED -> JobBar(JobBarAction.TRY_AGAIN)
        g.isClosed() -> JobBar(JobBarAction.SEND_NEW)
        s.changed -> JobBar(JobBarAction.UPDATE)
        else -> JobBar(null)
    }
}

/** Secondary "Preview" and, when there is one, the send action: Accent, or ink for Try again. */
@Composable
fun JobBottomBar(bar: JobBar, onPreview: () -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    PsBottomBar(modifier) {
        PsButton("Preview", onPreview, Modifier.weight(1f), variant = PsButtonVariant.Secondary)
        bar.action?.let { a ->
            PsButton(
                a.label,
                onSend,
                Modifier.weight(1f),
                variant = if (a.accent) PsButtonVariant.Accent else PsButtonVariant.Primary,
                busy = a.busy,
            )
        }
    }
}
