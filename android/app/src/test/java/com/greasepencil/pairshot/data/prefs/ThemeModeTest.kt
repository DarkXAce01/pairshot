package com.greasepencil.pairshot.data.prefs

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.ui.components.ps.isReducedMotion
import org.junit.Test

class ThemeModeTest {
    @Test fun systemFollowsThePhone() {
        assertThat(ThemeMode.SYSTEM.isDark(systemDark = true)).isTrue()
        assertThat(ThemeMode.SYSTEM.isDark(systemDark = false)).isFalse()
    }

    @Test fun forcedModesIgnoreThePhone() {
        assertThat(ThemeMode.LIGHT.isDark(systemDark = true)).isFalse()
        assertThat(ThemeMode.DARK.isDark(systemDark = false)).isTrue()
    }

    @Test fun storedValuesRoundTripAndUnknownMeansSystem() {
        ThemeMode.entries.forEach { assertThat(ThemeMode.fromStored(it.name)).isEqualTo(it) }
        assertThat(ThemeMode.fromStored(null)).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromStored("SEPIA")).isEqualTo(ThemeMode.SYSTEM)
        assertThat(AppSettings().themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun reducedMotionOnlyWhenAnimationsAreOff() {
        assertThat(isReducedMotion(0f)).isTrue()
        assertThat(isReducedMotion(1f)).isFalse()
        assertThat(isReducedMotion(0.5f)).isFalse()
    }
}
