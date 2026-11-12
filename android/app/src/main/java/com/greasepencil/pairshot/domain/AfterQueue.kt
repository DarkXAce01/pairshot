package com.greasepencil.pairshot.domain

import com.greasepencil.pairshot.data.db.PairRow

/**
 * The order the After camera walks: the order the befores were shot in, labelled or not, so a crew
 * walking the site the same way never has to hunt. Hidden pairs are skipped.
 */
object AfterQueue {
    private val shotOrder = compareBy<PairRow>({ it.pair.createdAt }, { it.pair.id })

    fun pending(pairs: List<PairRow>): List<PairRow> =
        pairs.filter { it.needsAfter && !it.pair.hidden }.sortedWith(shotOrder)

    /**
     * Which pair to show next. Prefers [preferredId] when it still needs an after, otherwise the first
     * pending one shot after [justFinishedId], wrapping round to earlier ones that were skipped.
     */
    fun next(pairs: List<PairRow>, preferredId: String? = null, justFinishedId: String? = null): PairRow? {
        val queue = pending(pairs)
        if (queue.isEmpty()) return null
        queue.firstOrNull { it.pair.id == preferredId }?.let { return it }
        if (justFinishedId != null) {
            val ordered = pairs.sortedWith(shotOrder)
            val idx = ordered.indexOfFirst { it.pair.id == justFinishedId }
            if (idx >= 0) {
                val rest = ordered.drop(idx + 1) + ordered.take(idx + 1)
                rest.firstOrNull { it.needsAfter && !it.pair.hidden }?.let { return it }
            }
        }
        return queue.first()
    }
}
