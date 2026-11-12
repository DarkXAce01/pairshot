package com.greasepencil.pairshot.domain

/** Fills the business's message template: "Hi {first_name}, here's your {service} before & after: {link}". */
object ClientMessage {
    fun fill(template: String, clientName: String, service: String?, link: String): String {
        val first = clientName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifEmpty { "there" }
        val t = template.ifBlank { "Hi {first_name}, here's your {service} before & after: {link}" }
        val filled = t
            .replace("{first_name}", first)
            .replace("{name}", clientName.trim().ifEmpty { first })
            .replace("{service}", service?.trim()?.ifEmpty { null }?.lowercase() ?: "job")
            .replace("{link}", link)
        // A template without {link} still has to carry the link.
        return if ("{link}" in t) filled else "$filled $link".trim()
    }
}

/** "3 hours ago", "in 12 days": short and plain. */
object Ago {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    fun past(then: Long, now: Long): String {
        val d = (now - then).coerceAtLeast(0)
        return when {
            d < MIN -> "just now"
            d < HOUR -> plural(d / MIN, "minute") + " ago"
            d < DAY -> plural(d / HOUR, "hour") + " ago"
            else -> plural(d / DAY, "day") + " ago"
        }
    }

    fun until(then: Long, now: Long): String {
        val d = then - now
        return when {
            d <= 0 -> "now"
            d < HOUR -> "in " + plural((d + MIN - 1) / MIN, "minute")
            d < DAY -> "in " + plural((d + HOUR - 1) / HOUR, "hour")
            else -> "in " + plural((d + DAY - 1) / DAY, "day")
        }
    }

    private fun plural(n: Long, word: String) = "$n $word" + if (n == 1L) "" else "s"
}
