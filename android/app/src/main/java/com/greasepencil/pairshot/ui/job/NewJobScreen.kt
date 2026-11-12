package com.greasepencil.pairshot.ui.job

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.greasepencil.pairshot.ui.nav.NewJobRoute
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewJobScreen(onBack: () -> Unit, onCreated: (String) -> Unit, vm: NewJobViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(f.savedId) {
        f.savedId?.let { if (vm.isEdit) onBack() else onCreated(it) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Edit job" else "New job", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (f.loading) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                f.clientName, { v -> vm.edit { it.copy(clientName = v) } },
                label = { Text("Client name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                f.service, { v -> vm.edit { it.copy(service = v) } },
                label = { Text("Service (optional)") }, placeholder = { Text("House wash") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                f.address, { v -> vm.edit { it.copy(address = v) } },
                label = { Text("Address (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                f.phone, { v -> vm.edit { it.copy(phone = v) } },
                label = { Text("Client phone (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            )
            Text("When", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Schedule.Day.entries.forEach { d ->
                    FilterChip(selected = f.day == d, onClick = { vm.edit { it.copy(day = d) } }, label = { Text(d.label) })
                }
            }
            Button(onClick = vm::save, enabled = f.canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (vm.isEdit) "Save" else "Create job")
            }
        }
    }
}
