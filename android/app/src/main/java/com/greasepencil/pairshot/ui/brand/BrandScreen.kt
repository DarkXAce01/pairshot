package com.greasepencil.pairshot.ui.brand

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greasepencil.pairshot.data.prefs.AppSettings
import com.greasepencil.pairshot.domain.Trade
import com.greasepencil.pairshot.ui.components.ps.PsButton
import com.greasepencil.pairshot.ui.components.ps.PsChip
import com.greasepencil.pairshot.ui.components.ps.PsChipRow
import com.greasepencil.pairshot.ui.components.ps.PsListBlock
import com.greasepencil.pairshot.ui.components.ps.PsListRow
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSectionHeader
import com.greasepencil.pairshot.ui.components.ps.PsSheet
import com.greasepencil.pairshot.ui.components.ps.PsTabHeader
import com.greasepencil.pairshot.ui.components.ps.PsTextField
import com.greasepencil.pairshot.ui.theme.Ps

/** The Brand tab's fields that open an edit sheet. */
private enum class BrandField { NAME, TRADE, PHONE, REVIEW, BOOKING }

private fun setOrNot(v: String) = if (v.isBlank()) "Not set" else "Set"

/** Brand: what clients see. Business identity, the buttons on their page, and the message. */
@Composable
fun BrandScreen(onMessage: () -> Unit, vm: BrandViewModel = hiltViewModel()) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<BrandField?>(null) }
    PsScaffold {
        val s = loaded ?: return@PsScaffold
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            PsTabHeader("Brand", eyebrow = "What your client sees")

            PsSectionHeader("Business", Modifier.padding(top = 12.dp))
            PsListBlock {
                row { PsListRow("Name", value = s.businessName.ifBlank { "Not set" }, onClick = { editing = BrandField.NAME }) }
                row { PsListRow("Trade", value = s.trade.label, onClick = { editing = BrandField.TRADE }) }
            }

            PsSectionHeader("On the page", Modifier.padding(top = Ps.space.sectionGap))
            PsListBlock {
                row { PsListRow("Call button", value = setOrNot(s.businessPhone), onClick = { editing = BrandField.PHONE }) }
                row { PsListRow("Leave a review", value = setOrNot(s.reviewUrl), onClick = { editing = BrandField.REVIEW }) }
                row { PsListRow("Book again", value = setOrNot(s.bookingUrl), onClick = { editing = BrandField.BOOKING }) }
            }
            Text(
                "Each button shows only if you fill it in.",
                Modifier.padding(start = Ps.space.gutter, end = Ps.space.gutter, top = 8.dp),
                style = Ps.type.caption,
                color = Ps.colors.inkSoft,
            )

            PsSectionHeader("Message", Modifier.padding(top = Ps.space.sectionGap))
            PsListBlock {
                row {
                    PsListRow(
                        "Message to the client",
                        supporting = MessageSample.fill(s.messageTemplate, s.trade),
                        onClick = onMessage,
                    )
                }
            }
        }
        editing?.let { field -> EditSheet(field, s, vm, onDone = { editing = null }) }
    }
}

@Composable
private fun EditSheet(field: BrandField, s: AppSettings, vm: BrandViewModel, onDone: () -> Unit) {
    when (field) {
        BrandField.NAME -> TextSheet(
            title = "Business name", label = "Business name", initial = s.businessName, onChange = vm::setName, onDone = onDone,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        BrandField.PHONE -> TextSheet(
            title = "Call button", label = "Business phone", initial = s.businessPhone, onChange = vm::setPhone, onDone = onDone,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        )
        BrandField.REVIEW -> TextSheet(
            title = "Leave a review", label = "Google review link", initial = s.reviewUrl, onChange = vm::setReview, onDone = onDone,
            helper = "Paste your Google review link.",
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        BrandField.BOOKING -> TextSheet(
            title = "Book again", label = "Booking link", initial = s.bookingUrl, onChange = vm::setBooking, onDone = onDone,
            helper = "Your booking page.",
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        BrandField.TRADE -> TradeSheet(s.trade, vm::setTrade, onDone)
    }
}

/** One field in a sheet. It saves as you type (so dismissing keeps it); Done closes. Empty turns the button off. */
@Composable
private fun TextSheet(
    title: String,
    label: String,
    initial: String,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    keyboard: KeyboardOptions,
    helper: String? = null,
) {
    // The field owns its text, so the saved (trimmed) value coming back never moves the cursor.
    var text by rememberSaveable { mutableStateOf(initial) }
    LaunchedEffect(text) { if (text.trim() != initial.trim()) onChange(text) }
    PsSheet(title, onDismiss = onDone) {
        PsTextField(
            text,
            { text = it },
            label = label,
            helper = helper,
            keyboardOptions = keyboard,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onChange(text); onDone() }),
        )
        Spacer(Modifier.height(Ps.space.xl))
        PsButton("Done", { onChange(text); onDone() }, fullWidth = true)
    }
}

@Composable
private fun TradeSheet(current: Trade, onPick: (Trade) -> Unit, onDone: () -> Unit) {
    PsSheet("Trade", onDismiss = onDone) {
        PsChipRow {
            Trade.entries.forEach { t -> PsChip(t.label, selected = t == current, onClick = { onPick(t) }) }
        }
        Text(
            "This only sets the label names we suggest. You can type your own any time.",
            Modifier.padding(top = 8.dp),
            style = Ps.type.caption,
            color = Ps.colors.inkSoft,
        )
        Spacer(Modifier.height(Ps.space.xl))
        PsButton("Done", onDone, fullWidth = true)
    }
}
