package com.greasepencil.b4after.domain

/** The trades we know, with the areas they shoot most. Picked during onboarding. */
enum class Trade(val label: String, val defaultAreas: List<String>) {
    PRESSURE_WASHING("Pressure washing", listOf("Driveway", "Patio", "Siding", "Roof", "Deck", "Walkway", "Fence")),
    WINDOW_CLEANING("Window cleaning", listOf("Front windows", "Back windows", "Screens", "Sills", "Skylights")),
    GUTTER_CLEANING("Gutter cleaning", listOf("Front gutters", "Back gutters", "Downspouts", "Roof")),
    CAR_DETAILING("Car detailing", listOf("Exterior", "Interior", "Seats", "Wheels", "Engine bay", "Trunk")),
    CARPET_CLEANING("Carpet cleaning", listOf("Living room", "Bedroom", "Hallway", "Stairs", "Rug")),
    CLEANING("House cleaning", listOf("Kitchen", "Bathroom", "Living room", "Bedroom", "Floors")),
    LANDSCAPING("Landscaping", listOf("Front yard", "Back yard", "Beds", "Hedges", "Lawn")),
    OTHER("Something else", listOf("Front", "Back", "Inside", "Outside")),
}

object AreaPresets {
    /**
     * Chips to offer when adding an area: the names this business has typed most (more than once),
     * then the trade's defaults, without repeats (case-insensitive), at most [limit].
     */
    fun rank(trade: Trade, usedNames: List<String>, limit: Int = 10): List<String> {
        val counts = usedNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupBy { it.lowercase() }
            .mapValues { (_, v) -> v.size to v.first() }
        val learned = counts.values
            .filter { it.first >= 2 }
            .sortedWith(compareByDescending<Pair<Int, String>> { it.first }.thenBy { it.second.lowercase() })
            .map { it.second }
        val seen = HashSet<String>()
        return (learned + trade.defaultAreas)
            .filter { seen.add(it.lowercase()) }
            .take(limit)
    }
}
