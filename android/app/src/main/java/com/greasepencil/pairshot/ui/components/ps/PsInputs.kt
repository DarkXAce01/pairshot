package com.greasepencil.pairshot.ui.components.ps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.PsCameraColors

// ---------- Text fields ----------

/** The label line above a field: label on the left, an optional note (e.g. "optional") on the right. */
@Composable
private fun FieldLabel(label: String, labelTrailing: String?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, style = Ps.type.caption, color = Ps.colors.inkSoft, modifier = Modifier.weight(1f))
        if (labelTrailing != null) Text(labelTrailing, style = Ps.type.caption, color = Ps.colors.inkFaint)
    }
}

/** The sunken box round the text: 1 dp outline, 2 dp ink when focused, pencilText on error. */
@Composable
private fun FieldBox(
    focused: Boolean,
    isError: Boolean,
    singleLine: Boolean,
    showPlaceholder: Boolean,
    placeholder: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    inner: @Composable () -> Unit,
) {
    val c = Ps.colors
    val shape = Ps.shape.md
    val borderColor = when {
        isError -> c.pencilText
        focused -> c.ink
        else -> c.outline
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(c.sunken)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .padding(start = if (leading != null) 12.dp else 16.dp, end = if (trailing != null) 4.dp else 16.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (leading != null) {
            Box(Modifier.padding(top = if (singleLine) 0.dp else 14.dp)) { leading() }
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (showPlaceholder && placeholder != null) {
                Text(placeholder, style = Ps.type.body, color = c.inkFaint)
            }
            inner()
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun FieldHelper(helper: String?, error: String?) {
    val text = error ?: helper ?: return
    Text(
        text,
        style = Ps.type.caption,
        color = if (error != null) Ps.colors.pencilText else Ps.colors.inkSoft,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * A text field: label above, sunken box, helper or error below. [labelTrailing] is the small
 * right-aligned note on the label line ("optional"). Saves as typed; no cursor jumps.
 */
@Composable
fun PsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelTrailing: String? = null,
    helper: String? = null,
    error: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)) {
        if (label != null) FieldLabel(label, labelTrailing)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.fillMaxWidth().fieldSemantics(label, placeholder, error),
            enabled = enabled,
            textStyle = Ps.type.body.copy(color = Ps.colors.ink),
            cursorBrush = SolidColor(Ps.colors.ink),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            decorationBox = { inner ->
                FieldBox(focused, error != null, singleLine, value.isEmpty(), placeholder, leading, trailing, inner)
            },
        )
        FieldHelper(helper, error)
    }
}

/** [PsTextField] over a [TextFieldValue], for editors that insert at the cursor. */
@Composable
fun PsTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelTrailing: String? = null,
    helper: String? = null,
    error: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fieldModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)) {
        if (label != null) FieldLabel(label, labelTrailing)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.fillMaxWidth().fieldSemantics(label, placeholder, error),
            enabled = enabled,
            textStyle = Ps.type.body.copy(color = Ps.colors.ink),
            cursorBrush = SolidColor(Ps.colors.ink),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            decorationBox = { inner ->
                FieldBox(focused, error != null, singleLine, value.text.isEmpty(), placeholder, null, null, inner)
            },
        )
        FieldHelper(helper, error)
    }
}

private fun Modifier.fieldSemantics(label: String?, placeholder: String?, error: String?) = semantics {
    (label ?: placeholder)?.let { contentDescription = it }
    if (error != null) error(error)
}

/** Search: a field without a label, a search icon in front and a clear button once there's text. */
@Composable
fun PsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    fieldModifier: Modifier = Modifier,
) {
    PsTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        fieldModifier = fieldModifier,
        leading = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Ps.colors.inkSoft, modifier = Modifier.size(20.dp)) },
        trailing = if (query.isNotEmpty()) {
            { PsIconButton(Icons.Outlined.Close, "Clear search", { onQueryChange("") }) }
        } else {
            null
        },
    )
}

// ---------- Chips ----------

/** How a chip behaves for TalkBack: one of a set, or an on/off of its own. */
enum class PsChipKind { Radio, Checkbox }

/**
 * A pill chip, 36 dp tall inside a 48 dp target. Selected: ink fill. Unselected: outline. [onDark]
 * is for the camera.
 */
@Composable
fun PsChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    kind: PsChipKind = PsChipKind.Radio,
    onDark: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Ps.colors
    val fill = when {
        !selected -> Color.Transparent
        onDark -> PsCameraColors.ink
        else -> c.ink
    }
    val textColor = when {
        selected && onDark -> PsCameraColors.background
        selected -> c.onInk
        onDark -> PsCameraColors.ink
        else -> c.ink
    }
    val border = if (onDark) PsCameraColors.muted else c.outline
    val animatedFill by animateColorAsState(fill, tween(Ps.motion.fast), label = "chipFill")
    val shape = Ps.shape.pill
    val interactive = when (kind) {
        PsChipKind.Radio -> Modifier.psSelectable(selected, onClick, Role.RadioButton, enabled, shape)
        PsChipKind.Checkbox -> Modifier.psToggleable(selected, { onClick() }, Role.Checkbox, enabled, shape)
    }
    Box(
        modifier.heightIn(min = 48.dp).then(interactive).alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 36.dp)
                .clip(shape)
                .background(animatedFill)
                .then(if (selected) Modifier else Modifier.border(1.dp, border, shape))
                .padding(start = if (leadingIcon != null) 10.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            Text(text, style = Ps.type.chip, color = textColor)
        }
    }
}

/** Chips that wrap onto more lines. A radio group for TalkBack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PsChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

/** Chips in one sideways-scrolling line (the camera's labels). */
@Composable
fun PsChipScroller(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        modifier.fillMaxWidth().selectableGroup(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ---------- Segmented control ----------

/** Two or three options in a pill track; the chosen one in ink. 44 dp inside a 48 dp target. */
@Composable
fun PsSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    val c = Ps.colors
    val track = if (onDark) PsCameraColors.hairline else c.sunken
    val on = if (onDark) PsCameraColors.ink else c.ink
    val onOn = if (onDark) PsCameraColors.background else c.onInk
    val off = if (onDark) PsCameraColors.ink else c.ink
    val shape = Ps.shape.pill
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(track)
                .then(if (onDark) Modifier.border(1.dp, PsCameraColors.muted, shape) else Modifier.border(1.dp, c.outline, shape))
                .padding(3.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { i, label ->
                val isOn = i == selected
                val fill by animateColorAsState(if (isOn) on else Color.Transparent, tween(Ps.motion.fast), label = "segment")
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .psSelectable(isOn, { onSelect(i) }, Role.RadioButton, focusShape = shape)
                        .clip(shape)
                        .background(fill)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Ps.type.chip, color = if (isOn) onOn else off, maxLines = 1)
                }
            }
        }
    }
}

// ---------- Slider ----------

/**
 * A slider: 4 dp sunken track, ink fill and a 24 dp ink thumb. [contentDescription] is required:
 * it's what TalkBack says before the value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onDark: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val active = if (onDark) PsCameraColors.ink else Ps.colors.ink
    val inactive = if (onDark) PsCameraColors.hairline else Ps.colors.sunken
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp).semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        thumb = { Box(Modifier.size(24.dp).clip(CircleShape).background(active)) },
        track = { state: SliderState ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span == 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(inactive, cornerRadius = r)
                drawRoundRect(active, size = Size(size.width * fraction, size.height), cornerRadius = r)
            }
        },
    )
}

// ---------- Swatch ----------

/**
 * A colour to pick: a 40 dp circle in a 48 dp target; chosen shows a 2 dp ink ring 3 dp out.
 * [label] is the colour's spoken name.
 */
@Composable
fun PsSwatch(
    colour: Color,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    val c = Ps.colors
    Box(
        modifier
            .size(48.dp)
            .psSelectable(selected, onClick, Role.RadioButton, focusShape = Ps.shape.pill)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(48.dp)) {
            val r = 20.dp.toPx()
            drawCircle(colour, radius = r)
            // A hairline edge so pale colours still show on paper.
            drawCircle(c.hairline, radius = r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            if (selected) {
                val ring = 2.dp.toPx()
                drawCircle(c.ink, radius = r + 3.dp.toPx() + ring / 2, center = Offset(size.width / 2, size.height / 2), style = Stroke(ring))
            }
        }
        content?.invoke()
    }
}

@PsPreviews
@Composable
private fun PsTextFieldPreview() = PreviewPaper {
    val m = Modifier.padding(horizontal = 20.dp)
    PsTextField("Dana Whitfield", {}, m, label = "Client name")
    PsTextField("", {}, m, label = "Client phone", labelTrailing = "optional", helper = "Lets you text the link straight to them.")
    PsTextField("not a link", {}, m, label = "Webhook address", error = "That doesn't look like a web address.")
    PsTextField("Hi {first_name}, here's your {service} before & after: {link}", {}, m, label = "Message", singleLine = false, minLines = 3)
}

@PsPreviews
@Composable
private fun PsSearchFieldPreview() = PreviewPaper {
    PsSearchField("", {}, Modifier.padding(horizontal = 20.dp), placeholder = "Search client, address, service")
    PsSearchField("Oak", {}, Modifier.padding(horizontal = 20.dp))
}

@PsPreviews
@Composable
private fun PsChipPreview() = PreviewPaper {
    PsChipRow(Modifier.padding(horizontal = 20.dp)) {
        PsChip("Today", selected = true, onClick = {})
        PsChip("Tomorrow", selected = false, onClick = {})
        PsChip("Label", selected = false, onClick = {}, leadingIcon = Icons.Outlined.Add)
    }
    Box(Modifier.background(PsCameraColors.background)) {
        PsChipScroller {
            item { PsChip("No label", selected = true, onClick = {}, onDark = true) }
            item { PsChip("Driveway", selected = false, onClick = {}, onDark = true) }
        }
    }
}

@PsPreviews
@Composable
private fun PsSegmentedPreview() = PreviewPaper {
    var i by remember { mutableIntStateOf(0) }
    PsSegmented(listOf("System", "Light", "Dark"), i, { i = it }, Modifier.padding(horizontal = 20.dp))
}

@PsPreviews
@Composable
private fun PsSliderPreview() = PreviewPaper {
    var v by remember { mutableFloatStateOf(0.5f) }
    PsSlider(v, { v = it }, "Ghost strength", Modifier.padding(horizontal = 20.dp))
}

@PsPreviews
@Composable
private fun PsSwatchPreview() = PreviewPaper {
    var chosen by remember { mutableIntStateOf(0) }
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Color(0xFFD4432A) to "Red", Color(0xFF1F3A5F) to "Navy", Color(0xFFF4E9A6) to "Pale yellow").forEachIndexed { i, (col, name) ->
            PsSwatch(col, chosen == i, name, { chosen = i })
        }
    }
}
