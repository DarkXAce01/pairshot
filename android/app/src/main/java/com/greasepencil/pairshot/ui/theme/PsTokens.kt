package com.greasepencil.pairshot.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.brand.GreasePencil

/** The 4-point spacing scale and the layout measures built on it (spec 4.3). */
@Immutable
data class PsSpace(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 40.dp,
    val huge: Dp = 56.dp,
    /** Screen side gutter. */
    val gutter: Dp = 20.dp,
    /** Sheet side gutter. */
    val sheetGutter: Dp = 24.dp,
    /** Section header to its block. */
    val headerGap: Dp = 8.dp,
    /** Between blocks and sections. */
    val sectionGap: Dp = 32.dp,
    /** Minimum height of a one-line row. */
    val row: Dp = 56.dp,
    /** Minimum height of a two-line row. */
    val row2: Dp = 72.dp,
    /** Smallest touch target. */
    val target: Dp = 48.dp,
)

/** Corner radii (spec 4.4). */
@Immutable
data class PsShapes(
    /** 6 dp: photo tags, code wells. */
    val xs: Shape = RoundedCornerShape(6.dp),
    /** 10 dp: thumbnails, pair tiles. */
    val sm: Shape = RoundedCornerShape(10.dp),
    /** 14 dp: buttons, inputs, list blocks, banners. */
    val md: Shape = RoundedCornerShape(14.dp),
    /** 20 dp: sheet top corners, dialogs, hero preview. */
    val lg: Shape = RoundedCornerShape(20.dp),
    /** Status pills, chips, segmented control, avatars. */
    val pill: Shape = CircleShape,
    val sheetTop: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
)

/** Motion (spec 4.6). Durations in milliseconds. */
@Immutable
data class PsMotion(
    val pressScale: Float = 0.97f,
    val pressDamping: Float = 0.55f,
    val pressStiffness: Float = Spring.StiffnessMedium,
    val fast: Int = 120,
    val standard: Int = 220,
    val screenSlide: Int = 300,
    val screenFadeIn: Int = 220,
    val screenFadeOut: Int = 160,
    /** Screen push slides by this fraction of the width. */
    val screenSlideFraction: Float = 1f / 6f,
    val tab: Int = 160,
    val stroke: Int = 320,
    val loop: Int = GreasePencil.DRAW_MILLIS + 250,
) {
    val easing get() = FastOutSlowInEasing
}

internal val LocalPsColors = staticCompositionLocalOf { PsLightColors }
internal val LocalPsType = staticCompositionLocalOf { PsTypography }
internal val LocalPsSpace = staticCompositionLocalOf { PsSpace() }
internal val LocalPsShapes = staticCompositionLocalOf { PsShapes() }
internal val LocalPsMotion = staticCompositionLocalOf { PsMotion() }

/** The design tokens, read inside [PairshotTheme]: `Ps.colors.ink`, `Ps.type.body`, `Ps.space.gutter`… */
object Ps {
    val colors: PsColors @Composable @ReadOnlyComposable get() = LocalPsColors.current
    val type: PsType @Composable @ReadOnlyComposable get() = LocalPsType.current
    val space: PsSpace @Composable @ReadOnlyComposable get() = LocalPsSpace.current
    val shape: PsShapes @Composable @ReadOnlyComposable get() = LocalPsShapes.current
    val motion: PsMotion @Composable @ReadOnlyComposable get() = LocalPsMotion.current
}
