package com.greasepencil.pairshot.ui.components.ps

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.ui.theme.PairshotTheme
import com.greasepencil.pairshot.ui.theme.Ps

/** Every component preview, in light and dark. */
@Preview(name = "Light", widthDp = 360)
@Preview(name = "Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PsPreviews

/** Paper background and a little room, for previews. */
@Composable
internal fun PreviewPaper(content: @Composable ColumnScope.() -> Unit) {
    PairshotTheme {
        Column(
            Modifier.background(Ps.colors.paper).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
