package com.greasepencil.b4after.ui.camera

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Volume-key presses, forwarded by the Activity while the camera is open so they work as a shutter. */
object VolumeKeys {
    @Volatile var cameraOpen: Boolean = false
    private val _presses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val presses: SharedFlow<Unit> = _presses.asSharedFlow()

    /** @return true when the press was used as a shutter (and should not change the volume). */
    fun press(): Boolean {
        if (!cameraOpen) return false
        _presses.tryEmit(Unit)
        return true
    }
}
