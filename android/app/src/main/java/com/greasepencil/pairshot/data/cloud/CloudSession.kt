package com.greasepencil.pairshot.data.cloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greasepencil.pairshot.BuildConfig
import com.greasepencil.pairshot.domain.Ids
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cloudStore: DataStore<Preferences> by preferencesDataStore("cloud")

data class CloudState(
    val serverUrl: String = "",
    val deviceId: String = "",
    val token: String? = null,
    val accountId: String? = null,
    val plan: String = "free",
    val email: String? = null,
    val inboundUrl: String? = null,
    val crmCursor: Long = 0,
    val lastCrmSync: Long = 0,
) {
    val signedIn get() = token != null
    val isPro get() = plan == "pro"
}

/** Who the app is to the server. Signing in happens on the first Send, never before. */
interface CloudAccess {
    val state: Flow<CloudState>
    /** A client that is signed in (signing in now if needed). */
    suspend fun api(): CloudApi
    /** The session was refused (401): forget it so the next call signs in again. */
    suspend fun signedOut()
    suspend fun saveAccount(a: AccountDto)
    suspend fun setCrmCursor(cursor: Long, at: Long)
}

@Singleton
class CloudSession @Inject constructor(@ApplicationContext private val context: Context) : CloudAccess {
    private object Keys {
        val server = stringPreferencesKey("server_url")
        val device = stringPreferencesKey("device_id")
        val token = stringPreferencesKey("token")
        val account = stringPreferencesKey("account_id")
        val plan = stringPreferencesKey("plan")
        val email = stringPreferencesKey("email")
        val inbound = stringPreferencesKey("inbound_url")
        val cursor = longPreferencesKey("crm_cursor")
        val lastSync = longPreferencesKey("crm_last_sync")
    }
    private val signInLock = Mutex()

    override val state: Flow<CloudState> = context.cloudStore.data.map { p ->
        CloudState(
            serverUrl = p[Keys.server] ?: BuildConfig.DEFAULT_SERVER_URL,
            deviceId = p[Keys.device] ?: "",
            token = p[Keys.token],
            accountId = p[Keys.account],
            plan = p[Keys.plan] ?: "free",
            email = p[Keys.email],
            inboundUrl = p[Keys.inbound],
            crmCursor = p[Keys.cursor] ?: 0,
            lastCrmSync = p[Keys.lastSync] ?: 0,
        )
    }

    /** A client without signing in (for checking the server address). */
    suspend fun anonymous(): CloudApi = HttpCloudApi(requireServer(state.first()), null)

    override suspend fun api(): CloudApi = signInLock.withLock {
        val s = state.first()
        val server = requireServer(s)
        if (s.token != null) return HttpCloudApi(server, s.token)
        // With Google set up, signing in needs a screen (the Google sheet), so it can't happen here.
        if (GoogleAuth.configured) throw CloudException(401, "sign_in_needed", "Sign in with Google to send links.")
        signIn(server, AuthRequest(deviceId()))
    }

    /** Signs in with a token from [GoogleAuth.idToken]. */
    suspend fun signInWithGoogle(idToken: String) = signInLock.withLock {
        signIn(requireServer(state.first()), AuthRequest(deviceId(), googleIdToken = idToken))
        Unit
    }

    /** Signing in is needed before anything can be sent (Google sheet first). */
    suspend fun needsGoogle(): Boolean = GoogleAuth.configured && !state.first().signedIn

    private suspend fun deviceId(): String =
        state.first().deviceId.ifEmpty { Ids.new().also { id -> context.cloudStore.edit { it[Keys.device] = id } } }

    private suspend fun signIn(server: String, req: AuthRequest): CloudApi {
        val auth = HttpCloudApi(server, null).signIn(req)
        context.cloudStore.edit { it[Keys.token] = auth.token }
        saveAccount(auth.account)
        return HttpCloudApi(server, auth.token)
    }

    private fun requireServer(s: CloudState): String {
        if (s.serverUrl.isBlank()) throw CloudException(0, "no_server", "Add the server address in Settings → Cloud & CRM first.")
        return s.serverUrl
    }

    override suspend fun signedOut() { context.cloudStore.edit { it.remove(Keys.token) } }

    override suspend fun saveAccount(a: AccountDto) {
        context.cloudStore.edit {
            // A different account (e.g. after deleting cloud data) starts the CRM feed from scratch.
            if (it[Keys.account] != a.id) { it.remove(Keys.cursor); it.remove(Keys.lastSync) }
            it[Keys.account] = a.id
            it[Keys.plan] = a.plan
            a.email?.let { e -> it[Keys.email] = e } ?: it.remove(Keys.email)
            a.inboundUrl?.let { u -> it[Keys.inbound] = u }
        }
    }

    override suspend fun setCrmCursor(cursor: Long, at: Long) {
        context.cloudStore.edit { it[Keys.cursor] = cursor; it[Keys.lastSync] = at }
    }

    suspend fun setServerUrl(url: String) {
        val clean = url.trim().trimEnd('/').let { if (it.isEmpty() || it.startsWith("http")) it else "https://$it" }
        context.cloudStore.edit {
            if (it[Keys.server] != clean) {
                // A new server knows nothing of this session.
                it.remove(Keys.token); it.remove(Keys.account); it.remove(Keys.inbound); it.remove(Keys.cursor)
                it[Keys.plan] = "free"
            }
            it[Keys.server] = clean
        }
    }

    suspend fun setInboundUrl(url: String) { context.cloudStore.edit { it[Keys.inbound] = url } }

    /**
     * Signs this phone out. The server forgets the session (best effort: offline still signs out here).
     * Jobs, photos and sent links stay; the next Send asks for Google again.
     */
    suspend fun signOut() {
        val s = state.first()
        if (s.token != null && s.serverUrl.isNotBlank()) runCatching { HttpCloudApi(s.serverUrl, s.token).signOut() }
        forgetAccount()
    }

    /** After deleting cloud data: keep the server address and phone id, forget the rest. */
    suspend fun forgetAccount() {
        context.cloudStore.edit {
            it.remove(Keys.token); it.remove(Keys.account); it.remove(Keys.email); it.remove(Keys.inbound)
            it.remove(Keys.cursor); it.remove(Keys.lastSync)
            it[Keys.plan] = "free"
        }
    }
}

/** Runs [block] with a signed-in client, signing in again once if the session was refused. */
suspend fun <T> CloudAccess.call(block: suspend (CloudApi) -> T): T = try {
    block(api())
} catch (e: CloudException) {
    if (e.status != 401) throw e
    signedOut()
    block(api())
}
