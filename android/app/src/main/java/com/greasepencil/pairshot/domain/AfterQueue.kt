package com.greasepencil.pairshot.domain

import com.greasepencil.pairshot.data.db.PairRow

/**
 * The order the After camera walks: the same order the befores were shot in (area by area, pair by
 * pair), so a crew walking the site the same way never has to hunt. Hidden pairs are skipped.
 */
object AfterQueue {
    fun pending(pairs: List<PairRow>): List<PairRow> =
        pairs
            .filter { it.needsAfter && !it.pair.hidden }
            .sortedWith(compareBy({ it.areaPosition }, { it.pair.position }))

    /**
     * Which pair to show next. Prefers [preferredId] when it still needs an after (opening After from a
     * specific pair), otherwise the first pending one after the one just finished, wrapping round.
     */
    fun next(pairs: List<PairRow>, preferredId: String? = null, justFinishedId: String? = null): PairRow? {
        val queue = pending(pairs)
        if (queue.isEmpty()) return null
        queue.firstOrNull { it.pair.id == preferredId }?.let { return it }
        if (justFinishedId != null) {
            val ordered = pairs.sortedWith(compareBy({ it.areaPosition }, { it.pair.position }))
            val idx = ordered.indexOfFirst { it.pair.id == justFinishedId }
            if (idx >= 0) {
                val after = ordered.drop(idx + 1) + ordered.take(idx + 1)
                after.firstOrNull { it.needsAfter && !it.pair.hidden }?.let { return it }
            }
        }
        return queue.first()
    }
}
