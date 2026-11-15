package com.greasepencil.pairshot.ui.job

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.greasepencil.pairshot.data.cloud.CloudSession
import com.greasepencil.pairshot.data.cloud.GallerySender
import com.greasepencil.pairshot.data.db.AreaEntity
import com.greasepencil.pairshot.data.db.JobEntity
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.data.db.SendState
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.data.repo.Clock
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.data.repo.PhotoImporter
import com.greasepencil.pairshot.domain.AfterQueue
import com.greasepencil.pairshot.domain.ClientMessage
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsConfirmDialog
import com.greasepencil.pairshot.ui.components.ps.PsIconButton
import com.greasepencil.pairshot.ui.components.ps.PsMenu
import com.greasepencil.pairshot.ui.components.ps.PsMenuItem
import com.greasepencil.pairshot.ui.components.ps.PsPairArt
import com.greasepencil.pairshot.ui.components.ps.PsPairTile
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.PsSnackbarHost
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.nav.JobRoute
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.tabular
import com.greasepencil.pairshot.work.SendWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A label and its pairs; [area] null is the "No label" group. */
data class AreaSection(val area: AreaEntity?, val pairs: List<PairRow>) {
    val key get() = area?.id ?: "none"
    val title get() = area?.name ?: "No label"
}

data class JobState(
    val loaded: Boolean = false,
    val job: JobEntity? = null,
    val sections: List<AreaSection> = emptyList(),
    val pending: Int = 0,
    val total: Int = 0,
    val completePairs: Int = 0,
)

@HiltViewModel
class JobViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: JobRepository,
    private val importer: PhotoImporter,
    private val sender: GallerySender,
    private val settings: SettingsStore,
    private val cloud: CloudSession,
    private val clock: Clock,
    @ApplicationContext private val app: Context,
) : ViewModel() {
    val jobId = handle.toRoute<JobRoute>().jobId

    /** A filled-in message to share, once the link is ready. */
    private val shareEvents = Channel<String>(Channel.BUFFERED)
    val shares = shareEvents.receiveAsFlow()

    /** A first link just went out: play the "Sent." moment, then share. */
    private val momentEvents = Channel<Moment>(Channel.BUFFERED)
    val moments = momentEvents.receiveAsFlow()

    val link: StateFlow<LinkState> = combine(
        repo.observeJob(jobId), repo.observePairs(jobId), sender.observe(jobId), settings.settings, cloud.state,
    ) { job, pairs, g, s, c ->
        val canSend = pairs.any { !it.needsAfter && !it.pair.hidden }
        val changed = job != null && g?.sentFingerprint != null &&
            GallerySender.fingerprint(job, pairs, s, c.isPro) != g.sentFingerprint
        LinkState(g, changed, canSend, clock.now(), wifiOnly = s.wifiOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LinkState())

    init {
        viewModelScope.launch { runCatching { sender.refresh() } }
        // The user tapped Send and is waiting: open the share sheet as soon as the link exists.
        viewModelScope.launch {
            sender.observe(jobId).collect { g ->
                if (g != null && g.shareWhenReady && g.sendState == SendState.SENT && g.url != null) {
                    sender.clearShareFlag(g.id)
                    message()?.let { msg ->
                        val pairs = repo.observePairs(jobId).first().filter { !it.pair.hidden && it.afterFile != null }
                        val cover = pairs.firstOrNull { it.pair.isCover } ?: pairs.firstOrNull()
                        momentEvents.send(Moment(cover?.afterFile, cover?.afterRotation ?: 0, msg))
                    }
                }
            }
        }
    }

    suspend fun needsGoogle() = cloud.needsGoogle()
    suspend fun signInWithGoogle(idToken: String) = cloud.signInWithGoogle(idToken)

    fun send() = viewModelScope.launch {
        val first = link.value.gallery?.url == null || link.value.gallery?.remoteState in setOf("revoked", "expired")
        sender.queue(jobId, shareWhenReady = first)
        SendWorker.enqueue(app, jobId, wifiOnly = settings.settings.first().wifiOnly)
    }

    /** "Wi-Fi only" is on but the client is waiting: send this one now over mobile data. */
    fun sendOverMobile() = viewModelScope.launch { SendWorker.enqueue(app, jobId, wifiOnly = false) }

    fun close() = viewModelScope.launch {
        runCatching { sender.close(jobId) }.onFailure { shareEvents.send("!" + (it.message ?: "Couldn't close the link.")) }
    }

    /** The message for the client, with their first name, the service and the link filled in. */
    suspend fun message(): String? {
        val g = sender.observe(jobId).first() ?: return null
        val url = g.url ?: return null
        val job = repo.observeJob(jobId).first() ?: return url
        val s = settings.settings.first()
        return ClientMessage.fill(s.messageTemplate, job.clientName, job.service, url)
    }

    val state: StateFlow<JobState> = combine(
        repo.observeJob(jobId), repo.observeAreas(jobId), repo.observePairs(jobId),
    ) { job, areas, pairs ->
        val byArea = pairs.groupBy { it.pair.areaId }
        JobState(
            loaded = true,
            job = job,
            sections = listOfNotNull(
                byArea[null]?.let { AreaSection(null, it) },
            ) + areas.map { AreaSection(it, byArea[it.id].orEmpty()) },
            pending = AfterQueue.pending(pairs).size,
            total = pairs.size,
            completePairs = pairs.count { !it.needsAfter && !it.pair.hidden },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobState())

    fun renameArea(id: String, name: String) = viewModelScope.launch { if (name.isNotBlank()) repo.renameArea(id, name) }
    fun deleteArea(id: String) = viewModelScope.launch { repo.deleteArea(id) }
    fun import(uris: List<Uri>, done: (Int) -> Unit) = viewModelScope.launch { done(importer.importBefores(jobId, uris)) }
    fun deleteJob(done: () -> Unit) = viewModelScope.launch { repo.deleteJob(jobId); done() }
}

/**
 * The one line above the pairs that says what's next, or null once a link exists. [canSend] is
 * "at least one finished pair shown to the client".
 */
fun jobHint(total: Int, canSend: Boolean, hasLink: Boolean): String? = when {
    total == 0 -> "No photos yet. Tap Before and shoot. Labels like \"Driveway\" are optional."
    !canSend -> "Finish a before and after to send a link."
    !hasLink -> "Send makes a page with these before and afters for your client. Their name isn't on it."
    else -> null
}

/** "House wash · 12 Oak St", or null when the job has neither. */
fun jobMeta(service: String?, address: String?): String? =
    listOfNotNull(service?.takeIf { it.isNotBlank() }, address?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null }

@Composable
fun JobScreen(
    onBack: () -> Unit,
    onCamera: (after: Boolean, pairId: String?) -> Unit,
    onOpenPair: (String) -> Unit,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    vm: JobViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) vm.import(uris) { n ->
            scope.launch {
                snackbar.showSnackbar(
                    if (n == uris.size) "Added $n ${if (n == 1) "photo" else "photos"} as befores."
                    else "Added $n of ${uris.size}. The rest couldn't be read.",
                )
            }
        }
    }
    val s by vm.state.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var moment by remember { mutableStateOf<Moment?>(null) }
    LaunchedEffect(Unit) { vm.moments.collect { moment = it } }
    LaunchedEffect(Unit) {
        vm.shares.collect { msg ->
            if (msg.startsWith("!")) snackbar.showSnackbar(msg.drop(1)) else LinkActions.share(context, msg)
        }
    }
    var confirmClose by remember { mutableStateOf(false) }
    // Asked once, at the first Send: so a link that waits for signal can say when it has gone.
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // The first Send is where sign-in happens; never before. A short sheet says why, then Google's own sheet.
    var signInSheet by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    var signInFailed by remember { mutableStateOf<String?>(null) }
    val askNotifications = {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val send = {
        scope.launch {
            if (vm.needsGoogle()) {
                signInFailed = null
                signInSheet = true
            } else {
                askNotifications()
                vm.send()
            }
        }
        Unit
    }
    val continueWithGoogle = {
        scope.launch {
            signingIn = true
            signInFailed = null
            val result = runCatching { vm.signInWithGoogle(com.greasepencil.pairshot.data.cloud.GoogleAuth.idToken(context)) }
            signingIn = false
            result.exceptionOrNull()?.let { e ->
                signInFailed = com.greasepencil.pairshot.ui.cloud.signInError(e)
                return@launch
            }
            signInSheet = false
            askNotifications()
            vm.send()
            launch { snackbar.showSnackbar("Signed in. Sending your link…") }
        }
        Unit
    }
    if (signInSheet) {
        com.greasepencil.pairshot.ui.cloud.SignInSheet(
            busy = signingIn,
            error = signInFailed,
            onContinue = continueWithGoogle,
            onDismiss = { signInSheet = false },
        )
    }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AreaEntity?>(null) }
    var deletingArea by remember { mutableStateOf<AreaSection?>(null) }

    // The job was deleted (here or elsewhere): leave.
    LaunchedEffect(s.loaded, s.job) { if (s.loaded && s.job == null) onBack() }
    val job = s.job ?: return
    val bar = jobBar(link)

    PsScaffold(
        topBar = {
            PsTopBar(job.clientName, onBack = onBack) {
                Box {
                    PsIconButton(Icons.Outlined.MoreVert, "Job options", { menu = true })
                    PsMenu(menu, { menu = false }) {
                        PsMenuItem("Edit job", { menu = false; onEdit() }, icon = Icons.Outlined.Edit)
                        PsMenuItem(
                            "Add photos from gallery",
                            {
                                menu = false
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            icon = Icons.Outlined.PhotoLibrary,
                        )
                        PsMenuItem("Delete job", { menu = false; confirmDelete = true }, icon = Icons.Outlined.Delete, destructive = true)
                    }
                }
            }
        },
        bottomBar = bar?.let { b -> { JobBottomBar(b, onPreview = onPreview, onSend = send) } },
        snackbarHost = { PsSnackbarHost(snackbar) },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "meta") { JobMeta(job) }
            if (linkPanelMode(link.gallery) != LinkPanelMode.ABSENT) {
                item(key = "link") {
                    LinkPanel(
                        link,
                        clientPhone = job.clientPhone,
                        onShare = { scope.launch { vm.message()?.let { LinkActions.share(context, it) } } },
                        onText = { scope.launch { vm.message()?.let { m -> job.clientPhone?.let { LinkActions.text(context, it, m) } } } },
                        onCopy = {
                            link.gallery?.url?.let { LinkActions.copy(context, it) }
                            scope.launch { snackbar.showSnackbar("Link copied.") }
                        },
                        onClose = { confirmClose = true },
                        onSendOverMobile = vm::sendOverMobile,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item(key = "capture") { CaptureRow(s.pending, onCamera) }
            val hint = jobHint(s.total, link.canSend, hasLink = link.gallery != null)
            if (s.total == 0) {
                item(key = "art") {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp), contentAlignment = Alignment.Center) { PsPairArt() }
                }
            }
            if (hint != null) {
                item(key = "hint") {
                    Text(
                        hint,
                        style = Ps.type.bodyS,
                        color = Ps.colors.inkSoft,
                        textAlign = if (s.total == 0) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Ps.space.gutter, vertical = 8.dp),
                    )
                }
            }
            s.sections.forEach { section ->
                item(key = "head-" + section.key) {
                    AreaHeader(
                        section,
                        onRename = { renaming = section.area },
                        onDelete = { deletingArea = section },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                items(section.pairs.chunked(2), key = { row -> "row-" + row.first().pair.id }) { row ->
                    Row(
                        Modifier.padding(horizontal = Ps.space.gutter).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { pair -> PsPairTile(pair, onClick = { onOpenPair(pair.pair.id) }, modifier = Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (confirmClose) {
        PsConfirmDialog(
            title = "Close this link?",
            text = "Your client will see a short \"this page was closed\" note, and the photos are deleted from the server. They stay on this phone.",
            confirm = "Close link",
            onConfirm = { confirmClose = false; vm.close() },
            onDismiss = { confirmClose = false },
            destructive = true,
            dismiss = "Keep it",
        )
    }
    if (confirmDelete) {
        PsConfirmDialog(
            title = "Delete this job?",
            text = "Its ${s.total * 2} or so photos are deleted from this phone too. This can't be undone.",
            confirm = "Delete",
            onConfirm = { confirmDelete = false; vm.deleteJob(onBack) },
            onDismiss = { confirmDelete = false },
            destructive = true,
            dismiss = "Keep",
        )
    }
    renaming?.let { area ->
        var name by remember(area.id) { mutableStateOf(area.name) }
        PsSheet("Rename label", onDismiss = { renaming = null }) {
            PsTextField(
                name, { name = it },
                label = "Label",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.renameArea(area.id, name); renaming = null }),
            )
            Spacer(Modifier.height(16.dp))
            PsButton("Save", { vm.renameArea(area.id, name); renaming = null }, fullWidth = true, enabled = name.isNotBlank())
        }
    }
    deletingArea?.let { section ->
        PsConfirmDialog(
            title = "Remove the label \"${section.title}\"?",
            text = if (section.pairs.isEmpty()) "It has no photos." else "Its ${section.pairs.size} pairs stay, without a label.",
            confirm = "Remove",
            onConfirm = { section.area?.let { vm.deleteArea(it.id) }; deletingArea = null },
            onDismiss = { deletingArea = null },
            dismiss = "Keep",
        )
    }
    moment?.let { m ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { moment = null; LinkActions.share(context, m.message) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            SentMoment(m) {
                if (moment != null) {
                    moment = null
                    LinkActions.share(context, m.message)
                }
            }
        }
    }
}

/** "service · address" with Map and Call buttons when the job has them. No red link text. */
@Composable
private fun JobMeta(job: JobEntity) {
    val context = LocalContext.current
    val meta = jobMeta(job.service, job.address)
    if (meta == null && job.address == null && job.clientPhone == null) return
    Row(
        Modifier.fillMaxWidth().padding(start = Ps.space.gutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(meta ?: "", style = Ps.type.bodyS, color = Ps.colors.inkSoft, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
        job.address?.let { address ->
            PsIconButton(Icons.Outlined.Map, "Open the address in maps", {
                val uri = Uri.parse("geo:0,0?q=" + Uri.encode(address))
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            })
        }
        job.clientPhone?.let { phone ->
            PsIconButton(Icons.Outlined.Call, "Call the client", {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))) }
            })
        }
    }
}

/** Before and After, the next one in ink: After while befores wait for theirs, else Before. */
@Composable
private fun CaptureRow(pending: Int, onCamera: (after: Boolean, pairId: String?) -> Unit) {
    val afterNext = pending > 0
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Ps.space.gutter).padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PsButton(
            "Before",
            { onCamera(false, null) },
            Modifier.weight(1f),
            variant = if (afterNext) PsButtonVariant.Secondary else PsButtonVariant.Primary,
            icon = Icons.Outlined.CameraAlt,
            height = 56.dp,
        )
        PsButton(
            if (afterNext) "After · $pending left" else "After",
            { onCamera(true, null) },
            Modifier.weight(1f),
            variant = if (afterNext) PsButtonVariant.Primary else PsButtonVariant.Secondary,
            icon = Icons.Outlined.CameraAlt,
            enabled = afterNext,
            height = 56.dp,
        )
    }
}

/** A label's header: its name, "done/total" and, for a real label, a menu. */
@Composable
private fun AreaHeader(section: AreaSection, onRename: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    PsSectionHeader(section.title, modifier) {
        if (section.pairs.isNotEmpty()) {
            val done = section.pairs.count { !it.needsAfter }
            Text(
                "$done/${section.pairs.size}",
                style = Ps.type.mono.tabular(),
                color = Ps.colors.inkFaint,
                modifier = Modifier
                    .padding(end = if (section.area == null) 12.dp else 0.dp)
                    .semantics { contentDescription = "$done of ${section.pairs.size} done" },
            )
        }
        if (section.area != null) {
            Box {
                PsIconButton(Icons.Outlined.MoreVert, "Options for ${section.title}", { menu = true })
                PsMenu(menu, { menu = false }) {
                    PsMenuItem("Rename label", { menu = false; onRename() }, icon = Icons.Outlined.Edit)
                    PsMenuItem("Remove label", { menu = false; onDelete() }, icon = Icons.Outlined.Delete)
                }
            }
        }
    }
}
