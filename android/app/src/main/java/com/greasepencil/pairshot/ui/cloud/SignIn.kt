package com.greasepencil.pairshot.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greasepencil.pairshot.data.cloud.SignInCancelled

/** The one sign-in button, used on the job screen's sheet and in Account & CRM. */
@Composable
fun GoogleSignInButton(onClick: () -> Unit, busy: Boolean, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(12.dp))
            Text("Signing in…")
        } else {
            Icon(Icons.Outlined.AccountCircle, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google")
        }
    }
}

/** A line with an icon, for "what this means" lists. The icon is decoration; the text says it all. */
@Composable
fun FactRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** What the person is agreeing to, before Google's own sheet appears. */
@Composable
fun SignInFacts() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FactRow(Icons.Outlined.PhoneAndroid, "Your jobs and photos stay on this phone. Only what you send goes online.")
        FactRow(Icons.Outlined.Lock, "Google shares only your name and email. Pairshot never sees your password.")
        FactRow(Icons.Outlined.Timer, "Free plan: 3 client links a month, each live for 14 days.")
    }
}

/** An error said out loud by screen readers as soon as it appears. */
@Composable
fun ErrorLine(message: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Shown at the first Send: says why, then opens Google's sheet. It stays open (with any error)
 * until sign-in works or the person backs out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(busy: Boolean, error: String?, onContinue: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Sign in to send your link", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                "This is the only time Pairshot asks. It keeps your links tied to you, so you can update or close them later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignInFacts()
            error?.let { ErrorLine(it) }
            GoogleSignInButton(onClick = onContinue, busy = busy)
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Not now") }
        }
    }
}

/** Plain words for what went wrong; null when the person just backed out. */
fun signInError(e: Throwable): String? = when (e) {
    is SignInCancelled -> null
    else -> e.message ?: "Couldn't sign in. Check your connection and try again."
}
