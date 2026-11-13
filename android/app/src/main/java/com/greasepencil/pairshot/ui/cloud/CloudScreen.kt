package com.greasepencil.pairshot.ui.cloud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import com.greasepencil.pairshot.data.cloud.GallerySender
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
    fun connect(googleToken: suspend () -> String) = run {
        try {
            if (session.needsGoogle()) session.signInWithGoogle(googleToken()) else session.api()
        } catch (e: com.greasepencil.pairshot.data.cloud.SignInCancelled) {
            return@run
        }
        msgs.send("Connected.")
        load().join()
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(onBack: () -> Unit, vm: CloudViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val x by vm.extras.collectAsStateWithLifecycle()
    val s = st ?: return
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var server by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var hookUrl by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud & CRM", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (x.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Your jobs and photos live on this phone. The server only holds what you send to a client, " +
                    "and only until the link expires.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("SERVER") {
                OutlinedTextField(
                    server, { server = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server address") },
                    supportingText = { Text("For testing: your computer's address, e.g. http://192.168.1.20:8787") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setServer(server) }, enabled = server.trim() != s.serverUrl) { Text("Save") }
                    Button(
                        onClick = { vm.connect { com.greasepencil.pairshot.data.cloud.GoogleAuth.idToken(context) } },
                        enabled = s.serverUrl.isNotBlank() && !x.busy,
                    ) {
                        Text(
                            when {
                                s.signedIn -> "Refresh"
                                com.greasepencil.pairshot.data.cloud.GoogleAuth.configured -> "Sign in with Google"
                                else -> "Connect"
                            },
                        )
                    }
                }
                Text(
                    if (s.signedIn) "Connected · ${if (s.isPro) "Pro" else "Free plan"}${s.email?.let { " · $it" } ?: ""}"
                    else "Not connected yet. Sending your first link connects.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            x.usage?.let { u ->
                Section("THIS MONTH") {
                    Text(
                        if (u.galleriesPerMonth == null) "${u.galleriesThisMonth} links sent. No limit on Pro."
                        else "${u.galleriesThisMonth} of ${u.galleriesPerMonth} free links sent.",
                    )
                    Text(
                        "Up to ${u.photosPerGallery} photos a link" +
                            (u.galleryLifetimeDays?.let { ", each live for $it days." } ?: ", live until you close them."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Storage: ${mb(u.storageBytes)} of ${mb(u.storageLimitBytes)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (BuildConfig.DEBUG && s.signedIn) {
                Section("TESTING") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pretend this account is Pro", Modifier.weight(1f))
                        Switch(s.isPro, onCheckedChange = vm::setPro, enabled = !x.busy)
                    }
                    Text("Only works on a test server. Real Pro comes from Google Play.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Section("JOBS FROM YOUR CRM") {
                if (!s.isPro) {
                    ProNote("New jobs in Jobber, Housecall Pro, ServiceTitan or anything Zapier/Make can reach show up here by themselves.")
                } else {
                    Text("Point a Zapier/Make step (or your CRM's webhook) at this address. Each job it sends appears in your list.")
                    s.inboundUrl?.let { url ->
                        Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { copy(context, url) }) { Text("Copy") }
                            OutlinedButton(onClick = vm::rotate) { Text("New address") }
                            Button(onClick = vm::syncNow) { Text("Sync now") }
                        }
                    }
                    if (s.lastCrmSync > 0) {
                        Text("Last synced ${Ago.past(s.lastCrmSync, System.currentTimeMillis())}.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Section("TELL YOUR CRM (WEBHOOKS)") {
                if (!s.isPro) {
                    ProNote("When a link is sent, updated or first opened, Pairshot tells your CRM, so it can add a note or trigger a review request.")
                } else {
                    val hooks = x.hooks
                    hooks?.webhooks?.forEach { h ->
                        Column {
                            Text(h.url, style = MaterialTheme.typography.bodyMedium)
                            Text(h.events.replace(",", " · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row {
                                TextButton(onClick = { vm.testHook(h.id) }) { Text("Test") }
                                TextButton(onClick = { vm.deleteHook(h.id) }) { Text("Remove") }
                            }
                        }
                    }
                    OutlinedTextField(
                        hookUrl, { hookUrl = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Webhook address (https://…)") },
                    )
                    Button(onClick = { vm.addHook(hookUrl); hookUrl = "" }, enabled = hookUrl.isNotBlank()) { Text("Add webhook") }
                    if (!hooks?.deliveries.isNullOrEmpty()) {
                        HorizontalDivider()
                        Text("Recent", style = MaterialTheme.typography.titleSmall)
                        hooks!!.deliveries.take(10).forEach { d ->
                            val result = when (d.status) {
                                "ok" -> "delivered"
                                "failed" -> "gave up after ${d.attempts} tries"
                                else -> "retrying (${d.attempts} ${if (d.attempts == 1) "try" else "tries"}${d.lastStatus?.let { ", got $it" } ?: ""})"
                            }
                            Text(
                                "${d.event} · $result · ${Ago.past(d.createdAt, System.currentTimeMillis())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (d.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Section("API KEYS (ZAPIER, MAKE)") {
                if (!s.isPro) {
                    ProNote("Look up a job's link from your CRM, list sent links, or add jobs from anywhere.")
                } else {
                    x.keys.forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${k.prefix}… · " + (k.lastUsedAt?.let { "used ${Ago.past(it, System.currentTimeMillis())}" } ?: "never used"),
                                Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { vm.deleteKey(k.id) }) { Text("Delete") }
                        }
                    }
                    OutlinedButton(onClick = vm::newKey) { Text("New API key") }
                }
            }

            if (s.signedIn) {
                Section("ACCOUNT") {
                    Text(
                        s.email?.let { "Signed in as $it." } ?: "Connected without Google (from test mode). Sign out, then send, to link your Google account.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { confirmSignOut = true }, enabled = !x.busy) { Text("Sign out") }
                }
                Section("YOUR DATA") {
                    Text(
                        "Deletes every link, photo and CRM job on the server. Clients see a \"closed\" page. Nothing on this phone is touched.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete my cloud data", color = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    x.reveal?.let { (title, secret) ->
        AlertDialog(
            onDismissRequest = vm::dismissReveal,
            title = { Text("Copy this now") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title)
                    Text(secret, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = { TextButton(onClick = { copy(context, secret); vm.dismissReveal() }) { Text("Copy & close") } },
            dismissButton = { TextButton(onClick = vm::dismissReveal) { Text("Close") } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your jobs, photos and sent links stay as they are. Links keep working for clients. You'll sign in again the next time you send.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything on the server?") },
            text = { Text("All your links stop working right away. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCloud() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ProNote(what: String) {
    Text(what)
    Text("Part of Pro: one payment, no subscription.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}

private fun mb(bytes: Long) = if (bytes < 1_000_000_000) "%.0f MB".format(bytes / 1e6) else "%.1f GB".format(bytes / 1e9)

private fun copy(context: Context, text: String) =
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pairshot", text))
