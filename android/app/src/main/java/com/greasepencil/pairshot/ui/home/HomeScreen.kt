package com.greasepencil.pairshot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.greasepencil.pairshot.data.db.JobSummary
import com.greasepencil.pairshot.data.repo.Clock
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.domain.HomeOrder
import com.greasepencil.pairshot.domain.JobStatus
import com.greasepencil.pairshot.ui.brand.BrandViewModel
import com.greasepencil.pairshot.ui.components.ps.PsBanner
import com.greasepencil.pairshot.ui.components.ps.PsBannerTone
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsEmptyState
import com.greasepencil.pairshot.ui.components.ps.PsFab
import com.greasepencil.pairshot.ui.components.ps.PsIconButton
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSearchField
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsTabHeader
import com.greasepencil.pairshot.ui.components.ps.psListBlockItems
import com.greasepencil.pairshot.ui.theme.Ps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val loaded: Boolean = false,
    val query: String = "",
    val today: List<JobSummary> = emptyList(),
    val recent: List<JobSummary> = emptyList(),
    val totalJobs: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: JobRepository,
    clock: Clock,
    crm: com.greasepencil.pairshot.data.cloud.CrmSync,
    private val storage: com.greasepencil.pairshot.data.repo.LocalStorage,
) : ViewModel() {
    /** Free space on the phone when it's under 1 GB (null when there's plenty). Checked each time Home shows. */
    val lowSpace = MutableStateFlow<Long?>(null)
    fun checkSpace() { lowSpace.value = storage.freeBytes().takeIf { it < com.greasepencil.pairshot.data.repo.LocalStorage.LOW_BYTES } }

    init {
        // New jobs from the CRM (Pro). Quiet: offline or free just means nothing happens.
        viewModelScope.launch { runCatching { crm.pull() } }
    }

    val query = MutableStateFlow("")
    val state: StateFlow<HomeState> = combine(repo.observeSummaries(), query) { jobs, q ->
        val filtered = jobs.filter { HomeOrder.matches(it, q) }
        val sections = HomeOrder.sections(filtered, clock.now())
        HomeState(true, q, sections.today, sections.recent, jobs.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

/** Section names: "Today" and "Earlier", or "Recent" when nothing is on today. */
internal fun earlierSectionName(hasToday: Boolean): String = if (hasToday) "Earlier" else "Recent"

@Composable
fun HomeScreen(
    onOpenJob: (String) -> Unit,
    onNewJob: () -> Unit,
    onFreeUpSpace: () -> Unit,
    /** Debug builds only, until the Developer page exists: long-press "Jobs" for the component gallery. */
    onComponentGallery: (() -> Unit)? = null,
    vm: HomeViewModel = hiltViewModel(),
    // Only for the business name over the title; HomeViewModel stays as it is.
    brand: BrandViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lowSpace by vm.lowSpace.collectAsStateWithLifecycle()
    val businessName = brand.settings.collectAsStateWithLifecycle().value?.businessName.orEmpty()
    LifecycleResumeEffect(Unit) { vm.checkSpace(); onPauseOrDispose { } }
    // The field keeps its own text so typing never jumps the cursor; the list follows vm.query.
    var query by rememberSaveable { mutableStateOf(vm.query.value) }
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    fun setQuery(q: String) { query = q; vm.query.value = q }
    fun stopSearching() { setQuery(""); searching = false }
    BackHandler(enabled = searching) { stopSearching() }

    PsScaffold {
        if (!state.loaded) return@PsScaffold
        val empty = state.totalJobs == 0
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (empty) 24.dp else 104.dp)) {
            item(key = "header") {
                if (searching) {
                    SearchHeader(query, ::setQuery, ::stopSearching)
                } else {
                    val galleryEntry = onComponentGallery?.let { open ->
                        Modifier.pointerInput(open) { detectTapGestures(onLongPress = { open() }) }
                    } ?: Modifier
                    PsTabHeader(
                        "Jobs",
                        eyebrow = businessName.ifBlank { null },
                        titleModifier = galleryEntry,
                        actions = {
                            if (state.totalJobs > 3) PsIconButton(Icons.Outlined.Search, "Search jobs", { searching = true })
                        },
                    )
                }
            }
            lowSpace?.let { free ->
                item(key = "lowSpace") {
                    PsBanner(
                        "Phone storage is nearly full",
                        "Only ${"%.0f".format(free / 1e6)} MB left. New photos might not save.",
                        Modifier.padding(top = 4.dp, bottom = 12.dp),
                        tone = PsBannerTone.Warning,
                        actionLabel = "Free up space",
                        onAction = onFreeUpSpace,
                    )
                }
            }
            if (empty) {
                item(key = "empty") {
                    PsEmptyState(
                        "No jobs yet",
                        "Add a job, shoot the befores, then the afters. They pair up on their own.",
                        Modifier.padding(top = 32.dp),
                        actionLabel = "New job",
                        onAction = onNewJob,
                    )
                }
            } else if (state.today.isEmpty() && state.recent.isEmpty()) {
                item(key = "noMatch") {
                    Text(
                        "Nothing matches \"${state.query}\".",
                        Modifier.padding(start = Ps.space.gutter, end = Ps.space.gutter, top = 24.dp),
                        style = Ps.type.bodyS,
                        color = Ps.colors.inkSoft,
                    )
                }
            }
            if (state.today.isNotEmpty()) {
                item(key = "todayHeader") { PsSectionHeader("Today", Modifier.padding(top = 12.dp)) }
                psListBlockItems(state.today, key = { it.job.id }) { JobRow(it, { onOpenJob(it.job.id) }) }
            }
            if (state.recent.isNotEmpty()) {
                item(key = "recentHeader") {
                    val gap = if (state.today.isEmpty()) 12.dp else Ps.space.sectionGap
                    PsSectionHeader(earlierSectionName(state.today.isNotEmpty()), Modifier.padding(top = gap))
                }
                psListBlockItems(state.recent, key = { it.job.id }) { JobRow(it, { onOpenJob(it.job.id) }) }
            }
        }
        if (!empty) {
            PsFab(
                "New job",
                Icons.Outlined.Add,
                onNewJob,
                Modifier.align(Alignment.BottomEnd).padding(end = Ps.space.gutter, bottom = 16.dp),
            )
        }
    }
}

/** Search in place of the header: the field, focused, and Cancel to go back to the header. */
@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(start = Ps.space.gutter, end = 8.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PsSearchField(
            query,
            onQuery,
            Modifier.weight(1f),
            placeholder = "Search client, address, service",
            fieldModifier = Modifier.focusRequester(focus),
        )
        Spacer(Modifier.width(4.dp))
        PsButton("Cancel", onCancel, variant = PsButtonVariant.Quiet)
    }
}

fun statusOf(s: JobSummary): JobStatus = JobStatus.of(
    pairCount = s.pairCount,
    doneCount = s.doneCount,
    linkSent = s.linkRemoteState != null,
    linkLive = s.linkRemoteState == "live",
    viewed = s.linkViewedAt != null,
)

fun progressText(s: JobSummary): String = when {
    s.pairCount == 0 -> "NO PHOTOS YET"
    s.doneCount == s.pairCount -> "ALL ${s.pairCount} PAIRS DONE"
    else -> "${s.doneCount} OF ${s.pairCount} AFTERS"
}

