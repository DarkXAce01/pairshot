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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.greasepencil.pairshot.domain.Rotation
import com.greasepencil.pairshot.ui.components.PhotoImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val Dim = Color(0xFF0E0E0D)
private val Ink = Color(0xFFEDE9E2)
private val Muted = Color(0xFF9B968D)

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
                Text("Pairshot needs the camera to take your before and after photos.", color = Ink)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }) { Text("Open settings") }
                TextButton(onClick = onClose) { Text("Go back", color = Muted) }
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
            !s.after && areaId == null -> vm.openAreaPicker()
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
                    Ghost(target.beforeFile, ui.ghostOpacity, ui.ghostEdges, peeking)
                }
                GestureLayer(camera, previewView)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = flash.value)))
                ZoomBadge(camera, Modifier.align(Alignment.BottomCenter).padding(8.dp))
                if (ui.after && target != null) {
                    PeekButton(Modifier.align(Alignment.BottomEnd).padding(8.dp)) { peeking = it }
                }
                (cameraError ?: ui.error)?.let {
                    Text(
                        it, color = Color.White,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(10.dp),
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
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close camera", tint = Ink) }
        Text(if (ui.after) "AFTER" else "BEFORE", color = Ink, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        val t = ui.target
        val info = if (ui.after) {
            if (t != null) "${t.areaName} · ${ui.pendingCount} left" else ""
        } else {
            ui.areas.firstOrNull { it.id == ui.selectedAreaId }?.name ?: "Pick an area"
        }
        Text(info, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
    }
}

@Composable
private fun Ghost(beforeFile: String, opacity: Float, edges: Boolean, peeking: Boolean) {
    val context = LocalContext.current
    if (edges && !peeking) {
        val outline by produceState<ImageBitmap?>(null, beforeFile) { value = GhostLoader.outline(context, beforeFile) }
        outline?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    } else {
        // rotation 0: the before in the camera's own frame, exactly as its preview looked.
        PhotoImage(beforeFile, rotation = 0, modifier = Modifier.fillMaxSize(), alpha = if (peeking) 1f else opacity)
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
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun PeekButton(modifier: Modifier, onPeek: (Boolean) -> Unit) {
    Text(
        "HOLD TO PEEK",
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPeek(true)
                    tryAwaitRelease()
                    onPeek(false)
                })
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun AllDone(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Dim.copy(alpha = 0.85f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Every before has its after.", color = Ink, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose) { Text("Back to the job") }
    }
}

@Composable
private fun GhostControls(ui: CameraUi, vm: CameraViewModel) {
    if (ui.target == null) {
        Spacer(Modifier.height(56.dp)); return
    }
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("GHOST", color = Muted, style = MaterialTheme.typography.labelSmall)
        Slider(
            value = ui.ghostOpacity,
            onValueChange = { vm.setGhost(it, ui.ghostEdges) },
            onValueChangeFinished = { vm.saveGhostAsDefault() },
            valueRange = 0.1f..0.9f,
            enabled = !ui.ghostEdges,
            colors = SliderDefaults.colors(inactiveTrackColor = Color(0xFF3A3835)),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = "Ghost strength" },
        )
        FilterChip(
            selected = ui.ghostEdges,
            onClick = { vm.setGhost(ui.ghostOpacity, !ui.ghostEdges); vm.saveGhostAsDefault() },
            label = { Text("Outline") },
            colors = FilterChipDefaults.filterChipColors(labelColor = Ink),
        )
    }
}

@Composable
private fun AreaChips(ui: CameraUi, vm: CameraViewModel) {
    LazyRow(
        Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(ui.areas, key = { it.id }) { area ->
            FilterChip(
                selected = area.id == ui.selectedAreaId,
                onClick = { vm.selectArea(area.id) },
                label = { Text(area.name) },
                colors = FilterChipDefaults.filterChipColors(labelColor = Ink),
            )
        }
        item {
            AssistChip(
                onClick = vm::openAreaPicker,
                label = { Text("Area") },
                leadingIcon = { Icon(Icons.Filled.Add, null, tint = Ink) },
                colors = AssistChipDefaults.assistChipColors(labelColor = Ink),
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PhotoImage(
                        last.file, last.rotation,
                        Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Muted, RoundedCornerShape(8.dp)),
                    )
                    Text(
                        "UNDO", color = Muted, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable(onClick = onUndo).padding(6.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(76.dp)
                .border(4.dp, if (enabled) Color.White else Muted, CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Muted)
                .clickable(enabled = enabled, onClick = onShoot)
                .semantics { contentDescription = "Take photo" },
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            OutlinedButton(onClick = onDone) {
                Text(if (ui.shotsThisSession > 0) "Done · ${ui.shotsThisSession}" else "Done", color = Ink)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AreaPicker(ui: CameraUi, vm: CameraViewModel) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var custom by remember { mutableStateOf("") }
    val existing = ui.areas.map { it.name.lowercase() }.toSet()
    ModalBottomSheet(onDismissRequest = vm::closeAreaPicker, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Which area?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.presets.filter { it.lowercase() !in existing }.forEach { name ->
                    AssistChip(onClick = { vm.addArea(name) }, label = { Text(name) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    custom, { custom = it },
                    placeholder = { Text("Or type one") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addArea(custom) }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.addArea(custom) }, enabled = custom.isNotBlank()) { Text("Add") }
            }
        }
    }
}
