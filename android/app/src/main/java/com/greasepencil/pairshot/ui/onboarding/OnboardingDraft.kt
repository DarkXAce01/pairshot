package com.greasepencil.pairshot.ui.onboarding

import com.greasepencil.pairshot.domain.Trade
import com.greasepencil.pairshot.domain.gallery.BrandLook
import com.greasepencil.pairshot.domain.gallery.ClientTemplate

/**
 * What the first run has gathered so far. Picking a trade applies its colour and page style
 * (spec 8c) unless the user has already chosen that one themselves.
 */
data class OnboardingDraft(
    val step: Int = 1,
    val name: String = "",
    val trade: Trade = Trade.PRESSURE_WASHING,
    val accent: String = BrandLook.defaultsFor(trade).first,
    val template: ClientTemplate = BrandLook.defaultsFor(trade).second,
    val colourTouched: Boolean = false,
    val styleTouched: Boolean = false,
) {
    fun withName(name: String) = copy(name = name)

    fun withTrade(trade: Trade): OnboardingDraft {
        val (colour, style) = BrandLook.defaultsFor(trade)
        return copy(
            trade = trade,
            accent = if (colourTouched) accent else colour,
            template = if (styleTouched) template else style,
        )
    }

    fun withAccent(hex: String) = copy(accent = BrandLook.normalise(hex) ?: accent, colourTouched = true)
    fun withTemplate(t: ClientTemplate) = copy(template = t, styleTouched = true)

    fun next() = copy(step = 2)
    fun back() = copy(step = 1)

    /** What Start saves: the look as shown. */
    fun started(): Look = Look(name, trade, accent, template)

    /** What Skip saves: the trade's own look, whatever was tapped. */
    fun skipped(): Look = BrandLook.defaultsFor(trade).let { (colour, style) -> Look(name, trade, colour, style) }

    data class Look(val name: String, val trade: Trade, val accent: String, val template: ClientTemplate)
}
