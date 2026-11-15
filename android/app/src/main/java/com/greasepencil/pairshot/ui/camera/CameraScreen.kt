package com.greasepencil.pairshot.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Rational
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.prefs.GhostMode
import com.greasepencil.pairshot.domain.Rotation
import com.greasepencil.pairshot.ui.components.PhotoImage
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipKind
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.components.ps.PsChipScroller
import com.greasepencil.pairshot.ui.components.ps.PsIconButton
import com.greasepencil.pairshot.ui.components.ps.PsSegmented
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.PsSlider
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.components.ps.psPressable
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.PsCameraColors
import com.greasepencil.pairshot.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val Dim = PsCameraColors.background
private val Ink = PsCameraColors.ink
private val Muted = PsCameraColors.muted

@Composable
fun CameraScreen(onClose: () -> Unit, vm: CameraViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Dim)) {
        if (granted) {
            CameraContent(vm, onClose)
        } else if (asked) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Pairshot needs the camera",
                    style = Ps.type.titleM,
                    color = Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "It's used only while this screen is open, for your before and after photos.",
                    style = Ps.type.bodyS,
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                PsButton(
                    "Open settings",
                    {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    onDark = true,
                )
                Spacer(Modifier.height(4.dp))
                PsButton("Go back", onClose, variant = PsButtonVariant.Quiet, onDark = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraContent(vm: CameraViewModel, onClose: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // ---- CameraX ---------------------------------------------------------------------------------
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val resolution = remember {
        ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
    }
    val preview = remember { Preview.Builder().setResolutionSelector(resolution).build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolution)
            // Always the phone's portrait frame, whatever way it's held: that's what makes the ghost line up.
            .setTargetRotation(Surface.ROTATION_0)
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val p = ProcessCameraProvider.awaitInstance(context)
        provider = p
        preview.setSurfaceProvider(previewView.surfaceProvider)
        // The preview box, the saved photo and the ghost all share this 3:4 frame.
        val viewPort = ViewPort.Builder(Rational(3, 4), Surface.ROTATION_0).setScaleType(ViewPort.FILL_CENTER).build()
        val group = UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).addUseCase(imageCapture).build()
        runCatching {
            p.unbindAll()
            camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
        }.onFailure { cameraError = "The camera didn't start (${it.message})." }
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    // Same zoom as the before, so the after is framed the same.
    val target = ui.target
    LaunchedEffect(camera, target?.pair?.id) {
        val cam = camera ?: return@LaunchedEffect
        if (ui.after && target != null) cam.cameraControl.setZoomRatio(target.beforeZoom)
    }

    // ---- Which way up the phone is ----------------------------------------------------------------
    var orientation by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(o: Int) {
                if (o != ORIENTATION_UNKNOWN) orientation = o
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    // ---- Shutter ----------------------------------------------------------------------------------
    var inFlight by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    val shoot = rememberUpdatedState {
        val s = vm.ui.value
        val areaId = s.selectedAreaId
        val pairId = s.target?.pair?.id
        when {
            camera == null -> Unit
            s.after && pairId == null -> Unit
            s.after && inFlight > 0 -> Unit // wait so the next after goes on the right pair
            inFlight >= 3 -> Unit
            else -> {
                val (id, file) = vm.newShotFile()
                val zoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val upright = Rotation.uprightFromOrientation(orientation)
                inFlight++
                scope.launch { flash.snapTo(0.6f); flash.animateTo(0f, tween(180)) }
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            inFlight--
                            vm.onSaved(id, file, zoom, upright, areaId, pairId)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            inFlight--
                            vm.onCaptureFailed(file, exception.message)
                        }
                    },
                )
            }
        }
    }
    DisposableEffect(Unit) {
        VolumeKeys.cameraOpen = true
        onDispose { VolumeKeys.cameraOpen = false }
    }
    LaunchedEffect(Unit) { VolumeKeys.presses.collect { shoot.value() } }

    LaunchedEffect(ui.error) { if (ui.error != null) { delay(3500); vm.clearError() } }

    // ---- Layout -----------------------------------------------------------------------------------
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopRow(ui, onClose)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(4.dp))) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                var peeking by remember { mutableStateOf(false) }
                if (ui.after && target != null) {
                    Ghost(target.beforeFile, ui.ghostStrength, ui.ghostMode, peeking)
                }
                GestureLayer(camera, previewView)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = flash.value)))
                ZoomBadge(camera, Modifier.align(Alignment.BottomCenter).padding(8.dp))
                if (ui.after && target != null) {
                    PeekButton(Modifier.align(Alignment.BottomEnd).padding(8.dp)) { peeking = it }
                }
                (cameraError ?: ui.error)?.let {
                    Text(
                        it, color = Ink, style = Ps.type.bodyS,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                            .background(PsCameraColors.overlay, Ps.shape.sm).padding(10.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (ui.after && target == null && ui.loaded) AllDone(onClose)
            }
        }
        if (ui.after) GhostControls(ui, vm) else AreaChips(ui, vm)
        ShutterRow(ui, enabled = camera != null && !(ui.after && target == null), onShoot = { shoot.value() }, onUndo = vm::undo, onDone = onClose)
    }

    if (ui.showAreaPicker) AreaPicker(ui, vm)
}

@Composable
private fun TopRow(ui: CameraUi, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PsIconButton(Icons.Outlined.Close, "Close camera", onClose, tint = Ink)
        Text(
            if (ui.after) "AFTER" else "BEFORE",
            color = Ink,
            style = Ps.type.eyebrow,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.weight(1f))
        val t = ui.target
        val info = if (ui.after) {
            if (t != null) "${t.areaName ?: "No label"} · ${ui.pendingCount} left" else ""
        } else {
            ui.areas.firstOrNull { it.id == ui.selectedAreaId }?.name ?: "No label"
        }
        Text(
            info, color = Muted, style = Ps.type.bodyS, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp).weight(1f, fill = false),
        )
    }
}

@Composable
private fun Ghost(beforeFile: String, strength: Float, mode: GhostMode, peeking: Boolean) {
    val context = LocalContext.current
    when {
        // Peeking always shows the real photo, even with the ghost off.
        peeking -> PhotoImage(beforeFile, rotation = 0, modifier = Modifier.fillMaxSize())
        mode == GhostMode.OUTLINE -> {
            val outline by produceState<ImageBitmap?>(null, beforeFile) { value = GhostLoader.outline(context, beforeFile) }
            outline?.let {
                Image(
                    it, contentDescription = null, contentScale = ContentScale.Crop,
                    alpha = 0.35f + 0.65f * strength, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // rotation 0: the before in the camera's own frame, exactly as its preview looked.
        mode == GhostMode.PHOTO -> PhotoImage(beforeFile, rotation = 0, modifier = Modifier.fillMaxSize(), alpha = strength * 0.8f)
        else -> Unit
    }
}

@Composable
private fun GestureLayer(camera: Camera?, previewView: PreviewView) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(camera) {
                detectTransformGestures { _, _, zoom, _ ->
                    val cam = camera ?: return@detectTransformGestures
                    val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    cam.cameraControl.setZoomRatio(current * zoom)
                }
            }
            .pointerInput(camera) {
                detectTapGestures { offset ->
                    val cam = camera ?: return@detectTapGestures
                    val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                    cam.cameraControl.startFocusAndMetering(
                        FocusMeteringAction.Builder(point).setAutoCancelDuration(4, TimeUnit.SECONDS).build(),
                    )
                }
            },
    )
}

@Composable
private fun ZoomBadge(camera: Camera?, modifier: Modifier) {
    val zoomState = camera?.cameraInfo?.zoomState ?: return
    var ratio by remember { mutableStateOf(zoomState.value?.zoomRatio ?: 1f) }
    DisposableEffect(zoomState) {
        val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { ratio = it.zoomRatio }
        zoomState.observeForever(observer)
        onDispose { zoomState.removeObserver(observer) }
    }
    Text(
        "%.1f×".format(ratio),
        color = Ink,
        style = Ps.type.chip.tabular(),
        modifier = modifier
            .background(PsCameraColors.overlay, Ps.shape.pill)
            .semantics { contentDescription = "Zoom %.1f times".format(ratio) }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun PeekButton(modifier: Modifier, onPeek: (Boolean) -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Hold to see the before" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPeek(true)
                    tryAwaitRelease()
                    onPeek(false)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "HOLD TO PEEK",
            color = Ink,
            style = Ps.type.eyebrow,
            modifier = Modifier.background(PsCameraColors.overlay, Ps.shape.pill).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AllDone(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(PsCameraColors.veil).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Every before has its after.",
            color = Ink,
            style = Ps.type.titleM,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(16.dp))
        PsButton("Back to the job", onClose, onDark = true)
    }
}

@Composable
private fun GhostControls(ui: CameraUi, vm: CameraViewModel) {
    if (ui.target == null) {
        Spacer(Modifier.height(56.dp)); return
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PsSegmented(
            options = GhostMode.entries.map { it.label },
            selected = GhostMode.entries.indexOf(ui.ghostMode),
            onSelect = { i -> vm.setGhost(ui.ghostStrength, GhostMode.entries[i]); vm.saveGhostAsDefault() },
            onDark = true,
            modifier = Modifier.weight(1.3f),
        )
        if (ui.ghostMode != GhostMode.OFF) {
            PsSlider(
                value = ui.ghostStrength,
                onValueChange = { vm.setGhost(it, ui.ghostMode) },
                onValueChangeFinished = { vm.saveGhostAsDefault() },
                valueRange = 0.1f..1f,
                contentDescription = "Ghost strength",
                onDark = true,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        } else {
            Text(
                "Shoot freely",
                color = Muted, style = Ps.type.caption,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun AreaChips(ui: CameraUi, vm: CameraViewModel) {
    PsChipScroller(Modifier.heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        item(key = "none") {
            PsChip("No label", selected = ui.selectedAreaId == null, onClick = { vm.selectArea(null) }, onDark = true)
        }
        items(ui.areas, key = { it.id }) { area ->
            PsChip(area.name, selected = area.id == ui.selectedAreaId, onClick = { vm.selectArea(area.id) }, onDark = true)
        }
        item(key = "add") {
            PsChip(
                "Label",
                selected = false,
                onClick = vm::openAreaPicker,
                leadingIcon = Icons.Outlined.Add,
                kind = PsChipKind.Action,
                onDark = true,
            )
        }
    }
}

@Composable
private fun ShutterRow(ui: CameraUi, enabled: Boolean, onShoot: () -> Unit, onUndo: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            ui.lastShot?.let { last ->
                // The thumbnail and the word are one 48 dp+ button.
                Column(
                    Modifier
                        .psPressable(onClick = onUndo, focusShape = Ps.shape.sm)
                        .clearAndSetSemantics { contentDescription = "Undo last photo"; role = Role.Button }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PhotoImage(
                        last.file, last.rotation,
                        Modifier.size(52.dp).clip(Ps.shape.sm).border(1.dp, PsCameraColors.hairline, Ps.shape.sm),
                    )
                    Text("Undo", color = Muted, style = Ps.type.chip, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Box(
            Modifier
                .size(76.dp)
                .psPressable(onClick = onShoot, enabled = enabled, focusShape = CircleShape)
                .semantics { contentDescription = "Take photo" }
                .border(4.dp, if (enabled) PsCameraColors.shutter else Muted, CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                .background(if (enabled) PsCameraColors.shutter else Muted),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            PsButton(
                if (ui.shotsThisSession > 0) "Done · ${ui.shotsThisSession}" else "Done",
                onDone,
                variant = PsButtonVariant.Secondary,
                onDark = true,
                height = 48.dp,
                compact = true,
            )
        }
    }
}

@Composable
private fun AreaPicker(ui: CameraUi, vm: CameraViewModel) {
    var custom by remember { mutableStateOf("") }
    val existing = ui.areas.map { it.name.lowercase() }.toSet()
    PsSheet("Add a label", onDismiss = vm::closeAreaPicker) {
        Text(
            "Optional. Photos you take next get this label, and the client sees it as a heading.",
            style = Ps.type.caption,
            color = Ps.colors.inkSoft,
        )
        Spacer(Modifier.height(12.dp))
        PsChipRow {
            ui.presets.filter { it.lowercase() !in existing }.forEach { name ->
                PsChip(name, selected = false, onClick = { vm.addArea(name) }, kind = PsChipKind.Action, leadingIcon = Icons.Outlined.Add)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            PsTextField(
                custom, { custom = it },
                modifier = Modifier.weight(1f),
                placeholder = "Or type one",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addArea(custom) }),
            )
            Spacer(Modifier.width(12.dp))
            PsButton("Add", { vm.addArea(custom) }, enabled = custom.isNotBlank())
        }
    }
}
