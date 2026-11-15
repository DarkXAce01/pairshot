package com.greasepencil.pairshot.ui.components.ps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.ui.theme.Ps

/**
 * A screen: paper background, [topBar] under the status bar, [content] filling the middle and
 * [bottomBar] pinned at the bottom (it pads for the navigation bar itself). Without a top or
 * bottom bar the content pads for that edge's system bar. Snackbars float above the bottom bar.
 */
@Composable
fun PsScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val top = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    Column(modifier.fillMaxSize().background(Ps.colors.paper)) {
        if (topBar != null) {
            Box(Modifier.fillMaxWidth().windowInsetsPadding(top)) { topBar() }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (topBar != null) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else Modifier)
                .windowInsetsPadding(
                    when {
                        topBar == null && bottomBar == null -> WindowInsets.safeDrawing
                        topBar == null -> top
                        bottomBar == null -> sides.union(bottom)
                        else -> sides
                    },
                ),
        ) {
            content()
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { snackbarHost() }
        }
        bottomBar?.invoke()
    }
}

@PsPreviews
@Composable
private fun PsScaffoldPreview() = PreviewPaper {
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        PsScaffold(
            topBar = { PsTopBar("New job", onBack = {}) },
            bottomBar = { PsBottomBar { PsButton("Create job", {}, Modifier.weight(1f)) } },
        ) {
            Text("Content", Modifier.padding(20.dp), style = Ps.type.body, color = Ps.colors.ink)
        }
    }
}
