package com.greasepencil.pairshot.ui.components.ps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greasepencil.pairshot.ui.theme.Ps

/**
 * Every bottom sheet: surface, 20 dp top corners, a hairline drag handle, serif title, 24 dp
 * gutter. It rises with the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ps.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = Ps.shape.sheetTop,
        containerColor = c.surface,
        contentColor = c.ink,
        tonalElevation = 0.dp,
        scrimColor = c.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(Ps.shape.pill)
                    .background(c.hairline),
            )
        },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        SheetBody(title, Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), content)
    }
}

@Composable
private fun SheetBody(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = Ps.space.sheetGutter, end = Ps.space.sheetGutter, top = 8.dp, bottom = 24.dp),
    ) {
        Text(title, style = Ps.type.titleM, color = Ps.colors.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** The dialog panel shared by the confirm and reveal dialogs. */
@Composable
private fun DialogPanel(
    title: String,
    onDismiss: () -> Unit,
    dismissOnOutside: Boolean,
    buttons: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = dismissOnOutside, dismissOnBackPress = dismissOnOutside),
    ) {
        DialogCard(title, buttons, content)
    }
}

@Composable
private fun DialogCard(title: String, buttons: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Ps.colors
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(Ps.shape.lg)
            .background(c.surface)
            .semantics { paneTitle = title }
            .padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
    ) {
        Column(Modifier.padding(end = 12.dp)) {
            Text(title, style = Ps.type.titleM, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            content()
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            buttons()
        }
    }
}

/**
 * A question before something happens. Buttons are Quiet, right-aligned; a destructive confirm is in
 * pencilText.
 */
@Composable
fun PsConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismiss: String = "Cancel",
) {
    DialogPanel(title, onDismiss, dismissOnOutside = true, buttons = {
        PsButton(dismiss, onDismiss, variant = PsButtonVariant.Quiet)
        PsButton(confirm, onConfirm, variant = if (destructive) PsButtonVariant.Danger else PsButtonVariant.Quiet)
    }) {
        Text(text, style = Ps.type.body, color = Ps.colors.ink)
    }
}

/**
 * "Copy this now": a secret shown once (API keys, signing secrets). [what] says what it is.
 * It can't be dismissed by accident.
 */
@Composable
fun PsRevealDialog(
    what: String,
    secret: String,
    onCopyClose: () -> Unit,
    onSaved: () -> Unit,
    title: String = "Copy this now",
) {
    DialogPanel(title, onSaved, dismissOnOutside = false, buttons = {
        PsButton("I've saved it", onSaved, variant = PsButtonVariant.Quiet)
        PsButton("Copy & close", onCopyClose, variant = PsButtonVariant.Quiet, icon = Icons.Outlined.ContentCopy)
    }) {
        Text(what, style = Ps.type.body, color = Ps.colors.ink)
        Spacer(Modifier.height(12.dp))
        PsCodeWell(secret, onCopy = null)
        Spacer(Modifier.height(12.dp))
        Text("For safety it's shown only once. If you lose it, make a new one.", style = Ps.type.caption, color = Ps.colors.inkSoft)
    }
}

/** One style for every snackbar: an ink bar, radius md, paper text. */
@Composable
fun PsSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> PsSnackbar(data) }
}

@Composable
private fun PsSnackbar(data: SnackbarData) {
    val c = Ps.colors
    Row(
        Modifier
            .padding(horizontal = Ps.space.gutter, vertical = 12.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(Ps.shape.md)
            .background(c.ink)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, style = Ps.type.bodyS, color = c.onInk, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        data.visuals.actionLabel?.let { label ->
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .psPressable(onClick = { data.performAction() })
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Ps.type.button, color = c.onInk)
            }
        }
    }
}

/** A selectable mono box for addresses, keys and secrets, with a copy button when [onCopy] is set. */
@Composable
fun PsCodeWell(text: String, onCopy: (() -> Unit)?, modifier: Modifier = Modifier, copyLabel: String = "Copy") {
    val c = Ps.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Ps.shape.xs)
            .background(c.sunken)
            .padding(start = 12.dp, end = if (onCopy != null) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionContainer(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(text, style = Ps.type.mono, color = c.ink)
        }
        if (onCopy != null) PsIconButton(Icons.Outlined.ContentCopy, copyLabel, onCopy)
    }
}

/**
 * A frame round a template or collage thumbnail. Chosen: 2 dp ink border and a pencil check badge.
 * A radio button for TalkBack; give it a spoken [label].
 */
@Composable
fun PsSelectableFrame(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Ps.colors
    val shape = Ps.shape.md
    Box(
        modifier
            .psSelectable(selected, onClick, Role.RadioButton, focusShape = shape)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Box(
            Modifier
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) c.ink else c.hairline, shape)
                .padding(if (selected) 2.dp else 1.dp),
            content = content,
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(Ps.shape.pill)
                    .background(c.pencil),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = c.onPhotoTag, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@PsPreviews
@Composable
private fun PsCodeWellPreview() = PreviewPaper {
    PsCodeWell("jobs-7f3k2q@in.pairshot.app", onCopy = {}, Modifier.padding(horizontal = 20.dp))
}

@PsPreviews
@Composable
private fun PsSelectableFramePreview() = PreviewPaper {
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PsSelectableFrame(selected = true, onClick = {}, label = "Classic") {
            Box(Modifier.size(80.dp, 112.dp).background(Ps.colors.sunken))
        }
        PsSelectableFrame(selected = false, onClick = {}, label = "Report") {
            Box(Modifier.size(80.dp, 112.dp).background(Ps.colors.sunken))
        }
    }
}

@PsPreviews
@Composable
private fun PsSnackbarPreview() = PreviewPaper {
    PsSnackbar(PreviewSnackbar("Link copied", "Undo"))
}

private class PreviewSnackbar(override val message: String, override val actionLabel: String?) : SnackbarVisuals, SnackbarData {
    override val withDismissAction = false
    override val duration = SnackbarDuration.Short
    override val visuals: SnackbarVisuals get() = this
    override fun performAction() {}
    override fun dismiss() {}
}

@PsPreviews
@Composable
private fun PsSheetPreview() = PreviewPaper {
    Box(Modifier.clip(Ps.shape.sheetTop).background(Ps.colors.surface)) {
        SheetBody("Add a label", Modifier) {
            PsTextField("Driveway", {}, label = "Label")
            Spacer(Modifier.height(16.dp))
            PsButton("Add", {}, fullWidth = true)
        }
    }
}

@PsPreviews
@Composable
private fun PsConfirmDialogPreview() = PreviewPaper {
    Box(Modifier.padding(horizontal = 20.dp)) {
        DialogCard("Delete this job?", buttons = {
            PsButton("Cancel", {}, variant = PsButtonVariant.Quiet)
            PsButton("Delete", {}, variant = PsButtonVariant.Danger)
        }) { Text("Its photos are removed from this phone.", style = Ps.type.body, color = Ps.colors.ink) }
    }
}

@PsPreviews
@Composable
private fun PsRevealDialogPreview() = PreviewPaper {
    Box(Modifier.padding(horizontal = 20.dp)) {
        DialogCard("Copy this now", buttons = {
            PsButton("I've saved it", {}, variant = PsButtonVariant.Quiet)
            PsButton("Copy & close", {}, variant = PsButtonVariant.Quiet)
        }) { PsCodeWell("ps_live_8Hq2v0x", onCopy = null) }
    }
}
