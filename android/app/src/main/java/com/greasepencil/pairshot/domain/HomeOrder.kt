package com.greasepencil.pairshot.domain

import com.greasepencil.pairshot.data.db.JobSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How Home groups jobs: today's first (by time), then everything else by most recent activity. */
object HomeOrder {
    data class Sections(val today: List<JobSummary>, val recent: List<JobSummary>)

    fun sections(jobs: List<JobSummary>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Sections {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun isToday(ms: Long?) = ms != null && Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == today
        val (todays, others) = jobs.partition { isToday(it.job.scheduledAt) }
        return Sections(
            today = todays.sortedBy { it.job.scheduledAt },
            recent = others.sortedByDescending { maxOf(it.job.updatedAt, it.lastShotAt ?: 0L) },
        )
    }

    fun matches(job: JobSummary, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOfNotNull(job.job.clientName, job.job.address, job.job.service)
            .any { it.contains(q, ignoreCase = true) }
    }
}
