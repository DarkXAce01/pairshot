package com.greasepencil.pairshot.domain

import com.greasepencil.pairshot.domain.gallery.BrandLook
import com.greasepencil.pairshot.domain.gallery.ClientTemplate

/**
 * The look of a share image, picked from the business's page style and colour (spec 8b). Pure
 * palette and metrics; colours are upper-case #RRGGBB (or #AARRGGBB for the muted mark).
 */
data class CollageFrame(
    val template: ClientTemplate,
    val background: String,
    /** How the business name is set. */
    val nameFont: NameFont,
    val nameColour: String,
    /** Letter spacing for the name, in em. */
    val nameTracking: Float,
    /** The business colour, or the frame's own ink when none. */
    val accent: String,
    /** Height of the accent rule above the footer at a 2048 long edge; 0 for a filled band. */
    val ruleAt2048: Int,
    /** Bright: the header and footer bands are filled with the accent. */
    val bandsFilled: Boolean,
    /** "MADE WITH PAIRSHOT". */
    val markColour: String,
) {
    enum class NameFont { SERIF, SANS_CAPS, MONO_CAPS }

    /** The rule height at [longEdge] pixels; at least one pixel when there is a rule. */
    fun rule(longEdge: Int): Int = if (ruleAt2048 == 0) 0 else maxOf(1, ruleAt2048 * longEdge / 2048)

    /** The business name as this frame sets it; Report adds the pair's label. */
    fun nameText(businessName: String, label: String?): String {
        val name = businessName.trim()
        if (name.isEmpty()) return ""
        return when (nameFont) {
            NameFont.SERIF -> name
            NameFont.SANS_CAPS -> name.uppercase()
            NameFont.MONO_CAPS -> listOfNotNull(name, label?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ").uppercase()
        }
    }

    companion object {
        fun of(template: ClientTemplate, accent: String?): CollageFrame {
            val picked = BrandLook.normalise(accent)
            // A rule that would vanish into the background falls back to the frame's ink.
            fun ruleAccent(bg: String, ink: String) = picked?.takeIf { BrandLook.contrast(it, bg) >= 1.5 } ?: ink
            return when (template) {
                ClientTemplate.CLASSIC -> CollageFrame(
                    template, "#F3F0EA", NameFont.SERIF, "#1B1A18", 0f, ruleAccent("#F3F0EA", "#1B1A18"), 6, false, "#6B665E",
                )
                ClientTemplate.SHOWROOM -> CollageFrame(
                    template, "#0E0D0C", NameFont.SANS_CAPS, "#F2EEE7", 0.12f, ruleAccent("#0E0D0C", "#F2EEE7"), 6, false, "#A39E95",
                )
                ClientTemplate.BRIGHT -> {
                    val fill = picked ?: "#111111"
                    val on = BrandLook.onAccent(fill)
                    // The mark is the on-accent colour, softened.
                    val mark = "#C8" + on.removePrefix("#")
                    CollageFrame(template, "#FFFFFF", NameFont.SANS_CAPS, on, 0.06f, fill, 0, true, mark)
                }
                ClientTemplate.REPORT -> CollageFrame(
                    template, "#FFFFFF", NameFont.MONO_CAPS, "#111111", 0.08f, ruleAccent("#FFFFFF", "#111111"), 3, false, "#6B6B6B",
                )
            }
        }

        /** Tags over photos are the same in every frame: white on 55 % black. */
        const val TAG_FILL = "#8C000000"
        const val TAG_TEXT = "#FFFFFF"

        /**
         * The text size that fits [textWidthAtOne] (the text's width at size 1) into [maxWidth],
         * never above [preferred]; null when even [min] won't fit (the caller then shortens it).
         */
        fun fitSize(textWidthAtOne: Float, maxWidth: Float, preferred: Float, min: Float): Float? {
            if (textWidthAtOne <= 0f) return preferred
            val fit = maxWidth / textWidthAtOne
            return when {
                fit >= preferred -> preferred
                fit >= min -> fit
                else -> null
            }
        }
    }
}
