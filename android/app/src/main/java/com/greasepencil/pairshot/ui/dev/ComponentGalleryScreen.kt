package com.greasepencil.pairshot.ui.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.domain.JobStatus
import com.greasepencil.pairshot.ui.cloud.GoogleSignInButton
import com.greasepencil.pairshot.ui.components.ps.PsAvatar
import com.greasepencil.pairshot.ui.components.ps.PsBanner
import com.greasepencil.pairshot.ui.components.ps.PsBannerTone
import com.greasepencil.pairshot.ui.components.ps.PsBottomBar
import com.greasepencil.pairshot.ui.components.ps.PsBusyBar
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipKind
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.components.ps.PsChipScroller
import com.greasepencil.pairshot.ui.components.ps.PsCodeWell
import com.greasepencil.pairshot.ui.components.ps.PsConfirmDialog
import com.greasepencil.pairshot.ui.components.ps.PsEmptyState
import com.greasepencil.pairshot.ui.components.ps.PsFab
import com.greasepencil.pairshot.ui.components.ps.PsIconButton
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsLoopMark
import com.greasepencil.pairshot.ui.components.ps.PsMeter
import com.greasepencil.pairshot.ui.components.ps.PsPairArt
import com.greasepencil.pairshot.ui.components.ps.PsPairTile
import com.greasepencil.pairshot.ui.components.ps.PsPencilStroke
import com.greasepencil.pairshot.ui.components.ps.PsPhotoThumb
import com.greasepencil.pairshot.ui.components.ps.PsPlanPill
import com.greasepencil.pairshot.ui.components.ps.PsRevealDialog
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSearchField
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsSegmented
import com.greasepencil.pairshot.ui.components.ps.PsSelectableFrame
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.PsSlider
import com.greasepencil.pairshot.ui.components.ps.PsSnackbarHost
import com.greasepencil.pairshot.ui.components.ps.PsStatusPill
import com.greasepencil.pairshot.ui.components.ps.PsSwatch
import com.greasepencil.pairshot.ui.components.ps.PsSwitchRow
import com.greasepencil.pairshot.ui.components.ps.SampleTabs
import com.greasepencil.pairshot.ui.components.ps.PsTabBar
import com.greasepencil.pairshot.ui.components.ps.PsTabHeader
import com.greasepencil.pairshot.ui.components.ps.PsTag
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.components.ps.PsValueRow
import com.greasepencil.pairshot.ui.theme.Contrast
import com.greasepencil.pairshot.ui.theme.PairshotTheme
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.theme.PsCameraColors
import com.greasepencil.pairshot.ui.theme.Sans
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Debug only: every shared component in every state on one scrolling page, with its own light/dark
 * switch so both themes can be reviewed without changing the phone's setting.
 */
@Composable
fun ComponentGalleryScreen(onBack: () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }
    PairshotTheme(dark = dark) {
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var tab by remember { mutableIntStateOf(0) }
        PsScaffold(
            topBar = { PsTopBar("Components", onBack = onBack, eyebrow = "Debug") },
            bottomBar = {
                PsTabBar(
                    SampleTabs,
                    current = tab,
                    onSelect = { tab = it },
                )
            },
            snackbarHost = { PsSnackbarHost(snackbar) },
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
                PsSegmented(
                    listOf("Light", "Dark"),
                    selected = if (dark) 1 else 0,
                    onSelect = { dark = it == 1 },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                ColoursSection()
                TypeSection()
                ButtonsSection()
                BarsSection()
                RowsSection()
                InputsSection()
                StatusSection()
                SurfacesSection(onSnack = { scope.launch { snackbar.showSnackbar("Link copied", actionLabel = "Undo") } })
                MediaSection()
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Text(
            title,
            style = Ps.type.heading,
            color = Ps.colors.ink,
            modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

private val gutter = Modifier.padding(horizontal = 20.dp)

@Composable
private fun ColoursSection() = Group("Colour") {
    val c = Ps.colors
    val swatches = listOf(
        "paper" to c.paper, "surface" to c.surface, "sunken" to c.sunken, "ink" to c.ink,
        "onInk" to c.onInk, "inkSoft" to c.inkSoft, "inkFaint" to c.inkFaint, "hairline" to c.hairline,
        "outline" to c.outline, "pencil" to c.pencil, "onPencil" to c.onPencil, "pencilText" to c.pencilText,
        "pencilWash" to c.pencilWash,
    )
    PsListBlock {
        swatches.forEach { (name, colour) ->
            row {
                PsListRow(
                    name,
                    leading = { Box(Modifier.size(28.dp).clip(Ps.shape.xs).background(colour).border(1.dp, c.hairline, Ps.shape.xs)) },
                    value = "%.1f:1 on paper".format(Locale.US, Contrast.ratio(colour, c.paper)),
                )
            }
        }
    }
}

@Composable
private fun TypeSection() = Group("Type") {
    val t = Ps.type
    val c = Ps.colors
    Column(gutter, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Display 40", style = t.display, color = c.ink)
        Text("Title XL 32", style = t.titleXL, color = c.ink)
        Text("Title L 26", style = t.titleL, color = c.ink)
        Text("Title M 22", style = t.titleM, color = c.ink)
        Text("Heading 18 / 600", style = t.heading, color = c.ink)
        Text("Row title 16 / 500", style = t.rowTitle, color = c.ink)
        Text("Row title small 14 / 500", style = t.rowTitleS, color = c.ink)
        Text("Body 16. Paragraphs and input text.", style = t.body, color = c.ink)
        Text("Body small 14. Row supporting text.", style = t.bodyS, color = c.inkSoft)
        Text("Caption 13. Helper text, footnotes.", style = t.caption, color = c.inkSoft)
        Text("Button 15 / 600", style = t.button, color = c.ink)
        Text("Chip 13 / 500", style = t.chip, color = c.ink)
        Text("EYEBROW 11 MONO", style = t.eyebrow, color = c.inkFaint)
        Text("12 Oak St · 2 of 5 · 0123456789", style = t.mono, color = c.ink)
        Spacer(Modifier.height(4.dp))
        listOf(FontWeight.Normal to "Geist 400", FontWeight.Medium to "Geist 500", FontWeight.SemiBold to "Geist 600").forEach { (w, label) ->
            Text("$label: The quick brown fox", style = t.body.copy(fontFamily = Sans, fontWeight = w), color = c.ink)
        }
    }
}

@Composable
private fun ButtonsSection() = Group("Buttons") {
    Column(gutter, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PsButton("Create job", {}, fullWidth = true)
        PsButton("Send link", {}, variant = PsButtonVariant.Accent, fullWidth = true)
        PsButton("Sending…", {}, variant = PsButtonVariant.Accent, busy = true, fullWidth = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PsButton("Before", {}, Modifier.weight(1f), variant = PsButtonVariant.Secondary, icon = Icons.Outlined.CameraAlt)
            PsButton("After", {}, Modifier.weight(1f), variant = PsButtonVariant.Secondary, icon = Icons.Outlined.CameraAlt, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PsButton("Not now", {}, variant = PsButtonVariant.Quiet)
            PsButton("Delete pair…", {}, variant = PsButtonVariant.Danger, icon = Icons.Outlined.Delete)
        }
        GoogleSignInButton(onClick = {}, busy = false)
        GoogleSignInButton(onClick = {}, busy = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PsIconButton(Icons.Outlined.Search, "Search jobs", {})
            PsIconButton(Icons.Outlined.Phone, "Call the client", {})
            PsIconButton(Icons.Outlined.MoreVert, "More", {}, enabled = false)
            Spacer(Modifier.weight(1f))
            PsFab("New job", Icons.Outlined.Add, {})
        }
    }
    CameraPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PsButton("Open settings", {}, Modifier.weight(1f), onDark = true)
            PsButton("Done", {}, Modifier.weight(1f), variant = PsButtonVariant.Secondary, onDark = true)
        }
        PsButton("Go back", {}, variant = PsButtonVariant.Quiet, onDark = true)
    }
}

/** The always-dark camera chrome, for onDark variants. */
@Composable
private fun CameraPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(PsCameraColors.background).padding(vertical = 12.dp, horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("CAMERA (ALWAYS DARK)", style = Ps.type.eyebrow, color = PsCameraColors.muted)
        content()
    }
}

@Composable
private fun BarsSection() = Group("Bars") {
    val c = Ps.colors
    Column(Modifier.fillMaxWidth().background(c.paper).border(1.dp, c.hairline)) {
        PsTopBar("Dana Whitfield", onBack = {}, actions = { PsIconButton(Icons.Outlined.MoreVert, "More", {}) })
        PsTopBar("Updates to your CRM", onBack = {}, eyebrow = "Connections")
    }
    PsTabHeader("Jobs", eyebrow = "Sparkle Wash", actions = { PsIconButton(Icons.Outlined.Search, "Search jobs", {}) })
    var replay by remember { mutableIntStateOf(0) }
    Row(gutter, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Pencil stroke", style = Ps.type.bodyS, color = c.inkSoft)
        PsPencilStroke()
        key(replay) { PsPencilStroke(animate = true) }
        PsButton("Replay", { replay++ }, variant = PsButtonVariant.Quiet)
    }
    Text("Tab bar: see the bottom of this screen.", style = Ps.type.caption, color = c.inkSoft, modifier = gutter)
    PsBottomBar {
        PsButton("Preview", {}, Modifier.weight(1f), variant = PsButtonVariant.Secondary)
        PsButton("Send link", {}, Modifier.weight(1f), variant = PsButtonVariant.Accent)
    }
}

@Composable
private fun RowsSection() = Group("Rows") {
    var wifi by remember { mutableStateOf(true) }
    var other by remember { mutableStateOf(false) }
    Column {
        PsSectionHeader("Work")
        PsListBlock {
            row { PsListRow("Camera", value = "Outline", onClick = {}) }
            row { PsListRow("Sign in", supporting = "Only needed to send links.", onClick = {}) }
            row { PsListRow("Connections", trailing = { PsPlanPill(pro = true) }, onClick = {}) }
            row { PsListRow("Disabled row", value = "Not set", onClick = {}, enabled = false) }
            row { PsSwitchRow("Wi-Fi only", checked = wifi, onChange = { wifi = it }, supporting = "Links wait for Wi-Fi.") }
            row { PsSwitchRow("Off switch", checked = other, onChange = { other = it }) }
            row { PsValueRow("Version", "0.1.0") }
        }
        Spacer(Modifier.height(16.dp))
        PsSectionHeader("Driveway", trailing = {
            Text("2/3", style = Ps.type.mono, color = Ps.colors.inkFaint)
            PsIconButton(Icons.Outlined.MoreVert, "Label options", {})
        })
    }
}

@Composable
private fun InputsSection() = Group("Inputs") {
    var name by remember { mutableStateOf("Dana Whitfield") }
    var phone by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("not a link") }
    var query by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(0) }
    var checked by remember { mutableStateOf(setOf(1)) }
    var mode by remember { mutableIntStateOf(0) }
    var camMode by remember { mutableIntStateOf(0) }
    var strength by remember { mutableFloatStateOf(0.5f) }
    var swatch by remember { mutableIntStateOf(1) }
    Column(gutter, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PsTextField(name, { name = it }, label = "Client name")
        PsTextField(phone, { phone = it }, label = "Client phone", labelTrailing = "optional", helper = "Lets you text the link straight to them.")
        PsTextField(url, { url = it }, label = "Webhook address", error = "That doesn't look like a web address.")
        PsTextField("Can't change this", {}, label = "Disabled", enabled = false)
        PsTextField(
            "Hi {first_name}, here's your {service} before & after: {link}",
            {},
            label = "Message",
            singleLine = false,
            minLines = 3,
        )
        PsSearchField(query, { query = it }, placeholder = "Search client, address, service")
    }
    PsChipRow(gutter) {
        listOf("Today", "Tomorrow", "No date").forEachIndexed { i, label ->
            PsChip(label, selected = day == i, onClick = { day = i })
        }
    }
    PsChipRow(gutter) {
        listOf("Driveway", "Patio", "Roof").forEachIndexed { i, label ->
            PsChip(label, selected = i in checked, kind = PsChipKind.Checkbox, onClick = { checked = if (i in checked) checked - i else checked + i })
        }
        PsChip("Label", selected = false, onClick = {}, leadingIcon = Icons.Outlined.Add)
    }
    PsSegmented(listOf("System", "Light", "Dark"), mode, { mode = it }, gutter)
    PsSlider(strength, { strength = it }, "Ghost strength", gutter)
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SampleSwatches.forEachIndexed { i, (colour, label) ->
            PsSwatch(colour, swatch == i, label, { swatch = i })
        }
    }
    CameraPanel {
        PsSegmented(listOf("Outline", "Photo", "Off"), camMode, { camMode = it }, onDark = true)
        PsSlider(strength, { strength = it }, "Ghost strength", onDark = true)
    }
    Box(Modifier.background(PsCameraColors.background)) {
        PsChipScroller {
            listOf("No label", "Driveway", "Patio", "Back fence", "Roof").forEachIndexed { i, label ->
                item { PsChip(label, selected = i == day, onClick = { day = i }, onDark = true) }
            }
        }
    }
}

// Sample colours for the swatch demo only (the real palette arrives with the Brand look, WP5).
private val SampleSwatches = listOf(
    Color(0xFFD4432A) to "Pencil red",
    Color(0xFF1F3A5F) to "Navy",
    Color(0xFF2F5D3A) to "Forest",
    Color(0xFFF4E9A6) to "Pale yellow",
    Color(0xFFFFFFFF) to "White",
)

@Composable
private fun StatusSection() = Group("Status") {
    var busy by remember { mutableStateOf(true) }
    PsChipRow(gutter) {
        JobStatus.entries.forEach { PsStatusPill(it, Modifier.padding(vertical = 4.dp)) }
        PsPlanPill(pro = false, Modifier.padding(vertical = 4.dp))
        PsPlanPill(pro = true, Modifier.padding(vertical = 4.dp))
    }
    Row(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(120.dp, 80.dp).clip(Ps.shape.sm).background(Ps.colors.sunken).padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PsTag("Before")
                PsTag("Cover")
            }
        }
    }
    PsBanner(
        "Phone storage is nearly full",
        "Only 412 MB left. New photos may not save.",
        tone = PsBannerTone.Warning,
        actionLabel = "Free up space",
        onAction = {},
    )
    PsBanner("This month's links are used", "Your next link can go out on 1 December.", actionLabel = "See Pro", onAction = {})
    Column(gutter, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PsMeter("Client links", "2 of 3", "2 of 3 used", 2 / 3f, full = false)
        PsMeter("Client links", "3 of 3", "3 of 3 used, none left", 1f, full = true)
    }
    PsBusyBar(busy)
    PsButton(if (busy) "Stop busy bar" else "Start busy bar", { busy = !busy }, gutter, variant = PsButtonVariant.Quiet)
}

@Composable
private fun SurfacesSection(onSnack: () -> Unit) = Group("Sheets, dialogs, wells") {
    var sheet by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var destructive by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    PsCodeWell("jobs-7f3k2q@in.pairshot.app", onCopy = {}, gutter)
    Column(gutter, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PsButton("Open a sheet", { sheet = true }, variant = PsButtonVariant.Secondary, fullWidth = true)
        PsButton("Confirm dialog", { confirm = true }, variant = PsButtonVariant.Secondary, fullWidth = true)
        PsButton("Destructive confirm", { destructive = true }, variant = PsButtonVariant.Secondary, fullWidth = true)
        PsButton("Reveal once dialog", { reveal = true }, variant = PsButtonVariant.Secondary, fullWidth = true)
        PsButton("Show a snackbar", onSnack, variant = PsButtonVariant.Secondary, fullWidth = true)
    }
    Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Classic", "Showroom", "Report").forEachIndexed { i, name ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PsSelectableFrame(selected = frame == i, onClick = { frame = i }, label = name) {
                    Box(Modifier.size(80.dp, 112.dp).background(Ps.colors.sunken))
                }
                Spacer(Modifier.height(6.dp))
                Text(name, style = Ps.type.chip, color = Ps.colors.ink)
            }
        }
    }
    if (sheet) {
        var label by remember { mutableStateOf("") }
        PsSheet("Add a label", onDismiss = { sheet = false }) {
            PsTextField(label, { label = it }, label = "Label", placeholder = "Driveway")
            Spacer(Modifier.height(16.dp))
            PsChipRow {
                listOf("Driveway", "Patio", "Roof").forEach { PsChip(it, selected = label == it, onClick = { label = it }) }
            }
            Spacer(Modifier.height(16.dp))
            PsButton("Add", { sheet = false }, fullWidth = true)
        }
    }
    if (confirm) {
        PsConfirmDialog(
            title = "Close this link?",
            text = "Your client won't be able to open it any more.",
            confirm = "Close link",
            onConfirm = { confirm = false },
            onDismiss = { confirm = false },
        )
    }
    if (destructive) {
        PsConfirmDialog(
            title = "Delete this job?",
            text = "Its photos are removed from this phone.",
            confirm = "Delete",
            destructive = true,
            onConfirm = { destructive = false },
            onDismiss = { destructive = false },
        )
    }
    if (reveal) {
        PsRevealDialog(
            what = "API key (shown once; paste it into Zapier or Make)",
            secret = "ps_live_8Hq2v0xR4mTz",
            onCopyClose = { reveal = false },
            onSaved = { reveal = false },
        )
    }
}

@Composable
private fun MediaSection() = Group("Photos and empty states") {
    Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PsPhotoThumb(null, 0, 56.dp)
        PsAvatar("D")
        PsLoopMark { PsStatusPill(JobStatus.SEEN) }
    }
    Row(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PsPairTile("Driveway", null, 0, "none", 0, cover = true, hidden = false, onClick = {}, modifier = Modifier.weight(1f))
        PsPairTile("Driveway", null, 0, null, 0, cover = false, hidden = true, onClick = {}, modifier = Modifier.weight(1f))
    }
    Box(gutter) { PsPairArt() }
    PsEmptyState(
        "No jobs yet",
        "Add a job, shoot the befores, then the afters. They pair up on their own.",
        actionLabel = "New job",
        onAction = {},
    )
}
