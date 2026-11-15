package com.greasepencil.pairshot.ui.account

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.cloud.GoogleAuth
import com.greasepencil.pairshot.ui.cloud.CloudViewModel
import com.greasepencil.pairshot.ui.cloud.GoogleSignInButton
import com.greasepencil.pairshot.ui.components.ps.PsBottomBar
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsLoopMark
import com.greasepencil.pairshot.ui.theme.Ps

/** Free against Pro, one line each: feature, free, pro. */
private val planRows = listOf(
    Triple("Client links", "3 a month", "No limit"),
    Triple("Photos a link", "30", "100"),
    Triple("Links stay live", "14 days", "Until you close them"),
    Triple("Edit after sending", "3 days", "Any time"),
    Triple("“Made with Pairshot”", "On", "Off"),
    Triple("CRM connections", "—", "Yes"),
)

/** Settings → Pairshot Pro: what it gives, one payment, and restore. */
@Composable
fun ProScreen(vm: CloudViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val x by vm.extras.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = st
    val pro = s?.isPro == true
    val signedIn = s?.signedIn == true

    AccountPage(
        "Pairshot Pro", vm, onBack,
        showTitle = false,
        bottomBar = if (s == null || pro) {
            null
        } else {
            {
                PsBottomBar {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!signedIn) {
                            Text(
                                "Pro is tied to your Google account, so sign in first.",
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                style = Ps.type.caption,
                                color = Ps.colors.inkSoft,
                                textAlign = TextAlign.Center,
                            )
                            val signIn = { vm.connect { GoogleAuth.idToken(context) }; Unit }
                            if (GoogleAuth.configured) {
                                GoogleSignInButton(onClick = signIn, busy = x.signingIn)
                            } else {
                                PsButton("Connect to the test server", signIn, variant = PsButtonVariant.Secondary, busy = x.signingIn, fullWidth = true)
                            }
                        } else {
                            PsButton(
                                x.price?.let { "Get Pro · $it" } ?: "Get Pro",
                                { (context as? Activity)?.let(vm::buyPro) },
                                variant = PsButtonVariant.Accent,
                                busy = x.busy,
                                fullWidth = true,
                            )
                            PsButton("Restore purchase", vm::restorePro, variant = PsButtonVariant.Quiet, enabled = !x.busy, fullWidth = true)
                        }
                    }
                }
            }
        },
    ) {
        val gutter = Modifier.padding(horizontal = Ps.space.gutter)
        Text("PAIRSHOT PRO", gutter, style = Ps.type.eyebrow, color = Ps.colors.inkSoft)
        if (pro) {
            Row(gutter.padding(top = 4.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
                PsLoopMark { Text("Pro", style = Ps.type.display, color = Ps.colors.ink) }
                Text(" is on.", style = Ps.type.display, color = Ps.colors.ink)
            }
            Text(
                "Thank you. Everything below is yours for good.",
                gutter.padding(top = 8.dp),
                style = Ps.type.body,
                color = Ps.colors.inkSoft,
            )
        } else {
            Text("Pay once.\nKeep it.", gutter.padding(top = 8.dp).semantics { heading() }, style = Ps.type.display, color = Ps.colors.ink)
            Text(
                "No subscription. No ads on either plan.",
                gutter.padding(top = 12.dp),
                style = Ps.type.body,
                color = Ps.colors.inkSoft,
            )
        }

        PsListBlock(Modifier.padding(top = 24.dp)) {
            row { PlanRow("", "FREE", "PRO", head = true, dimFree = pro) }
            planRows.forEach { (feature, free, p) -> row { PlanRow(feature, free, p, dimFree = pro) } }
        }
    }
}

@Composable
private fun PlanRow(feature: String, free: String, pro: String, head: Boolean = false, dimFree: Boolean = false) {
    val c = Ps.colors
    val m = Modifier
        .fillMaxWidth()
        .heightIn(min = if (head) 40.dp else Ps.space.row)
        .padding(horizontal = Ps.space.gutter, vertical = 10.dp)
        .then(
            if (head) {
                Modifier.clearAndSetSemantics {}
            } else {
                Modifier.clearAndSetSemantics { contentDescription = "$feature: Free $free, Pro ${pro.lowercase()}" }
            },
        )
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        Text(feature, Modifier.weight(1.3f), style = Ps.type.bodyS, color = c.ink)
        Spacer(Modifier.width(8.dp))
        Text(
            free,
            Modifier.weight(1f).alpha(if (dimFree) 0.5f else 1f),
            style = if (head) Ps.type.eyebrow else Ps.type.bodyS,
            color = c.inkSoft,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            pro,
            Modifier.weight(1f),
            style = if (head) Ps.type.eyebrow else Ps.type.rowTitleS,
            color = if (head) c.inkSoft else c.ink,
            textAlign = TextAlign.End,
        )
    }
}
