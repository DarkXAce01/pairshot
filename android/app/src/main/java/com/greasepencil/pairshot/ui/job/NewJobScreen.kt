package com.greasepencil.pairshot.ui.job

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.greasepencil.pairshot.data.repo.Clock
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.domain.Schedule
import com.greasepencil.pairshot.ui.components.ps.PsBottomBar
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.nav.NewJobRoute
import com.greasepencil.pairshot.ui.theme.Ps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JobForm(
    val loading: Boolean = false,
    val clientName: String = "",
    val service: String = "",
    val address: String = "",
    val phone: String = "",
    val day: Schedule.Day = Schedule.Day.TODAY,
    val saving: Boolean = false,
    val savedId: String? = null,
) {
    val canSave get() = clientName.isNotBlank() && !saving
}

@HiltViewModel
class NewJobViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: JobRepository,
    private val clock: Clock,
) : ViewModel() {
    private val editId = handle.toRoute<NewJobRoute>().editJobId
    val isEdit = editId != null
    val form = MutableStateFlow(JobForm(loading = isEdit))

    init {
        if (editId != null) viewModelScope.launch {
            val job = repo.observeJob(editId).first() ?: return@launch
            form.value = JobForm(
                clientName = job.clientName,
                service = job.service.orEmpty(),
                address = job.address.orEmpty(),
                phone = job.clientPhone.orEmpty(),
                day = Schedule.dayOf(job.scheduledAt, clock.now()),
            )
        }
    }

    fun edit(f: (JobForm) -> JobForm) = form.update(f)

    fun save() {
        val f = form.value
        if (!f.canSave) return
        form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val scheduled = Schedule.startOf(f.day, clock.now())
            val id = if (editId == null) {
                repo.createJob(f.clientName, f.address, f.service, scheduled, f.phone)
            } else {
                val job = repo.observeJob(editId).first() ?: return@launch
                repo.updateJob(
                    job.copy(
                        clientName = f.clientName.trim(),
                        service = f.service.trim().ifEmpty { null },
                        address = f.address.trim().ifEmpty { null },
                        clientPhone = f.phone.trim().ifEmpty { null },
                        scheduledAt = if (f.day == Schedule.dayOf(job.scheduledAt, clock.now())) job.scheduledAt else scheduled,
                    ),
                )
                editId
            }
            form.update { it.copy(saving = false, savedId = id) }
        }
    }
}

@Composable
fun NewJobScreen(onBack: () -> Unit, onCreated: (String) -> Unit, vm: NewJobViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(f.savedId) {
        f.savedId?.let { if (vm.isEdit) onBack() else onCreated(it) }
    }
    val gutter = Modifier.padding(horizontal = Ps.space.gutter)
    PsScaffold(
        topBar = { PsTopBar(if (vm.isEdit) "Edit job" else "New job", onBack = onBack) },
        // PsBottomBar pads for the keyboard itself, so the button stays above it.
        bottomBar = {
            PsBottomBar {
                PsButton(
                    if (vm.isEdit) "Save" else "Create job",
                    vm::save,
                    Modifier.weight(1f),
                    enabled = f.clientName.isNotBlank(),
                    busy = f.saving,
                )
            }
        },
    ) {
        if (f.loading) return@PsScaffold
        val nameFocus = remember { FocusRequester() }
        if (!vm.isEdit) LaunchedEffect(Unit) { nameFocus.requestFocus() }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Ps.space.md),
        ) {
            PsTextField(
                f.clientName, { v -> vm.edit { it.copy(clientName = v) } }, gutter,
                label = "Client name",
                fieldModifier = Modifier.focusRequester(nameFocus),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            PsTextField(
                f.service, { v -> vm.edit { it.copy(service = v) } }, gutter,
                label = "Service", labelTrailing = "optional", placeholder = "House wash",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            PsTextField(
                f.address, { v -> vm.edit { it.copy(address = v) } }, gutter,
                label = "Address", labelTrailing = "optional",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            PsTextField(
                f.phone, { v -> vm.edit { it.copy(phone = v) } }, gutter,
                label = "Client phone", labelTrailing = "optional",
                helper = "Lets you text the link straight to them.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            )
            Column {
                PsSectionHeader("When", Modifier.padding(top = 8.dp))
                PsChipRow(gutter) {
                    Schedule.Day.entries.forEach { d ->
                        PsChip(d.label, selected = f.day == d, onClick = { vm.edit { it.copy(day = d) } })
                    }
                }
            }
        }
    }
}
