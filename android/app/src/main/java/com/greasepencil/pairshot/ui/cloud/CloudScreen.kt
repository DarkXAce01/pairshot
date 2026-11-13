package com.greasepencil.pairshot.ui.cloud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.greasepencil.pairshot.BuildConfig
import com.greasepencil.pairshot.data.cloud.ApiKeyDto
import com.greasepencil.pairshot.data.cloud.CloudSession
import com.greasepencil.pairshot.data.cloud.CloudState
import com.greasepencil.pairshot.data.cloud.CrmSync
import com.greasepencil.pairshot.data.cloud.DeliveryDto
import com.greasepencil.pairshot.data.cloud.GallerySender
import com.greasepencil.pairshot.data.cloud.GoogleAuth
import com.greasepencil.pairshot.data.cloud.UsageDto
import com.greasepencil.pairshot.data.cloud.WebhooksResponse
import com.greasepencil.pairshot.data.cloud.call
import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.domain.Ago
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudExtras(
    val busy: Boolean = false,
    val usage: UsageDto? = null,
    val hooks: WebhooksResponse? = null,
    val keys: List<ApiKeyDto> = emptyList(),
    /** Shown once, right after it's made: the API key or the webhook signing secret. */
    val reveal: Pair<String, String>? = null,
    /** Sign-in is under way (the Google sheet is up or the server is answering). */
    val signingIn: Boolean = false,
    /** Why the last sign-in didn't work, shown under the button. */
    val signInError: String? = null,
)

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val session: CloudSession,
    private val crm: CrmSync,
    private val sender: GallerySender,
    private val db: AppDatabase,
) : ViewModel() {
    val state: StateFlow<CloudState?> = session.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val extras = MutableStateFlow(CloudExtras())
    private val msgs = Channel<String>(Channel.BUFFERED)
    val messages = msgs.receiveAsFlow()

    init { load() }

    private fun run(okMessage: String? = null, block: suspend () -> Unit) = viewModelScope.launch {
        extras.update { it.copy(busy = true) }
        try {
            block()
            okMessage?.let { msgs.send(it) }
        } catch (e: Exception) {
            msgs.send(e.message ?: "Something went wrong.")
        } finally {
            extras.update { it.copy(busy = false) }
        }
    }

    /** Fetches plan, usage and (Pro) integrations, if this phone has signed in before. */
    fun load() = run {
        if (!session.state.first().signedIn) return@run
        val me = session.call { it.me() }
        session.saveAccount(me)
        val usage = session.call { it.usage() }
        extras.update { it.copy(usage = usage) }
        if (me.plan == "pro") {
            val hooks = session.call { it.webhooks() }
            val keys = session.call { it.apiKeys() }
            extras.update { it.copy(hooks = hooks, keys = keys) }
        } else {
            extras.update { it.copy(hooks = null, keys = emptyList()) }
        }
    }

    fun setServer(url: String) = run("Saved.") { session.setServerUrl(url) }

    /** [googleToken] fetches a Google ID token (needs the screen); used only when Google sign-in is set up. */
    fun connect(googleToken: suspend () -> String) = viewModelScope.launch {
        extras.update { it.copy(signingIn = true, signInError = null) }
        val result = runCatching {
            if (session.needsGoogle()) session.signInWithGoogle(googleToken()) else session.api()
        }
        extras.update { it.copy(signingIn = false, signInError = result.exceptionOrNull()?.let(::signInError)) }
        if (result.isSuccess) {
            msgs.send("Signed in.")
            load().join()
        }
    }

    fun copied(what: String) = viewModelScope.launch { msgs.send("$what copied.") }

    fun setPro(pro: Boolean) = run(if (pro) "Pro is on (test only)." else "Back to free.") {
        session.call { it.setDevPlan(if (pro) "pro" else "free") }
        load().join()
    }

    fun syncNow() = run {
        val r = crm.pull()
        sender.refresh()
        msgs.send(
            when {
                r == null -> "CRM sync is part of Pro."
                r.total == 0 -> "Up to date. No new jobs."
                else -> listOfNotNull(
                    r.added.takeIf { it > 0 }?.let { "$it new" },
                    r.updated.takeIf { it > 0 }?.let { "$it updated" },
                    r.removed.takeIf { it > 0 }?.let { "$it cancelled" },
                ).joinToString(", ", postfix = ".")
            },
        )
    }

    fun rotate() = run("New address made. Update it in Zapier/your CRM.") {
        session.setInboundUrl(session.call { it.rotateInbound() })
    }

    fun addHook(url: String) = run {
        val h = session.call { it.addWebhook(url.trim()) }
        extras.update { it.copy(reveal = "Signing secret (for checking the X-Pairshot-Signature header)" to h.secret) }
        load().join()
    }

    fun testHook(id: String) = run {
        val ok = session.call { it.testWebhook(id) }
        msgs.send(if (ok) "It answered. You're connected." else "It didn't answer with OK. Check the address.")
        load().join()
    }

    fun deleteHook(id: String) = run("Removed.") { session.call { it.deleteWebhook(id) }; load().join() }

    fun newKey() = run {
        val k = session.call { it.createApiKey() }
        extras.update { it.copy(reveal = "API key (shown once; paste it into Zapier or Make)" to k.key) }
        load().join()
    }

    fun deleteKey(id: String) = run("Key deleted. Anything using it stops working.") { session.call { it.deleteApiKey(id) }; load().join() }

    fun dismissReveal() = extras.update { it.copy(reveal = null) }

    fun signOut() = run("Signed out. Your jobs and links are still here; the next Send asks you to sign in.") {
        session.signOut()
        extras.value = CloudExtras()
    }

    fun deleteCloud() = run("Everything on the server is gone. Your jobs and photos on this phone are untouched.") {
        session.call { it.deleteEverything() }
        session.forgetAccount()
        db.galleries().deleteAll()
        extras.value = CloudExtras()
    }
}


/** A question to confirm before doing something that can't easily be undone. */
private data class Confirm(val title: String, val text: String, val yes: String, val destructive: Boolean, val action: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(onBack: () -> Unit, vm: CloudViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val x by vm.extras.collectAsStateWithLifecycle()
    val s = st ?: return
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val copy = { label: String, text: String -> copyText(context, text); vm.copied(label); Unit }
    val signIn = { vm.connect { GoogleAuth.idToken(context) }; Unit }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account & CRM", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Reserve the bar's space so the page doesn't jump when it appears.
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (x.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading" })
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (s.signedIn) {
                    AccountCard(s, busy = x.busy, onSignOut = {
                        confirm = Confirm(
                            "Sign out?",
                            "Your jobs, photos and sent links stay as they are, and clients' links keep working. You'll sign in again the next time you send.",
                            "Sign out", destructive = false, action = vm::signOut,
                        )
                    })
                    x.usage?.let { UsageCard(it) }
                } else {
                    SignedOutCard(busy = x.signingIn, error = x.signInError, onSignIn = signIn)
                }

                if (!s.isPro) {
                    ProCard()
                } else {
                    CrmJobsCard(
                        s, busy = x.busy,
                        onCopy = { copy("Address", it) },
                        onSync = vm::syncNow,
                        onNewAddress = {
                            confirm = Confirm(
                                "Make a new address?",
                                "The old address stops working right away. Anything sending jobs to it (Zapier, Make, your CRM) needs the new one.",
                                "Make new address", destructive = true, action = vm::rotate,
                            )
                        },
                    )
                    WebhooksCard(
                        x.hooks, busy = x.busy,
                        onAdd = vm::addHook,
                        onTest = vm::testHook,
                        onRemove = { id, url ->
                            confirm = Confirm("Remove this webhook?", "Pairshot stops telling $url about your links.", "Remove", destructive = true) { vm.deleteHook(id) }
                        },
                    )
                    ApiKeysCard(
                        x.keys, busy = x.busy,
                        onCreate = vm::newKey,
                        onDelete = { k ->
                            confirm = Confirm("Delete key ${k.prefix}…?", "Anything using this key (a Zap, a Make scenario) stops working right away.", "Delete key", destructive = true) { vm.deleteKey(k.id) }
                        },
                    )
                }

                if (BuildConfig.DEBUG) DeveloperCard(s, busy = x.busy, onSaveServer = vm::setServer, onPro = vm::setPro)

                if (s.signedIn) {
                    DangerCard(onDelete = {
                        confirm = Confirm(
                            "Delete everything on the server?",
                            "Every link you've sent stops working right away, and its photos are deleted. Jobs and photos on this phone stay. This can't be undone.",
                            "Delete everything", destructive = true, action = vm::deleteCloud,
                        )
                    })
                }

                Text(
                    "Your jobs and photos live on this phone. The server only holds what you send to a client, and only until the link expires.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    x.reveal?.let { (title, secret) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Copy this now") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title)
                    CodeBox(secret)
                    Text("For safety it's shown only once. If you lose it, make a new one.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { copy("Secret", secret); vm.dismissReveal() }) { Text("Copy & close") } },
            dismissButton = { TextButton(onClick = vm::dismissReveal) { Text("I've saved it") } },
        )
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.text) },
            confirmButton = {
                TextButton(onClick = { confirm = null; c.action() }) {
                    Text(c.yes, color = if (c.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

// ---------- Account ----------

@Composable
private fun SignedOutCard(busy: Boolean, error: String?, onSignIn: () -> Unit) {
    Card(title = "Send client links") {
        Text(
            "Sign in once to send links. Everything else in Pairshot works without an account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SignInFacts()
        error?.let { ErrorLine(it) }
        if (GoogleAuth.configured) {
            GoogleSignInButton(onClick = onSignIn, busy = busy)
        } else {
            Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Connect to the test server") }
        }
    }
}

@Composable
private fun AccountCard(s: CloudState, busy: Boolean, onSignOut: () -> Unit) {
    val name = s.email ?: "Test account"
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Read as one sentence: "Signed in as you@gmail.com, Free plan".
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Box(
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.first().uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Signed in as", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                PlanBadge(s.isPro)
            }
            if (s.email == null) {
                Text(
                    "This phone connected before Google sign-in was on. Sign out, then send a link, to tie your links to your Google account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sign out…") }
        }
    }
}

@Composable
private fun PlanBadge(pro: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (pro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (pro) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            if (pro) "Pro" else "Free plan",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun UsageCard(u: UsageDto) {
    Card(title = "This month") {
        val sent = u.galleriesThisMonth
        val max = u.galleriesPerMonth
        if (max == null) {
            Text("$sent ${if (sent == 1) "link" else "links"} sent. No monthly limit on Pro.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Meter(
                label = "Client links",
                value = "$sent of $max",
                spoken = "$sent of $max free links used",
                fraction = sent.toFloat() / max,
                full = sent >= max,
            )
            if (sent >= max) {
                Text("You've used this month's free links. They come back on the 1st, or Pro removes the limit.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Meter(
            label = "Photo storage",
            value = "${mb(u.storageBytes)} of ${mb(u.storageLimitBytes)}",
            spoken = "${mb(u.storageBytes)} of ${mb(u.storageLimitBytes)} used",
            fraction = if (u.storageLimitBytes > 0) u.storageBytes.toFloat() / u.storageLimitBytes else 0f,
            full = u.storageBytes >= u.storageLimitBytes,
        )
        Text(
            "Up to ${u.photosPerGallery} photos a link" +
                (u.galleryLifetimeDays?.let { ". Links stay live for $it days, then their photos are deleted." } ?: ". Links stay live until you close them."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A labelled bar. Screen readers hear [spoken] instead of a bare percentage. */
@Composable
private fun Meter(label: String, value: String, spoken: String, fraction: Float, full: Boolean) {
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label; stateDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

// ---------- Pro features ----------

@Composable
private fun ProCard() {
    Card(title = "Connect your CRM", icon = Icons.Outlined.WorkspacePremium) {
        FactRow(Icons.Outlined.MoveToInbox, "New jobs from Jobber, Housecall Pro, ServiceTitan or anything Zapier/Make can reach show up in your list by themselves.")
        FactRow(Icons.Outlined.NotificationsActive, "When a client opens their link, your CRM hears about it, so it can add a note or ask for a review.")
        FactRow(Icons.Outlined.Key, "API keys let Zapier and Make look up a job's link or list what you've sent.")
        Text(
            "Part of Pro: one payment, no subscription.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrmJobsCard(s: CloudState, busy: Boolean, onCopy: (String) -> Unit, onSync: () -> Unit, onNewAddress: () -> Unit) {
    Card(title = "Jobs from your CRM", icon = Icons.Outlined.MoveToInbox) {
        Text(
            "Send jobs to this address from a Zapier or Make step, or your CRM's webhook. Each one appears in your job list.",
            style = MaterialTheme.typography.bodyMedium,
        )
        val url = s.inboundUrl
        if (url == null) {
            Text("Sign in again to get your address.", style = MaterialTheme.typography.bodySmall)
        } else {
            CodeBox(url)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCopy(url) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy address")
                }
                OutlinedButton(onClick = onSync, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Sync now")
                }
                TextButton(onClick = onNewAddress, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("New address…") }
            }
        }
        Text(
            if (s.lastCrmSync > 0) "Last synced ${Ago.past(s.lastCrmSync, System.currentTimeMillis())}. Syncs by itself every 30 minutes."
            else "Syncs by itself every 30 minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WebhooksCard(
    hooks: WebhooksResponse?,
    busy: Boolean,
    onAdd: (String) -> Unit,
    onTest: (String) -> Unit,
    onRemove: (id: String, url: String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    val trimmed = url.trim()
    val invalid = trimmed.isNotEmpty() && !trimmed.startsWith("https://")
    Card(title = "Tell your CRM (webhooks)", icon = Icons.Outlined.NotificationsActive) {
        Text(
            "Pairshot posts to these addresses when a link is sent, updated or first opened.",
            style = MaterialTheme.typography.bodyMedium,
        )
        hooks?.webhooks?.forEach { h ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(h.url, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        h.events.split(",").joinToString(", ") { eventName(it.trim()) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = { onTest(h.id) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Send a test") }
                        TextButton(onClick = { onRemove(h.id, h.url) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Remove…", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            url, { url = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Webhook address") },
            placeholder = { Text("https://hooks.zapier.com/…") },
            isError = invalid,
            supportingText = { Text(if (invalid) "It has to start with https://" else "From Zapier's \"Catch Hook\" step, Make, or your CRM.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        Button(
            onClick = { onAdd(trimmed); url = "" },
            enabled = trimmed.isNotEmpty() && !invalid && !busy,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Add webhook") }

        val deliveries = hooks?.deliveries.orEmpty()
        if (deliveries.isNotEmpty()) {
            HorizontalDivider()
            Text("Recent deliveries", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            deliveries.take(10).forEach { DeliveryRow(it) }
        }
    }
}

/** Status is said by icon and words, never by colour alone. */
@Composable
private fun DeliveryRow(d: DeliveryDto) {
    val (icon, tint, result) = when (d.status) {
        "ok" -> Triple(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary, "Delivered")
        "failed" -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, "Gave up after ${d.attempts} tries")
        else -> Triple(
            Icons.Outlined.Schedule, MaterialTheme.colorScheme.onSurfaceVariant,
            "Retrying (${d.attempts} ${if (d.attempts == 1) "try" else "tries"}${d.lastStatus?.let { ", got $it" } ?: ""})",
        )
    }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("${eventName(d.event)}: $result", style = MaterialTheme.typography.bodyMedium)
            Text(Ago.past(d.createdAt, System.currentTimeMillis()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun eventName(e: String) = when (e) {
    "gallery.published" -> "Link sent"
    "gallery.updated" -> "Link updated"
    "gallery.viewed" -> "Link opened"
    "ping" -> "Test"
    else -> e
}

@Composable
private fun ApiKeysCard(keys: List<ApiKeyDto>, busy: Boolean, onCreate: () -> Unit, onDelete: (ApiKeyDto) -> Unit) {
    Card(title = "API keys (Zapier, Make)", icon = Icons.Outlined.Key) {
        Text(
            "Give a key to Zapier or Make so they can look up a job's link, list sent links, or add jobs.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (keys.isEmpty()) {
            Text("No keys yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        keys.forEach { k ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text("${k.prefix}…", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        k.lastUsedAt?.let { "Used ${Ago.past(it, System.currentTimeMillis())}" } ?: "Never used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onDelete(k) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Delete…", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        OutlinedButton(onClick = onCreate, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Create API key") }
    }
}

// ---------- Developer and danger ----------

@Composable
private fun DeveloperCard(s: CloudState, busy: Boolean, onSaveServer: (String) -> Unit, onPro: (Boolean) -> Unit) {
    var server by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    Card(title = "Developer (debug builds only)") {
        OutlinedTextField(
            server, { server = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server address") },
            supportingText = { Text("For a local server, e.g. http://192.168.1.20:8787. Changing it signs you out.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        OutlinedButton(
            onClick = { onSaveServer(server) },
            enabled = server.trim() != s.serverUrl && !busy,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Save address") }
        if (s.signedIn) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = s.isPro, enabled = !busy, role = Role.Switch, onValueChange = onPro),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pretend this account is Pro", style = MaterialTheme.typography.bodyLarge)
                    Text("Only works on a local test server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(s.isPro, onCheckedChange = null, enabled = !busy)
            }
        }
    }
}

@Composable
private fun DangerCard(onDelete: () -> Unit) {
    Card(title = "Delete cloud data", icon = Icons.Outlined.DeleteForever) {
        Text(
            "Removes every link, photo and CRM job from the server. Clients see a \"closed\" page. Nothing on this phone is touched.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = onDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Delete my cloud data…") }
    }
}

// ---------- Building blocks ----------

/** A card with a real heading, so screen-reader users can jump between sections. */
@Composable
private fun Card(title: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                icon?.let {
                    Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** Something to copy (an address or a key): selectable, monospace, wraps instead of cutting off. */
@Composable
private fun CodeBox(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }
    }
}

private fun mb(bytes: Long) = if (bytes < 1_000_000_000) "%.0f MB".format(bytes / 1e6) else "%.1f GB".format(bytes / 1e9)

private fun copyText(context: Context, text: String) =
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pairshot", text))
