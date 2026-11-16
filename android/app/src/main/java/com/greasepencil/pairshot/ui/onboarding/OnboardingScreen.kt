package com.greasepencil.pairshot.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.greasepencil.pairshot.data.prefs.SettingsStore
import com.greasepencil.pairshot.domain.Trade
import com.greasepencil.pairshot.domain.gallery.BrandLook
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import com.greasepencil.pairshot.ui.brand.colourOf
import com.greasepencil.pairshot.ui.components.ps.PsBottomBar
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.components.ps.PsLoopMark
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSwatch
import com.greasepencil.pairshot.ui.components.ps.PsSwatchRow
import com.greasepencil.pairshot.ui.components.ps.PsTemplateThumb
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.components.ps.rememberReducedMotion
import com.greasepencil.pairshot.ui.theme.Ps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val _draft = MutableStateFlow(restore())
    val draft: StateFlow<OnboardingDraft> = _draft

    private fun edit(f: (OnboardingDraft) -> OnboardingDraft) {
        _draft.update(f)
        // Kept in the saved state, so a killed app comes back to the same step and choices.
        val d = _draft.value
        handle["step"] = d.step
        handle["name"] = d.name
        handle["trade"] = d.trade.name
        handle["accent"] = d.accent
        handle["template"] = d.template.id
        handle["colourTouched"] = d.colourTouched
        handle["styleTouched"] = d.styleTouched
    }

    private fun restore(): OnboardingDraft {
        val trade = handle.get<String>("trade")?.let { t -> Trade.entries.firstOrNull { it.name == t } } ?: return OnboardingDraft()
        val base = OnboardingDraft(trade = trade)
        return base.copy(
            step = handle.get<Int>("step") ?: 1,
            name = handle.get<String>("name").orEmpty(),
            accent = BrandLook.normalise(handle.get<String>("accent")) ?: base.accent,
            template = ClientTemplate.fromId(handle.get<String>("template")) ?: base.template,
            colourTouched = handle.get<Boolean>("colourTouched") ?: false,
            styleTouched = handle.get<Boolean>("styleTouched") ?: false,
        )
    }

    fun setName(name: String) = edit { it.withName(name) }
    fun setTrade(trade: Trade) = edit { it.withTrade(trade) }
    fun setAccent(hex: String) = edit { it.withAccent(hex) }
    fun setTemplate(t: ClientTemplate) = edit { it.withTemplate(t) }
    fun next() = edit { it.next() }
    fun back() = edit { it.back() }

    /** Start saves the look as shown; Skip saves the trade's own. Then lands on Jobs. */
    fun finish(skip: Boolean, done: () -> Unit) {
        val look = if (skip) _draft.value.skipped() else _draft.value.started()
        viewModelScope.launch {
            settings.finishOnboarding(look.name, look.trade, look.accent, look.template)
            done()
        }
    }
}

/** The two-step first run: who you are, then what your client sees. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val d by vm.draft.collectAsStateWithLifecycle()
    var saving by rememberSaveable { mutableStateOf(false) }
    fun finish(skip: Boolean) {
        if (saving) return
        saving = true
        vm.finish(skip, onDone)
    }
    BackHandler(enabled = d.step == 2) { vm.back() }

    PsScaffold(
        bottomBar = {
            PsBottomBar {
                Column(Modifier.weight(1f)) {
                    if (d.step == 1) {
                        PsButton("Next", vm::next, fullWidth = true)
                    } else {
                        PsButton("Start", { finish(skip = false) }, fullWidth = true, busy = saving)
                        Text(
                            "No account needed. Everything stays on this phone until you send a link.",
                            Modifier.padding(top = Ps.space.xs),
                            style = Ps.type.caption,
                            color = Ps.colors.inkSoft,
                        )
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Ps.space.row).padding(start = Ps.space.gutter, end = Ps.space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepDots(d.step, Modifier.weight(1f))
                if (d.step == 2) PsButton("Skip", { finish(skip = true) }, variant = PsButtonVariant.Quiet, enabled = !saving)
            }
            val stepMs = if (rememberReducedMotion()) 0 else Ps.motion.standard
            AnimatedContent(
                d.step,
                Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(stepMs)) togetherWith fadeOut(tween(stepMs)) },
                label = "onboardingStep",
            ) { step ->
                if (step == 1) StepOne(d, vm) else StepTwo(d, vm)
            }
        }
    }
}

/** ● ○ or ● ●, read as "Step 1 of 2". */
@Composable
private fun StepDots(step: Int, modifier: Modifier = Modifier) {
    val c = Ps.colors
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Step $step of 2" },
        horizontalArrangement = Arrangement.spacedBy(Ps.space.xs),
    ) {
        for (i in 1..2) {
            val on = i <= step
            Box(
                Modifier
                    .size(8.dp)
                    .clip(Ps.shape.pill)
                    .background(if (on) c.ink else c.paper)
                    .border(1.dp, if (on) c.ink else c.outline, Ps.shape.pill),
            )
        }
    }
}

/** Moves focus (and TalkBack) to a step's heading when the step opens. */
@Composable
private fun rememberHeadingFocus(step: Int): FocusRequester {
    val focus = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { focus.requestFocus() } }
    return focus
}

/** A step heading that can take focus. */
private fun Modifier.stepHeading(focus: FocusRequester): Modifier = focusRequester(focus).focusable().semantics { heading() }

@Composable
private fun StepOne(d: OnboardingDraft, vm: OnboardingViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Ps.space.gutter)
            .padding(bottom = Ps.space.xl),
    ) {
        Spacer(Modifier.height(Ps.space.md))
        PsLoopMark(Modifier.stepHeading(rememberHeadingFocus(1))) {
            Text("Pairshot", style = Ps.type.display, color = Ps.colors.ink)
        }
        Spacer(Modifier.height(Ps.space.lg))
        Text(
            "Before and after photos, sorted by job, sent to your client as one clean link.",
            style = Ps.type.body,
            color = Ps.colors.ink,
        )
        Spacer(Modifier.height(Ps.space.xxl))
        PsTextField(
            d.name,
            vm::setName,
            label = "Business name",
            placeholder = "Sparkle Wash",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(Ps.space.xl))
        Text(
            "What do you do?".uppercase(),
            Modifier.padding(bottom = Ps.space.headerGap).semantics { heading(); contentDescription = "What do you do?" },
            style = Ps.type.eyebrow,
            color = Ps.colors.inkFaint,
        )
        PsChipRow {
            Trade.entries.forEach { t -> PsChip(t.label, selected = t == d.trade, onClick = { vm.setTrade(t) }) }
        }
        Spacer(Modifier.height(Ps.space.xs))
        Text(
            "This only sets the label names we suggest.",
            style = Ps.type.caption,
            color = Ps.colors.inkSoft,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepTwo(d: OnboardingDraft, vm: OnboardingViewModel) {
    val reduced = rememberReducedMotion()
    // On short phones a smaller hero keeps both pickers above the fold.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val heroWidth = if (windowHeight > 0.dp && windowHeight < 700.dp) 160.dp else 200.dp
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Ps.space.gutter)
            .padding(bottom = Ps.space.xl),
    ) {
        Text(
            "This is what your client gets.",
            Modifier.stepHeading(rememberHeadingFocus(2)),
            style = Ps.type.titleL,
            color = Ps.colors.ink,
        )
        Spacer(Modifier.height(Ps.space.md))
        val look = Triple(d.template, d.accent, d.name)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Crossfade(look, animationSpec = tween(if (reduced) 0 else Ps.motion.standard), label = "onboardingHero") { (template, accent, name) ->
                PsTemplateThumb(
                    template, accent, name,
                    modifier = Modifier.clip(Ps.shape.lg).border(1.dp, Ps.colors.hairline, Ps.shape.lg),
                    width = heroWidth,
                    description = "Your client page: ${template.label}, ${BrandLook.named(accent)?.name ?: "your colour"}",
                )
            }
        }
        Spacer(Modifier.height(Ps.space.lg))
        PsSwatchRow(Modifier.fillMaxWidth()) {
            BrandLook.colours.forEach { col ->
                PsSwatch(colourOf(col.hex), selected = d.accent == col.hex, label = col.name, onClick = { vm.setAccent(col.hex) })
            }
        }
        Spacer(Modifier.height(Ps.space.sm))
        PsChipRow {
            ClientTemplate.entries.forEach { t -> PsChip(t.label, selected = t == d.template, onClick = { vm.setTemplate(t) }) }
        }
        Spacer(Modifier.height(Ps.space.xs))
        Text(d.template.blurb, style = Ps.type.bodyS, color = Ps.colors.inkSoft)
        Spacer(Modifier.height(Ps.space.xxs))
        Text("You can change this any time in Brand.", style = Ps.type.caption, color = Ps.colors.inkSoft)
    }
}
