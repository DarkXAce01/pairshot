package com.greasepencil.pairshot.ui.pair

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.greasepencil.pairshot.data.db.AreaEntity
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.data.repo.PhotoImporter
import com.greasepencil.pairshot.domain.AreaPresets
import com.greasepencil.pairshot.domain.CollageFrame
import com.greasepencil.pairshot.domain.CollageLayout
import com.greasepencil.pairshot.ui.components.BeforeAfterSlider
import com.greasepencil.pairshot.ui.components.CollageExporter
import com.greasepencil.pairshot.ui.components.LabelSheet
import com.greasepencil.pairshot.ui.components.ps.PsBottomBar
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsConfirmDialog
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSnackbarHost
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.nav.PairRoute
import com.greasepencil.pairshot.ui.theme.Ps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairState(val loaded: Boolean = false, val pair: PairRow? = null)

@HiltViewModel
class PairViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: JobRepository,
    private val importer: PhotoImporter,
    private val settingsStore: SettingsStore,
    cloud: com.greasepencil.pairshot.data.cloud.CloudSession,
) : ViewModel() {
    private val pairId = handle.toRoute<PairRoute>().pairId
    val state: StateFlow<PairState> = repo.observePair(pairId).map { PairState(true, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PairState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val labels: StateFlow<List<AreaEntity>> = state.map { it.pair?.pair?.jobId }.distinctUntilChanged()
        .flatMapLatest { jobId -> if (jobId == null) flowOf(emptyList()) else repo.observeAreas(jobId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestions = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            suggestions.value = AreaPresets.rank(settingsStore.settings.first().trade, repo.usedAreaNames())
        }
    }

    fun setLabel(areaId: String?) = viewModelScope.launch { repo.setPairArea(pairId, areaId) }
    fun newLabel(name: String) = viewModelScope.launch {
        val jobId = state.value.pair?.pair?.jobId ?: return@launch
        if (name.isBlank()) return@launch
        repo.setPairArea(pairId, repo.addArea(jobId, name).id)
    }
    fun importAfter(uri: Uri, failed: () -> Unit) = viewModelScope.launch {
        if (!importer.importAfter(pairId, uri)) failed()
    }
    /** Settings, with Pro taken from the account (it decides the collage mark). */
    val settings: StateFlow<AppSettings> = kotlinx.coroutines.flow.combine(settingsStore.settings, cloud.state) { s, c -> s.copy(isPro = c.isPro) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** The other pairs in this job, for re-pairing, and this pair's place within its label. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val jobPairs: StateFlow<List<PairRow>> = state.map { it.pair?.pair?.jobId }.distinctUntilChanged()
        .flatMapLatest { jobId -> if (jobId == null) flowOf(emptyList()) else repo.observePairs(jobId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun move(delta: Int) = viewModelScope.launch { repo.movePair(pairId, delta) }
    fun giveAfterTo(otherPairId: String) = viewModelScope.launch { repo.swapAfters(pairId, otherPairId) }
    fun setCrop(c: com.greasepencil.pairshot.domain.Crop) = viewModelScope.launch { repo.setCrop(pairId, c) }

    fun setCollageLayout(style: CollageLayout.Style) = viewModelScope.launch { settingsStore.setCollageLayout(style.name) }

    fun setCover() = viewModelScope.launch { repo.setCover(pairId) }
    fun setHidden(h: Boolean) = viewModelScope.launch { repo.setHidden(pairId, h) }
    fun swap() = viewModelScope.launch { repo.swap(pairId) }
    fun delete() = viewModelScope.launch { repo.deletePair(pairId) }
}

@Composable
fun PairScreen(
    onBack: () -> Unit,
    onRetakeAfter: (jobId: String, pairId: String) -> Unit,
    onSeePro: () -> Unit = {},
    vm: PairViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareSheet by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var labelSheet by remember { mutableStateOf(false) }
    var moreSheet by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf(false) }
    var rePairing by remember { mutableStateOf(false) }
    val jobPairs by vm.jobPairs.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val pickAfter = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importAfter(uri) { scope.launch { snackbar.showSnackbar("That photo couldn't be read.") } }
    }
    val launchPickAfter = { pickAfter.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(s.loaded, s.pair) { if (s.loaded && s.pair == null) onBack() }
    val row = s.pair ?: return
    val p = row.pair
    val siblings = jobPairs.filter { it.pair.areaId == p.areaId }
    val menu = moreMenu(
        siblings = siblings.size,
        index = siblings.indexOfFirst { it.pair.id == p.id },
        jobPairs = jobPairs.size,
        hasAfter = !row.needsAfter,
    )

    PsScaffold(
        topBar = { PsTopBar(row.areaName ?: "No label", onBack = onBack) },
        bottomBar = {
            PsBottomBar {
                if (row.needsAfter) {
                    PsButton("Shoot the after", { onRetakeAfter(p.jobId, p.id) }, Modifier.weight(1f), icon = Icons.Outlined.CameraAlt)
                } else {
                    PsButton(
                        if (exporting) "Making image…" else "Share as one image",
                        { shareSheet = true },
                        Modifier.weight(1f),
                        variant = PsButtonVariant.Secondary,
                        icon = Icons.Outlined.Share,
                        busy = exporting,
                    )
                }
            }
        },
        snackbarHost = { PsSnackbarHost(snackbar) },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BeforeAfterSlider(
                row.beforeFile, row.beforeRotation, row.afterFile, row.afterRotation ?: 0,
                Modifier.fillMaxWidth().padding(horizontal = Ps.space.gutter),
            )
            PairTools(
                cropped = p.isCropped,
                cover = p.isCover,
                hidden = p.hidden,
                onLabel = { labelSheet = true },
                onCrop = { cropping = true },
                onCover = vm::setCover,
                onHidden = vm::setHidden,
            )
            PsListBlock {
                if (row.needsAfter) {
                    row {
                        PsListRow(
                            "Pick the after from your gallery",
                            leading = { Icon(Icons.Outlined.PhotoLibrary, null, tint = Ps.colors.inkSoft) },
                            onClick = launchPickAfter,
                        )
                    }
                } else {
                    row {
                        PsListRow(
                            "Retake the after",
                            leading = { Icon(Icons.Outlined.CameraAlt, null, tint = Ps.colors.inkSoft) },
                            onClick = { onRetakeAfter(p.jobId, p.id) },
                        )
                    }
                }
                row {
                    PsListRow(
                        "More",
                        leading = { Icon(Icons.Outlined.MoreHoriz, null, tint = Ps.colors.inkSoft) },
                        onClick = { moreSheet = true },
                    )
                }
            }
        }
    }

    if (moreSheet) {
        MoreSheet(
            menu,
            onPick = { item ->
                moreSheet = false
                when (item) {
                    MoreItem.EARLIER -> vm.move(-1)
                    MoreItem.LATER -> vm.move(1)
                    MoreItem.RE_PAIR -> rePairing = true
                    MoreItem.SWAP -> vm.swap()
                    MoreItem.PICK_AFTER -> launchPickAfter()
                    MoreItem.DELETE -> confirmDelete = true
                }
            },
            onDismiss = { moreSheet = false },
        )
    }
    if (shareSheet) {
        val look = remember(settings.clientTemplate, settings.accent, settings.businessName, settings.isPro) {
            CollageExporter.Look(
                CollageFrame.of(settings.clientTemplate, settings.accent),
                settings.businessName,
                watermark = !settings.isPro,
            )
        }
        ShareImageSheet(
            pair = row,
            look = look,
            initial = CollageLayout.Style.fromStored(settings.collageLayout),
            busy = exporting,
            onSelect = vm::setCollageLayout,
            onShare = { style ->
                exporting = true
                scope.launch {
                    runCatching { CollageExporter.export(context, row, style, look) }
                        .onSuccess { shareSheet = false; CollageExporter.share(context, it) }
                        .onFailure { shareSheet = false; snackbar.showSnackbar("Couldn't make the image. Try again.") }
                    exporting = false
                }
            },
            onSeePro = { shareSheet = false; onSeePro() },
            onDismiss = { if (!exporting) shareSheet = false },
        )
    }
    if (cropping) {
        val (w, h) = com.greasepencil.pairshot.domain.Rotation.size(row.beforeWidth, row.beforeHeight, row.beforeRotation)
        CropEditor(
            file = row.beforeFile,
            rotation = row.beforeRotation,
            photoAspect = if (w > 0 && h > 0) w.toFloat() / h else com.greasepencil.pairshot.ui.components.displayAspect(row.beforeRotation),
            initial = com.greasepencil.pairshot.domain.Crop.of(p),
            onSave = { vm.setCrop(it); cropping = false },
            onDismiss = { cropping = false },
        )
    }
    if (rePairing) {
        RePairSheet(
            pairs = jobPairs.filter { it.pair.id != p.id },
            hasAfter = !row.needsAfter,
            onPick = { vm.giveAfterTo(it); rePairing = false; scope.launch { snackbar.showSnackbar("Afters swapped.") } },
            onDismiss = { rePairing = false },
        )
    }
    if (labelSheet) {
        LabelSheet(
            current = p.areaId,
            jobLabels = labels,
            suggestions = suggestions,
            onPickExisting = { vm.setLabel(it); labelSheet = false },
            onNew = { vm.newLabel(it); labelSheet = false },
            onDismiss = { labelSheet = false },
        )
    }
    if (confirmDelete) {
        PsConfirmDialog(
            title = "Delete this pair?",
            text = "Both photos are deleted from this phone.",
            confirm = "Delete",
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
            destructive = true,
            dismiss = "Keep",
        )
    }
}
