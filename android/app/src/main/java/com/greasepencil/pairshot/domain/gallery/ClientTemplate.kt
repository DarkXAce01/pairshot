package com.greasepencil.pairshot.domain.gallery

import com.greasepencil.pairshot.domain.Trade

/** The four looks of the client page (shared/gallery/gallery.html). [id] is what the manifest carries. */
enum class ClientTemplate(val id: String, val label: String, val blurb: String) {
    CLASSIC("classic", "Classic", "Warm paper and a serif. Suits most work."),
    SHOWROOM("showroom", "Showroom", "Dark and glossy. Makes a finish shine."),
    BRIGHT("bright", "Bright", "White and crisp. Clean work, cleanly shown."),
    REPORT("report", "Report", "Side by side, like a record of the job."),
    ;

    companion object {
        /** A stored or sent id back to a template; null when it's missing or unknown. */
        fun fromId(id: String?): ClientTemplate? = entries.firstOrNull { it.id == id }
    }
}

/** One of the curated accent colours. [hex] is upper-case #RRGGBB. */
data class BrandColour(val name: String, val hex: String)

/** The colour swatches and the zero-setup look for each trade. */
object BrandLook {
    val INK = BrandColour("Ink", "#1C1B19")
    val SLATE = BrandColour("Slate", "#475361")
    val NAVY = BrandColour("Navy", "#1E3A5F")
    val BLUE = BrandColour("Blue", "#1F5FBF")
    val TEAL = BrandColour("Teal", "#0F6F68")
    val GREEN = BrandColour("Green", "#2F6B3A")
    val PLUM = BrandColour("Plum", "#6A2C5B")
    val RUST = BrandColour("Rust", "#A8431C")
    val RED = BrandColour("Red", "#B8321F")

    /** In the order they're shown. */
    val colours: List<BrandColour> = listOf(INK, SLATE, NAVY, BLUE, TEAL, GREEN, PLUM, RUST, RED)

    /** The page's own red when the business hasn't picked a colour (Classic's fallback). */
    const val CLASSIC_FALLBACK = "#D4432A"

    /** Colour hex and page style that look right for [trade] with no setup. */
    fun defaultsFor(trade: Trade): Pair<String, ClientTemplate> = when (trade) {
        Trade.PRESSURE_WASHING -> NAVY.hex to ClientTemplate.CLASSIC
        Trade.WINDOW_CLEANING -> BLUE.hex to ClientTemplate.BRIGHT
        Trade.GUTTER_CLEANING -> SLATE.hex to ClientTemplate.REPORT
        Trade.CAR_DETAILING -> INK.hex to ClientTemplate.SHOWROOM
        Trade.CARPET_CLEANING -> PLUM.hex to ClientTemplate.BRIGHT
        Trade.CLEANING -> TEAL.hex to ClientTemplate.BRIGHT
        Trade.LANDSCAPING -> GREEN.hex to ClientTemplate.CLASSIC
        Trade.OTHER -> INK.hex to ClientTemplate.CLASSIC
    }

    /** "#1f5fbf", "1F5FBF" → "#1F5FBF"; anything that isn't six hex digits → null. */
    fun normalise(hex: String?): String? {
        val h = hex?.trim()?.removePrefix("#") ?: return null
        return if (h.length == 6 && h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) "#" + h.uppercase() else null
    }

    /** The swatch with this colour, if it's one of ours. */
    fun named(hex: String?): BrandColour? = normalise(hex)?.let { n -> colours.firstOrNull { it.hex == n } }

    /** WCAG relative luminance of #RRGGBB. */
    fun luminance(hex: String): Double {
        val n = normalise(hex) ?: return 0.0
        fun ch(i: Int): Double {
            val c = n.substring(i, i + 2).toInt(16) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(1) + 0.7152 * ch(3) + 0.0722 * ch(5)
    }

    /** WCAG contrast ratio between two colours. */
    fun contrast(a: String, b: String): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Text on an accent fill: white or near-black, whichever reads better (same rule as the page). */
    fun onAccent(accent: String): String = if (contrast(accent, "#FFFFFF") >= contrast(accent, "#111111")) "#FFFFFF" else "#111111"
}
