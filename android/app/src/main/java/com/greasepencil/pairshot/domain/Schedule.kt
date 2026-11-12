package com.greasepencil.pairshot.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The simple "when" choice on the job form. CRM jobs bring their exact time instead. */
object Schedule {
    enum class Day(val label: String) { TODAY("Today"), TOMORROW("Tomorrow"), NONE("No date") }

    fun startOf(day: Day, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (day) {
            Day.TODAY -> today.atStartOfDay(zone).toInstant().toEpochMilli()
            Day.TOMORROW -> today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Day.NONE -> null
        }
    }

    fun dayOf(scheduledAt: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Day {
        if (scheduledAt == null) return Day.NONE
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val d = Instant.ofEpochMilli(scheduledAt).atZone(zone).toLocalDate()
        return when (d) {
            today -> Day.TODAY
            today.plusDays(1) -> Day.TOMORROW
            else -> Day.NONE
        }
    }
}
