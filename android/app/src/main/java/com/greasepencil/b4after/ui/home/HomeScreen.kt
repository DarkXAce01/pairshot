package com.greasepencil.b4after.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.greasepencil.b4after.data.db.JobSummary
import com.greasepencil.b4after.data.repo.Clock
import com.greasepencil.b4after.data.repo.JobRepository
import com.greasepencil.b4after.domain.HomeOrder
import com.greasepencil.b4after.ui.components.PhotoImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeState(
    val loaded: Boolean = false,
    val query: String = "",
    val today: List<JobSummary> = emptyList(),
    val recent: List<JobSummary> = emptyList(),
    val totalJobs: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(repo: JobRepository, clock: Clock) : ViewModel() {
    val query = MutableStateFlow("")
    val state: StateFlow<HomeState> = combine(repo.observeSummaries(), query) { jobs, q ->
        val filtered = jobs.filter { HomeOrder.matches(it, q) }
        val sections = HomeOrder.sections(filtered, clock.now())
        HomeState(true, q, sections.today, sections.recent, jobs.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenJob: (String) -> Unit,
    onNewJob: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jobs", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewJob,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New job") },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.totalJobs > 3) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text("Search client, address, service") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }
            if (state.totalJobs == 0) {
                item { EmptyHome() }
            } else if (state.today.isEmpty() && state.recent.isEmpty()) {
                item {
                    Text("Nothing matches \"${state.query}\".", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.today.isNotEmpty()) {
                item { SectionLabel("TODAY") }
                items(state.today, key = { it.job.id }) { JobCard(it) { onOpenJob(it.job.id) } }
            }
            if (state.recent.isNotEmpty()) {
                item { SectionLabel(if (state.today.isEmpty()) "RECENT" else "OTHER JOBS") }
                items(state.recent, key = { it.job.id }) { JobCard(it) { onOpenJob(it.job.id) } }
            }
        }
    }
}

@Composable
private fun EmptyHome() {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No jobs yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap New job, shoot your befores, then your afters. They pair up on their own.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun JobCard(summary: JobSummary, onClick: () -> Unit) {
    val job = summary.job
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            summary.coverFile?.let { PhotoImage(it, summary.coverRotation ?: 0, Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(job.clientName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(job.service, job.address).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(progressText(summary), style = MaterialTheme.typography.labelSmall, color = progressColor(summary))
        }
    }
}

fun progressText(s: JobSummary): String = when {
    s.pairCount == 0 -> "NO PHOTOS YET"
    s.doneCount == s.pairCount -> "ALL ${s.pairCount} PAIRS DONE"
    else -> "${s.doneCount} OF ${s.pairCount} AFTERS"
}

@Composable
private fun progressColor(s: JobSummary) =
    if (s.pairCount > 0 && s.doneCount == s.pairCount) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
