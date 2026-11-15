package com.greasepencil.pairshot.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.ui.cloud.CloudViewModel
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsSwitchRow
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.theme.Ps

/** Settings → Developer. Registered only in debug builds. */
@Composable
fun DeveloperScreen(vm: CloudViewModel, onBack: () -> Unit, onGallery: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val x by vm.extras.collectAsStateWithLifecycle()
    AccountPage("Developer", vm, onBack) {
        val s = st ?: return@AccountPage
        val gutter = Modifier.padding(horizontal = Ps.space.gutter)
        var server by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
        Text("Debug builds only.", gutter.padding(bottom = 16.dp), style = Ps.type.caption, color = Ps.colors.inkSoft)
        PsTextField(
            server, { server = it },
            gutter,
            label = "Server address",
            helper = "For a local server, e.g. http://192.168.1.20:8787. Changing it signs you out.",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        PsButton(
            "Save address", { vm.setServer(server) }, gutter.padding(top = 12.dp),
            variant = PsButtonVariant.Secondary, enabled = server.trim() != s.serverUrl && !x.busy, fullWidth = true,
        )
        PsListBlock(Modifier.padding(top = 24.dp)) {
            if (s.signedIn) {
                row {
                    PsSwitchRow(
                        "Pretend this account is Pro", checked = s.isPro, onChange = vm::setPro,
                        supporting = "Only works on a local test server.", enabled = !x.busy,
                    )
                }
            }
            row { PsListRow("Component gallery", onClick = onGallery) }
        }
    }
}
