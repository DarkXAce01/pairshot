package com.greasepencil.pairshot.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.data.db.JobSummary
import com.greasepencil.pairshot.domain.JobStatus
import com.greasepencil.pairshot.ui.components.ps.PsPhotoThumb
import com.greasepencil.pairshot.ui.components.ps.PsStatusPill
import com.greasepencil.pairshot.ui.components.ps.psPressable
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.tabular

/** "service · address", or null when the job has neither. */
internal fun jobSubtitle(s: JobSummary): String? =
    listOfNotNull(s.job.service?.trim(), s.job.address?.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
        .ifEmpty { null }

/** The progress line, shown only while the job is in progress ("2 OF 5 AFTERS"). */
internal fun jobProgressLine(s: JobSummary): String? =
    if (statusOf(s) == JobStatus.IN_PROGRESS) progressText(s) else null

/** What TalkBack says for a row: "Dana Whitfield, House wash, 12 Oak St, status In progress, 2 of 5 afters". */
internal fun jobRowDescription(s: JobSummary): String = buildList {
    add(s.job.clientName)
    s.job.service?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
    s.job.address?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
    add("status ${statusOf(s).label}")
    jobProgressLine(s)?.let { add(it.lowercase()) }
}.joinToString(", ")

/** One job in a Jobs list block: cover thumbnail, client, service and address, status pill. */
@Composable
fun JobRow(summary: JobSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ps.colors
    val spoken = jobRowDescription(summary)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .psPressable(onClick = onClick, focusShape = Ps.shape.xs)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = Ps.space.gutter, vertical = Ps.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PsPhotoThumb(summary.coverFile, summary.coverRotation ?: 0, 56.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                summary.job.clientName,
                style = Ps.type.rowTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            jobSubtitle(summary)?.let {
                Text(it, style = Ps.type.bodyS, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            jobProgressLine(summary)?.let {
                Text(it, style = Ps.type.eyebrow.tabular(), color = c.inkFaint, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        PsStatusPill(statusOf(summary))
    }
}
