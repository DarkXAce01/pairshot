package com.greasepencil.pairshot.ui.brand

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsButtonVariant
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipKind
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.theme.Ps

/** The message's fill-in words, and putting one in where the cursor is. */
object MessageTokens {
    const val LINK = "{link}"
    val all = listOf("{first_name}", "{service}", LINK)

    /** Text after an insert, and where the cursor goes (just after the token). */
    data class Result(val text: String, val cursor: Int)

    /**
     * Puts [token] in place of the selection [start]..[end] (a plain cursor when they're equal). A
     * space is added before it when it would otherwise run into a word, and after it when a word
     * follows straight on.
     */
    fun insert(text: String, start: Int, end: Int, token: String): Result {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        val before = text.substring(0, from)
        val after = text.substring(to)
        val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val trail = if (after.isNotEmpty() && after.first().isLetterOrDigit()) " " else ""
        val inserted = lead + token + trail
        return Result(before + inserted + after, before.length + lead.length + token.length)
    }

    fun hasLink(text: String): Boolean = LINK in text
}

/** The message editor: the template, insert chips, a filled-in preview, and a way back to the standard one. */
@Composable
fun MessageScreen(onBack: () -> Unit, vm: BrandViewModel = hiltViewModel()) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    PsScaffold(topBar = { PsTopBar("Message to the client", onBack = onBack) }) {
        val s = loaded ?: return@PsScaffold
        MessageEditor(s, vm::setMessage)
    }
}

@Composable
private fun MessageEditor(s: AppSettings, onSave: (String) -> Unit) {
    // The field owns its text and cursor and saves as you type, so nothing jumps.
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(s.messageTemplate)) }
    LaunchedEffect(value.text) { if (value.text != s.messageTemplate) onSave(value.text) }
    val noLink = !MessageTokens.hasLink(value.text)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp)) {
        PsTextField(
            value,
            { value = it },
            Modifier.padding(horizontal = Ps.space.gutter),
            label = "Message",
            helper = "The link goes where {link} is. Leave it in.",
            error = if (noLink) "Without {link} your client gets no link." else null,
            singleLine = false,
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        PsChipRow(Modifier.padding(start = Ps.space.gutter, end = Ps.space.gutter, top = 8.dp)) {
            MessageTokens.all.forEach { token ->
                PsChip(
                    token,
                    selected = false,
                    kind = PsChipKind.Action,
                    onClick = {
                        val r = MessageTokens.insert(value.text, value.selection.start, value.selection.end, token)
                        value = TextFieldValue(r.text, TextRange(r.cursor))
                    },
                )
            }
        }

        PsSectionHeader("Preview", Modifier.padding(top = Ps.space.xl))
        PsListBlock {
            row {
                Text(
                    MessageSample.fill(value.text, s.trade),
                    Modifier.padding(horizontal = Ps.space.gutter, vertical = Ps.space.md),
                    style = Ps.type.body,
                    color = Ps.colors.ink,
                )
            }
        }
        PsButton(
            "Reset to the standard message",
            { value = TextFieldValue(AppSettings.DEFAULT_TEMPLATE, TextRange(AppSettings.DEFAULT_TEMPLATE.length)) },
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            variant = PsButtonVariant.Quiet,
        )
    }
}
