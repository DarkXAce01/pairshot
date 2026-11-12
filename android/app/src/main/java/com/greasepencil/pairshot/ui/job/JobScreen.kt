package com.greasepencil.pairshot.ui.job

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.greasepencil.pairshot.data.db.AreaEntity
import com.greasepencil.pairshot.data.db.JobEntity
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.data.repo.PhotoImporter
import com.greasepencil.pairshot.domain.AfterQueue
import com.greasepencil.pairshot.ui.components.PhotoImage
import com.greasepencil.pairshot.ui.nav.JobRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) : ViewModel() {
    val jobId = handle.toRoute<JobRoute>().jobId

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

@OptIn(ExperimentalMaterial3Api::class)
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
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AreaEntity?>(null) }
    var deletingArea by remember { mutableStateOf<AreaSection?>(null) }

    // The job was deleted (here or elsewhere): leave.
    LaunchedEffect(s.loaded, s.job) { if (s.loaded && s.job == null) onBack() }
    val job = s.job ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(job.clientName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit job") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Add photos from gallery") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                                onClick = {
                                    menu = false
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete job") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { JobHeader(job) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onCamera(false, null) }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Outlined.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Before")
                    }
                    OutlinedButton(
                        onClick = { onCamera(true, null) },
                        enabled = s.pending > 0,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Outlined.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.pending > 0) "After · ${s.pending} left" else "After")
                    }
                }
            }
            if (s.completePairs > 0) {
                item {
                    OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Outlined.Language, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Preview the client page")
                    }
                }
            }
            if (s.total == 0) {
                item {
                    Text(
                        "No photos yet. Tap Before and shoot. Labels like \"Driveway\" are optional.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(s.sections, key = { it.key }) { section ->
                AreaBlock(
                    section,
                    onOpenPair = onOpenPair,
                    onRename = { renaming = section.area },
                    onDelete = { deletingArea = section },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this job?") },
            text = { Text("Its ${s.total * 2} or so photos are deleted from this phone too. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteJob(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
    renaming?.let { area ->
        var name by remember(area.id) { mutableStateOf(area.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename label") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renameArea(area.id, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deletingArea?.let { section ->
        AlertDialog(
            onDismissRequest = { deletingArea = null },
            title = { Text("Remove the label \"${section.title}\"?") },
            text = { Text(if (section.pairs.isEmpty()) "It has no photos." else "Its ${section.pairs.size} pairs stay, without a label.") },
            confirmButton = { TextButton(onClick = { section.area?.let { vm.deleteArea(it.id) }; deletingArea = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deletingArea = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun JobHeader(job: JobEntity) {
    val context = LocalContext.current
    Column {
        job.service?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        job.address?.let { address ->
            Text(
                address,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val uri = Uri.parse("geo:0,0?q=" + Uri.encode(address))
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
            )
        }
        job.clientPhone?.let { phone ->
            Text(
                phone,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))) }
                },
            )
        }
    }
}

@Composable
private fun AreaBlock(
    section: AreaSection,
    onOpenPair: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (section.area == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            val done = section.pairs.count { !it.needsAfter }
            if (section.pairs.isNotEmpty()) {
                Text("$done/${section.pairs.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (section.area != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Label options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename label") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Remove label") }, onClick = { menu = false; onDelete() })
                    }
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
        section.pairs.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { pair ->
                    PairTile(
                        pair,
                        Modifier.weight(1f),
                        onClick = { onOpenPair(pair.pair.id) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PairTile(pair: PairRow, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .alpha(if (pair.pair.hidden) 0.45f else 1f),
    ) {
        Row(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
            PhotoImage(pair.beforeFile, pair.beforeRotation, Modifier.weight(1f).fillMaxSize())
            Spacer(Modifier.width(2.dp))
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (pair.afterFile != null) {
                    PhotoImage(pair.afterFile, pair.afterRotation ?: 0, Modifier.fillMaxSize())
                } else {
                    Text(
                        "NEEDS\nAFTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (pair.pair.isCover) Badge { Icon(Icons.Filled.Star, "Cover", Modifier.size(14.dp), tint = Color.White) }
            if (pair.pair.hidden) Badge { Icon(Icons.Outlined.VisibilityOff, "Hidden from client", Modifier.size(14.dp), tint = Color.White) }
        }
    }
}

@Composable
private fun Badge(content: @Composable () -> Unit) {
    Box(Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(4.dp)) { content() }
}
