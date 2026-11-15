package com.greasepencil.pairshot.ui.cloud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greasepencil.pairshot.data.billing.ProBilling
import com.greasepencil.pairshot.data.billing.ProResult
import com.greasepencil.pairshot.data.cloud.ApiKeyDto
import com.greasepencil.pairshot.data.cloud.CloudSession
import com.greasepencil.pairshot.data.cloud.CloudState
import com.greasepencil.pairshot.data.cloud.CrmSync
import com.greasepencil.pairshot.data.cloud.GallerySender
import com.greasepencil.pairshot.data.cloud.UsageDto
import com.greasepencil.pairshot.data.cloud.WebhooksResponse
import com.greasepencil.pairshot.data.cloud.call
import com.greasepencil.pairshot.data.db.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudExtras(
    val busy: Boolean = false,
    val usage: UsageDto? = null,
    val hooks: WebhooksResponse? = null,
    val keys: List<ApiKeyDto> = emptyList(),
    /** Shown once, right after it's made: the API key or the webhook signing secret. */
    val reveal: Pair<String, String>? = null,
    /** Sign-in is under way (the Google sheet is up or the server is answering). */
    val signingIn: Boolean = false,
    /** Why the last sign-in didn't work, shown under the button. */
    val signInError: String? = null,
    /** Pro's price from Google Play, in the buyer's currency; null when Play can't sell it (debug builds). */
    val price: String? = null,
)

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val session: CloudSession,
    private val crm: CrmSync,
    private val sender: GallerySender,
    private val db: AppDatabase,
    private val billing: ProBilling,
) : ViewModel() {
    val state: StateFlow<CloudState?> = session.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val extras = MutableStateFlow(CloudExtras())
    private val msgs = Channel<String>(Channel.BUFFERED)
    val messages = msgs.receiveAsFlow()

    init {
        load()
        viewModelScope.launch { billing.results.collect { r -> proMessage(r)?.let { msgs.send(it) }; if (r is ProResult.Pro) load() } }
        viewModelScope.launch { val p = billing.price(); extras.update { it.copy(price = p) } }
    }

    private fun proMessage(r: ProResult): String? = when (r) {
        ProResult.Pro -> "Pro is on. Thank you."
        ProResult.Pending -> "Payment is pending. Pro switches on when Google Play confirms it."
        ProResult.NothingToRestore -> "No Pro purchase found for the Google account in the Play Store."
        ProResult.Cancelled -> null
        is ProResult.Failed -> r.message
    }

    fun buyPro(activity: android.app.Activity) = run { billing.buy(activity)?.let { msgs.send(it) } }

    fun restorePro() = run {
        val r = billing.restore()
        proMessage(r)?.let { msgs.send(it) }
        if (r is ProResult.Pro) load().join()
    }

    private fun run(okMessage: String? = null, block: suspend () -> Unit) = viewModelScope.launch {
        extras.update { it.copy(busy = true) }
        try {
            block()
            okMessage?.let { msgs.send(it) }
        } catch (e: Exception) {
            msgs.send(e.message ?: "Something went wrong.")
        } finally {
            extras.update { it.copy(busy = false) }
        }
    }

    /** Fetches plan, usage and (Pro) integrations, if this phone has signed in before. */
    fun load() = run {
        if (!session.state.first().signedIn) return@run
        var me = session.call { it.me() }
        session.saveAccount(me)
        // Bought Pro but the server never heard (offline, app closed mid-purchase): switch it on quietly.
        if (me.plan != "pro" && billing.restore() == ProResult.Pro) {
            msgs.send("Pro is on. Your earlier purchase was found.")
            me = session.call { it.me() }
        }
        val usage = session.call { it.usage() }
        extras.update { it.copy(usage = usage) }
        if (me.plan == "pro") {
            val hooks = session.call { it.webhooks() }
            val keys = session.call { it.apiKeys() }
            extras.update { it.copy(hooks = hooks, keys = keys) }
        } else {
            extras.update { it.copy(hooks = null, keys = emptyList()) }
        }
    }

    fun setServer(url: String) = run("Saved.") { session.setServerUrl(url) }

    /** [googleToken] fetches a Google ID token (needs the screen); used only when Google sign-in is set up. */
    fun connect(googleToken: suspend () -> String) = viewModelScope.launch {
        extras.update { it.copy(signingIn = true, signInError = null) }
        val result = runCatching {
            if (session.needsGoogle()) session.signInWithGoogle(googleToken()) else session.api()
        }
        extras.update { it.copy(signingIn = false, signInError = result.exceptionOrNull()?.let(::signInError)) }
        if (result.isSuccess) {
            msgs.send("Signed in.")
            load().join()
        }
    }

    fun copied(what: String) = viewModelScope.launch { msgs.send("$what copied.") }

    fun setPro(pro: Boolean) = run(if (pro) "Pro is on (test only)." else "Back to free.") {
        session.call { it.setDevPlan(if (pro) "pro" else "free") }
        load().join()
    }

    fun syncNow() = run {
        val r = crm.pull()
        sender.refresh()
        msgs.send(
            when {
                r == null -> "CRM sync is part of Pro."
                r.total == 0 -> "Up to date. No new jobs."
                else -> listOfNotNull(
                    r.added.takeIf { it > 0 }?.let { "$it new" },
                    r.updated.takeIf { it > 0 }?.let { "$it updated" },
                    r.removed.takeIf { it > 0 }?.let { "$it cancelled" },
                ).joinToString(", ", postfix = ".")
            },
        )
    }

    fun rotate() = run("New address made. Update it in Zapier/your CRM.") {
        session.setInboundUrl(session.call { it.rotateInbound() })
    }

    fun addHook(url: String) = run {
        val h = session.call { it.addWebhook(url.trim()) }
        extras.update { it.copy(reveal = "Signing secret (for checking the X-Pairshot-Signature header)" to h.secret) }
        load().join()
    }

    fun testHook(id: String) = run {
        val ok = session.call { it.testWebhook(id) }
        msgs.send(if (ok) "It answered. You're connected." else "It didn't answer with OK. Check the address.")
        load().join()
    }

    fun deleteHook(id: String) = run("Removed.") { session.call { it.deleteWebhook(id) }; load().join() }

    fun newKey() = run {
        val k = session.call { it.createApiKey() }
        extras.update { it.copy(reveal = "API key (shown once; paste it into Zapier or Make)" to k.key) }
        load().join()
    }

    fun deleteKey(id: String) = run("Key deleted. Anything using it stops working.") { session.call { it.deleteApiKey(id) }; load().join() }

    fun dismissReveal() = extras.update { it.copy(reveal = null) }

    fun signOut() = run("Signed out. Your jobs and links are still here; the next Send asks you to sign in.") {
        session.signOut()
        extras.value = CloudExtras()
    }

    fun deleteCloud() = run("Everything on the server is gone. Your jobs and photos on this phone are untouched.") {
        session.call { it.deleteEverything() }
        session.forgetAccount()
        db.galleries().deleteAll()
        extras.value = CloudExtras()
    }
}


/** A webhook event in words. */
fun eventName(e: String) = when (e) {
    "gallery.published" -> "Link sent"
    "gallery.updated" -> "Link updated"
    "gallery.viewed" -> "Link opened"
    "ping" -> "Test"
    else -> e
}

fun mb(bytes: Long) = if (bytes < 1_000_000_000) "%.0f MB".format(bytes / 1e6) else "%.1f GB".format(bytes / 1e9)

fun copyText(context: Context, text: String) =
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pairshot", text))
