package com.greasepencil.pairshot.ui.pair

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.outlined.Label
import com.greasepencil.pairshot.data.db.AreaEntity
import com.greasepencil.pairshot.data.repo.PhotoImporter
import com.greasepencil.pairshot.domain.AreaPresets
import com.greasepencil.pairshot.ui.components.LabelSheet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.domain.CollageLayout
import com.greasepencil.pairshot.ui.components.BeforeAfterSlider
import com.greasepencil.pairshot.ui.components.CollageExporter
import com.greasepencil.pairshot.ui.nav.PairRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun setCover() = viewModelScope.launch { repo.setCover(pairId) }
    fun setHidden(h: Boolean) = viewModelScope.launch { repo.setHidden(pairId, h) }
    fun swap() = viewModelScope.launch { repo.swap(pairId) }
    fun delete() = viewModelScope.launch { repo.deletePair(pairId) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PairScreen(
    onBack: () -> Unit,
    onRetakeAfter: (jobId: String, pairId: String) -> Unit,
    vm: PairViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var labelSheet by remember { mutableStateOf(false) }
    val labels by vm.labels.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val pickAfter = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importAfter(uri) { scope.launch { snackbar.showSnackbar("That photo couldn't be read.") } }
    }

    LaunchedEffect(s.loaded, s.pair) { if (s.loaded && s.pair == null) onBack() }
    val row = s.pair ?: return
    val p = row.pair

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(row.areaName ?: "No label", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete pair") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BeforeAfterSlider(
                row.beforeFile, row.beforeRotation, row.afterFile, row.afterRotation ?: 0,
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            if (row.needsAfter) {
                Button(onClick = { onRetakeAfter(p.jobId, p.id) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Text("  Shoot the after")
                }
                TextButton(
                    onClick = { pickAfter.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Or pick the after from your gallery") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { labelSheet = true },
                    label = { Text(row.areaName?.let { "Label: $it" } ?: "Add label") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                )
                AssistChip(
                    onClick = vm::setCover,
                    label = { Text(if (p.isCover) "Cover photo" else "Make cover") },
                    leadingIcon = { Icon(if (p.isCover) Icons.Filled.Star else Icons.Outlined.StarOutline, null) },
                )
                AssistChip(
                    onClick = { vm.setHidden(!p.hidden) },
                    label = { Text(if (p.hidden) "Hidden from client" else "Shown to client") },
                    leadingIcon = { Icon(if (p.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) },
                )
                if (!row.needsAfter) {
                    AssistChip(onClick = vm::swap, label = { Text("Swap") }, leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) })
                    AssistChip(
                        onClick = { onRetakeAfter(p.jobId, p.id) },
                        label = { Text("Retake after") },
                        leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) },
                    )
                }
            }
            if (!row.needsAfter) {
                Column {
                    Button(onClick = { exportMenu = true }, enabled = !exporting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Outlined.Share, null)
                        Text(if (exporting) "  Making image…" else "  Share as one image")
                    }
                    DropdownMenu(exportMenu, onDismissRequest = { exportMenu = false }) {
                        listOf(
                            CollageLayout.Style.SIDE_BY_SIDE to "Side by side",
                            CollageLayout.Style.STACKED to "Before on top",
                        ).forEach { (style, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                exportMenu = false
                                exporting = true
                                scope.launch {
                                    runCatching {
                                        CollageExporter.export(context, row, style, settings.businessName, watermark = !settings.isPro)
                                    }.onSuccess { CollageExporter.share(context, it) }
                                        .onFailure { snackbar.showSnackbar("Couldn't make the image. Try again.") }
                                    exporting = false
                                }
                            })
                        }
                    }
                }
            }
        }
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
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this pair?") },
            text = { Text("Both photos are deleted from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
