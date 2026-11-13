package com.greasepencil.pairshot.data.cloud

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.greasepencil.pairshot.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play Integrity at sign-in: a token from Google that says "this is Pairshot from Play, on a real phone".
 * The server decides what to do with it (server/src/integrity.ts). Never blocks here: any problem
 * (no Play services, debug build, timeout) just sends no token.
 */
object PlayIntegrity {
    private val lock = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    /** Must match requestHashFor() on the server. */
    fun requestHash(deviceId: String): String =
        MessageDigest.getInstance("SHA-256").digest("pairshot-signin:$deviceId".toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun token(context: Context, deviceId: String): String? {
        if (BuildConfig.CLOUD_PROJECT_NUMBER == 0L) return null
        return withTimeoutOrNull(15_000) {
            runCatching {
                val p = lock.withLock {
                    provider ?: IntegrityManagerFactory.createStandard(context.applicationContext)
                        .prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(BuildConfig.CLOUD_PROJECT_NUMBER).build())
                        .await()
                        .also { provider = it }
                }
                p.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash(deviceId)).build()).await().token()
            }.onFailure { provider = null }.getOrNull()
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
}
