package com.greasepencil.pairshot.ui.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.greasepencil.pairshot.data.db.AreaEntity
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.data.prefs.GhostMode
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.data.repo.CapturedPhoto
import com.greasepencil.pairshot.data.repo.JobRepository
import com.greasepencil.pairshot.data.repo.PhotoFiles
import com.greasepencil.pairshot.domain.AfterQueue
import com.greasepencil.pairshot.domain.AreaPresets
import com.greasepencil.pairshot.domain.Ids
import com.greasepencil.pairshot.ui.nav.CameraRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** What the last shot was, so it can be undone. */
sealed interface LastShot {
    val file: String
    val rotation: Int
    data class Before(val pairId: String, override val file: String, override val rotation: Int) : LastShot
    data class After(val pairId: String, override val file: String, override val rotation: Int) : LastShot
}

data class CameraUi(
    val loaded: Boolean = false,
    val after: Boolean = false,
    val areas: List<AreaEntity> = emptyList(),
    /** The label new befores get; null = no label. */
    val selectedAreaId: String? = null,
    /** After mode: the pair being shot now, or null when the queue is done. */
    val target: PairRow? = null,
    val pendingCount: Int = 0,
    val shotsThisSession: Int = 0,
    val lastShot: LastShot? = null,
    val ghostStrength: Float = 0.5f,
    val ghostMode: GhostMode = GhostMode.OUTLINE,
    val showAreaPicker: Boolean = false,
    val presets: List<String> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: JobRepository,
    private val files: PhotoFiles,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val route = handle.toRoute<CameraRoute>()
    val jobId = route.jobId

    private val local = MutableStateFlow(
        Local(targetId = route.pairId),
    )

    private data class Local(
        /** The label new befores get; null = no label. */
    val selectedAreaId: String? = null,
        val targetId: String? = null,
        val lastFinishedId: String? = null,
        val shots: Int = 0,
        val lastShot: LastShot? = null,
        val showAreaPicker: Boolean = false,
        val presets: List<String> = emptyList(),
        val ghostOverride: Pair<Float, GhostMode>? = null,
        val error: String? = null,
    )

    val ui: StateFlow<CameraUi> = combine(
        repo.observeAreas(jobId), repo.observePairs(jobId), settingsStore.settings, local,
    ) { areas, pairs, settings, l ->
        val selected = l.selectedAreaId?.takeIf { id -> areas.any { it.id == id } }
        val target = if (route.after) {
            // An explicit target (retake / shoot this one) wins even if it already has an after.
            pairs.firstOrNull { it.pair.id == l.targetId }
                ?: AfterQueue.next(pairs, preferredId = null, justFinishedId = l.lastFinishedId)
        } else null
        val ghost = l.ghostOverride ?: (settings.ghostStrength to settings.ghostMode)
        CameraUi(
            loaded = true,
            after = route.after,
            areas = areas,
            selectedAreaId = selected,
            target = target,
            pendingCount = AfterQueue.pending(pairs).size,
            shotsThisSession = l.shots,
            lastShot = l.lastShot,
            ghostStrength = ghost.first,
            ghostMode = ghost.second,
            showAreaPicker = l.showAreaPicker,
            presets = l.presets,
            error = l.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraUi(after = route.after))

    init {
        viewModelScope.launch {
            val trade = settingsStore.settings.first().trade
            local.update { it.copy(presets = AreaPresets.rank(trade, repo.usedAreaNames())) }
        }
    }

    fun selectArea(id: String?) = local.update { it.copy(selectedAreaId = id) }
    fun openAreaPicker() = local.update { it.copy(showAreaPicker = true) }
    fun closeAreaPicker() = local.update { it.copy(showAreaPicker = false) }

    fun addArea(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val area = repo.addArea(jobId, name)
            local.update { it.copy(selectedAreaId = area.id, showAreaPicker = false) }
        }
    }

    fun setGhost(strength: Float, mode: GhostMode) = local.update { it.copy(ghostOverride = strength to mode) }
    fun saveGhostAsDefault() = viewModelScope.launch {
        settingsStore.setGhost(ui.value.ghostStrength, ui.value.ghostMode)
    }

    fun clearError() = local.update { it.copy(error = null) }

    /** A fresh file for the next shot. */
    fun newShotFile(): Pair<String, File> {
        val id = Ids.new()
        return id to files.newFile(id)
    }

    /**
     * The camera wrote a photo. [targetPairId]/[areaId] are what the screen showed when the shutter was
     * pressed, so a slow save can't land on the wrong pair.
     */
    fun onSaved(id: String, file: File, zoom: Float, uprightRotation: Int, areaId: String?, targetPairId: String?) {
        viewModelScope.launch {
            runCatching {
                val (w, h) = files.displaySize(file)
                val shot = CapturedPhoto(id, file, w, h, zoom, uprightRotation, System.currentTimeMillis())
                if (route.after) {
                    val pairId = requireNotNull(targetPairId) { "no pair to put the after on" }
                    repo.setAfter(pairId, shot)
                    local.update {
                        it.copy(
                            targetId = null,
                            lastFinishedId = pairId,
                            shots = it.shots + 1,
                            lastShot = LastShot.After(pairId, file.name, uprightRotation),
                        )
                    }
                } else {
                    val pairId = repo.addBefore(jobId, areaId, shot)
                    local.update {
                        it.copy(shots = it.shots + 1, lastShot = LastShot.Before(pairId, file.name, uprightRotation))
                    }
                }
            }.onFailure { e ->
                repo.discardFile(file)
                local.update { it.copy(error = "That photo didn't save (${e.message}). Try again.") }
            }
        }
    }

    fun onCaptureFailed(file: File, message: String?) {
        repo.discardFile(file)
        local.update { it.copy(error = "The camera couldn't take that one${message?.let { m -> " ($m)" } ?: ""}. Try again.") }
    }

    fun undo() {
        val last = local.value.lastShot ?: return
        viewModelScope.launch {
            when (last) {
                is LastShot.Before -> repo.deletePair(last.pairId)
                is LastShot.After -> {
                    repo.clearAfter(last.pairId)
                    local.update { it.copy(targetId = last.pairId) }
                }
            }
            local.update { it.copy(lastShot = null, shots = (it.shots - 1).coerceAtLeast(0)) }
        }
    }
}
