package com.greasepencil.pairshot.data.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.greasepencil.pairshot.BuildConfig

/** The person backed out of the Google sheet: not an error, just nothing to do. */
class SignInCancelled : Exception("Sign-in cancelled.")

/**
 * Google sign-in through Android's Credential Manager. Used once, at the first Send: the token goes to
 * the server, which makes (or finds) the account. Pairshot never sees the Google password.
 */
object GoogleAuth {
    val configured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotEmpty()

    /** Shows Google's sheet and returns an ID token. [context] must be an Activity. */
    suspend fun idToken(context: Context): String {
        val id = BuildConfig.GOOGLE_WEB_CLIENT_ID
        val cm = CredentialManager.create(context)
        val result = try {
            // One tap: the phone's Google account, picked automatically if there's only one.
            val quick = GetGoogleIdOption.Builder()
                .setServerClientId(id)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(true)
                .build()
            cm.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(quick).build())
        } catch (e: NoCredentialException) {
            // Nothing for one-tap to offer (e.g. it was dismissed recently): the full "Sign in with Google" sheet.
            try {
                cm.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(id).build()).build())
            } catch (e: GetCredentialException) {
                throw map(e)
            }
        } catch (e: GetCredentialException) {
            throw map(e)
        }
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(cred.data).idToken
        }
        throw CloudException(0, "google_failed", "Google sign-in didn't work. Try again.")
    }

    private fun map(e: GetCredentialException): Exception = when (e) {
        is GetCredentialCancellationException -> SignInCancelled()
        is NoCredentialException -> CloudException(0, "no_google_account", "Add a Google account to this phone to send links.")
        else -> CloudException(0, "google_failed", "Google sign-in didn't work. Check your connection and try again.")
    }
}
