package com.greasepencil.pairshot.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.cloud.CloudState
import com.greasepencil.pairshot.data.cloud.GoogleAuth
import com.greasepencil.pairshot.data.cloud.UsageDto
import com.greasepencil.pairshot.ui.cloud.CloudViewModel
import com.greasepencil.pairshot.ui.cloud.ErrorLine
import com.greasepencil.pairshot.ui.cloud.GoogleSignInButton
import com.greasepencil.pairshot.ui.cloud.SignInFacts
import com.greasepencil.pairshot.ui.cloud.mb
import com.greasepencil.pairshot.ui.components.ps.PsAvatar
import com.greasepencil.pairshot.ui.components.ps.PsBanner
import com.greasepencil.pairshot.ui.components.ps.PsBannerTone
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsMeter
import com.greasepencil.pairshot.ui.components.ps.PsPlanPill
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.theme.Ps

/** Settings → Account: who's signed in, this month's usage, sign out, delete cloud data. */
@Composable
fun AccountScreen(vm: CloudViewModel, onBack: () -> Unit, onSeePro: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val x by vm.extras.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val context = LocalContext.current
    // Usage that never came back while not busy means the last load failed (offline, server down).
    var tried by remember { mutableStateOf(false) }
    LaunchedEffect(x.busy) { if (x.busy) tried = true }

    AccountPage("Account", vm, onBack, confirm = confirm, onConfirmDone = { confirm = null }) {
        val s = st ?: return@AccountPage
        val gutter = Modifier.padding(horizontal = Ps.space.gutter)
        if (!s.signedIn) {
            Column(gutter.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Send client links", Modifier.semantics { heading() }, style = Ps.type.titleM, color = Ps.colors.ink)
                Text(
                    "Sign in once to send links. Everything else in Pairshot works without an account.",
                    style = Ps.type.bodyS,
                    color = Ps.colors.inkSoft,
                )
                SignInFacts()
                x.signInError?.let { ErrorLine(it) }
                val signIn = { vm.connect { GoogleAuth.idToken(context) }; Unit }
                if (GoogleAuth.configured) {
                    GoogleSignInButton(onClick = signIn, busy = x.signingIn)
                } else {
                    PsButton("Connect to the test server", signIn, variant = PsButtonVariant.Secondary, busy = x.signingIn, fullWidth = true)
                }
            }
            return@AccountPage
        }

        AccountHeader(s)
        if (s.email == null) {
            Text(
                "This phone connected before Google sign-in was on. Sign out, then send a link, to tie your links to your Google account.",
                gutter.padding(top = 4.dp),
                style = Ps.type.caption,
                color = Ps.colors.inkSoft,
            )
        }

        val u = x.usage
        val full = u?.galleriesPerMonth?.let { max -> u.galleriesThisMonth >= max } == true
        if (full) {
            PsBanner(
                "This month's free links are used",
                "They come back on the 1st, or Pro removes the limit.",
                gutter.padding(top = 16.dp),
                tone = PsBannerTone.Warning,
                actionLabel = "See Pro",
                onAction = onSeePro,
            )
        }

        PsSectionHeader("This month", Modifier.padding(top = Ps.space.sectionGap))
        when {
            u != null -> UsageBlock(u)
            x.busy || !tried -> Box(
                gutter.fillMaxWidth().height(120.dp).clip(Ps.shape.md).background(Ps.colors.sunken)
                    .semantics { contentDescription = "Loading your usage" },
            )
            else -> Column(gutter) {
                Text("Couldn't load your usage. Check your connection.", style = Ps.type.caption, color = Ps.colors.inkSoft)
                PsButton("Try again", { vm.load() }, variant = PsButtonVariant.Quiet)
            }
        }

        PsListBlock(Modifier.padding(top = Ps.space.sectionGap)) {
            row {
                PsListRow("Sign out", showChevron = false, enabled = !x.busy, onClick = {
                    confirm = Confirm(
                        "Sign out?",
                        "Your jobs, photos and sent links stay as they are, and clients' links keep working. You'll sign in again the next time you send.",
                        "Sign out", destructive = false, action = vm::signOut,
                    )
                })
            }
        }
        PsListBlock(Modifier.padding(top = Ps.space.sectionGap)) {
            row {
                PsListRow(
                    "Delete cloud data…",
                    supporting = "Removes every link, photo and CRM job from the server. Clients see a “closed” page. Nothing on this phone is touched.",
                    titleColor = Ps.colors.pencilText,
                    showChevron = false,
                    enabled = !x.busy,
                    onClick = {
                        confirm = Confirm(
                            "Delete everything on the server?",
                            "Every link you've sent stops working right away, and its photos are deleted. Jobs and photos on this phone stay. This can't be undone.",
                            "Delete everything", destructive = true, action = vm::deleteCloud,
                        )
                    },
                )
            }
        }
        Text(
            "Your jobs and photos live on this phone. The server only holds what you send to a client, until the link expires.",
            gutter.padding(top = 12.dp),
            style = Ps.type.caption,
            color = Ps.colors.inkSoft,
        )
    }
}

@Composable
private fun AccountHeader(s: CloudState) {
    val name = s.email ?: "Test account"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Ps.space.gutter, vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PsAvatar(name)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Signed in as", style = Ps.type.caption, color = Ps.colors.inkSoft)
            Text(name, style = Ps.type.rowTitle, color = Ps.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        PsPlanPill(s.isPro)
    }
}

@Composable
private fun UsageBlock(u: UsageDto) {
    val gutter = Modifier.padding(horizontal = Ps.space.gutter)
    Column(
        gutter.fillMaxWidth().clip(Ps.shape.md).background(Ps.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val sent = u.galleriesThisMonth
        val max = u.galleriesPerMonth
        if (max == null) {
            Text("$sent ${if (sent == 1) "link" else "links"} sent. No monthly limit on Pro.", style = Ps.type.body, color = Ps.colors.ink)
        } else {
            PsMeter(
                label = "Client links",
                value = "$sent of $max",
                spoken = "$sent of $max free links used",
                fraction = sent.toFloat() / max,
                full = sent >= max,
            )
        }
        PsMeter(
            label = "Photo storage",
            value = "${mb(u.storageBytes)} of ${mb(u.storageLimitBytes)}",
            spoken = "${mb(u.storageBytes)} of ${mb(u.storageLimitBytes)} used",
            fraction = if (u.storageLimitBytes > 0) u.storageBytes.toFloat() / u.storageLimitBytes else 0f,
            full = u.storageBytes >= u.storageLimitBytes,
        )
    }
    Text(
        "Up to ${u.photosPerGallery} photos a link" +
            (u.galleryLifetimeDays?.let { ". Links stay live for $it days, then their photos are deleted." } ?: ". Links stay live until you close them."),
        gutter.padding(top = 8.dp),
        style = Ps.type.caption,
        color = Ps.colors.inkSoft,
    )
}
