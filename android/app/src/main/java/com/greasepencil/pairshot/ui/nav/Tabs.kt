package com.greasepencil.pairshot.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.greasepencil.pairshot.ui.components.ps.PsTab
import kotlin.reflect.KClass

/** The three bottom tabs. The tab bar shows only on their root screens. */
enum class AppTab(val label: String, val route: Any, private val routeClass: KClass<*>) {
    JOBS("Jobs", HomeRoute, HomeRoute::class),
    BRAND("Brand", BrandRoute, BrandRoute::class),
    SETTINGS("Settings", SettingsRoute, SettingsRoute::class),
    ;

    val psTab: PsTab
        get() = PsTab(
            label,
            when (this) {
                JOBS -> Icons.Outlined.WorkOutline
                BRAND -> Icons.Outlined.Brush
                SETTINGS -> Icons.Outlined.Settings
            },
        )

    companion object {
        val psTabs: List<PsTab> get() = entries.map { it.psTab }

        /** The tab whose root is [destination], or null on any pushed screen. */
        fun of(destination: NavDestination?): AppTab? =
            destination?.let { d -> entries.firstOrNull { d.hasRoute(it.routeClass) } }
    }
}

/**
 * Switch tabs: back to Jobs' level (keeping the tab left behind), one copy of each, and the tab
 * comes back as it was. System back on Brand or Settings then returns to Jobs.
 */
fun NavController.selectTab(tab: AppTab) {
    navigate(tab.route) {
        popUpTo(HomeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
