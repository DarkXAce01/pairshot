package com.greasepencil.pairshot.ui.job

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.data.db.GalleryEntity
import com.greasepencil.pairshot.data.db.SendState
import com.greasepencil.pairshot.domain.Ago

/** What the job screen knows about the client link. */
data class LinkState(
    val gallery: GalleryEntity? = null,
    /** Photos, labels or business details changed since the link was last sent. */
    val changed: Boolean = false,
    val canSend: Boolean = false,
    val now: Long = System.currentTimeMillis(),
)

@Composable
fun LinkCard(
    s: LinkState,
    clientPhone: String?,
    onSend: () -> Unit,
    onShare: () -> Unit,
    onText: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
) {
    val g = s.gallery
    val closed = g?.remoteState == "revoked" || g?.remoteState == "expired"
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                g == null || (closed && g.sendState == SendState.SENT) -> {
                    Text("Client link", style = MaterialTheme.typography.titleMedium)
                    if (closed) {
                        Text(
                            if (g!!.remoteState == "expired") "The last link expired." else "You closed the last link.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (s.canSend) "A page with these before & afters, made for your client. Their name isn't on it."
                        else "Finish a before & after to send a link.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onSend, enabled = s.canSend, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Send, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (closed) "Send a new link" else "Send link to client")
                    }
                }
                g.sendState == SendState.QUEUED || g.sendState == SendState.SENDING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when {
                                g.sendState == SendState.SENDING -> if (g.url == null) "Sending…" else "Updating the link…"
                                else -> "Waiting for signal. It'll go by itself."
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    if (g.sendState == SendState.QUEUED && g.error != null) {
                        Text(g.error, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                g.sendState == SendState.FAILED -> {
                    Text(if (g.url == null) "Not sent" else "Not updated", style = MaterialTheme.typography.titleMedium)
                    Text(g.error ?: "Something went wrong.", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSend, enabled = s.canSend) { Text("Try again") }
                        if (g.url != null) OutlinedButton(onClick = onShare) { Text("Share the old link") }
                    }
                }
                else -> {
                    Text("Client link", style = MaterialTheme.typography.titleMedium)
                    Text(g.url ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                    Text(seen(g, s.now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (clientPhone != null) {
                            Button(onClick = onText, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Sms, null); Spacer(Modifier.width(6.dp)); Text("Text")
                            }
                        }
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text("Share")
                        }
                        OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy")
                        }
                    }
                    if (s.changed) {
                        Text("You've changed things since sending.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onSend, enabled = s.canSend, modifier = Modifier.fillMaxWidth()) { Text("Update the link") }
                    }
                    TextButton(onClick = onClose) { Text("Close link", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

private fun seen(g: GalleryEntity, now: Long): String {
    val views = when {
        g.firstViewedAt == null -> "Not opened yet"
        g.viewCount <= 1 -> "Opened ${Ago.past(g.firstViewedAt, now)}"
        else -> "Opened ${g.viewCount} times, first ${Ago.past(g.firstViewedAt, now)}"
    }
    val expiry = g.expiresAt?.let { " · expires ${Ago.until(it, now)}" } ?: ""
    return views + expiry
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
