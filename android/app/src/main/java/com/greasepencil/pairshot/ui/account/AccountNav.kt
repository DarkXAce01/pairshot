package com.greasepencil.pairshot.ui.account

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.greasepencil.pairshot.BuildConfig
import com.greasepencil.pairshot.ui.cloud.CloudViewModel
import com.greasepencil.pairshot.ui.cloud.copyText
import com.greasepencil.pairshot.ui.components.ps.PsBusyBar
import com.greasepencil.pairshot.ui.components.ps.PsConfirmDialog
import com.greasepencil.pairshot.ui.components.ps.PsRevealDialog
import com.greasepencil.pairshot.ui.components.ps.PsScaffold
import com.greasepencil.pairshot.ui.components.ps.PsSnackbarHost
import com.greasepencil.pairshot.ui.components.ps.PsTopBar
import com.greasepencil.pairshot.ui.nav.AccountGraph
import com.greasepencil.pairshot.ui.nav.AccountRoute
import com.greasepencil.pairshot.ui.nav.ApiKeysRoute
import com.greasepencil.pairshot.ui.nav.ComponentGalleryRoute
import com.greasepencil.pairshot.ui.nav.ConnectionsRoute
import com.greasepencil.pairshot.ui.nav.CrmUpdatesRoute
import com.greasepencil.pairshot.ui.nav.DeliveryLogRoute
import com.greasepencil.pairshot.ui.nav.DeveloperRoute
import com.greasepencil.pairshot.ui.nav.IncomingJobsRoute
import com.greasepencil.pairshot.ui.nav.ProRoute

/**
 * Account, Pro, Connections (and its three pages) and Developer: one nested graph, so they all
 * share one CloudViewModel and moving between them never reloads.
 */
fun NavGraphBuilder.accountGraph(nav: NavHostController) {
    navigation<AccountGraph>(startDestination = AccountRoute) {
        composable<AccountRoute> { entry ->
            AccountScreen(sharedCloudVm(nav, entry), onBack = { nav.popBackStack() }, onSeePro = { nav.navigate(ProRoute) })
        }
        composable<ProRoute> { entry -> ProScreen(sharedCloudVm(nav, entry), onBack = { nav.popBackStack() }) }
        composable<ConnectionsRoute> { entry ->
            ConnectionsScreen(
                sharedCloudVm(nav, entry),
                onBack = { nav.popBackStack() },
                onIncoming = { nav.navigate(IncomingJobsRoute) },
                onUpdates = { nav.navigate(CrmUpdatesRoute) },
                onKeys = { nav.navigate(ApiKeysRoute) },
                onSeePro = { nav.navigate(ProRoute) },
                onAccount = { nav.navigate(AccountRoute) },
            )
        }
        composable<IncomingJobsRoute> { entry -> IncomingJobsScreen(sharedCloudVm(nav, entry), onBack = { nav.popBackStack() }) }
        composable<CrmUpdatesRoute> { entry ->
            CrmUpdatesScreen(sharedCloudVm(nav, entry), onBack = { nav.popBackStack() }, onDeliveries = { nav.navigate(DeliveryLogRoute) })
        }
        composable<DeliveryLogRoute> { entry -> DeliveryLogScreen(sharedCloudVm(nav, entry), onBack = { nav.popBackStack() }) }
        composable<ApiKeysRoute> { entry -> ApiKeysScreen(sharedCloudVm(nav, entry), onBack = { nav.popBackStack() }) }
        if (BuildConfig.DEBUG) {
            composable<DeveloperRoute> { entry ->
                DeveloperScreen(sharedCloudVm(nav, entry), onBack = { nav.popBackStack() }, onGallery = { nav.navigate(ComponentGalleryRoute) })
            }
        }
    }
}

/** The one CloudViewModel of the account graph. */
@Composable
private fun sharedCloudVm(nav: NavHostController, entry: NavBackStackEntry): CloudViewModel {
    val parent = remember(entry) { nav.getBackStackEntry(AccountGraph) }
    return hiltViewModel(parent)
}

/** A question to confirm before doing something that can't easily be undone. */
internal data class Confirm(val title: String, val text: String, val yes: String, val destructive: Boolean, val action: () -> Unit)

/**
 * The frame every account page shares: top bar with the busy line under it, its own snackbars
 * (only the visible page collects them), the reveal-once dialog and any confirm.
 */
@Composable
internal fun AccountPage(
    title: String,
    vm: CloudViewModel,
    onBack: () -> Unit,
    confirm: Confirm? = null,
    onConfirmDone: () -> Unit = {},
    showTitle: Boolean = true,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val x by vm.extras.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    PsScaffold(
        topBar = {
            Column {
                PsTopBar(if (showTitle) title else "", onBack = onBack)
                PsBusyBar(x.busy)
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { PsSnackbarHost(snackbar) },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp), content = content)
    }
    x.reveal?.let { (what, secret) ->
        PsRevealDialog(
            what = what,
            secret = secret,
            onCopyClose = { copyText(context, secret); vm.copied("Secret"); vm.dismissReveal() },
            onSaved = vm::dismissReveal,
        )
    }
    confirm?.let { c ->
        PsConfirmDialog(
            title = c.title,
            text = c.text,
            confirm = c.yes,
            destructive = c.destructive,
            onConfirm = { onConfirmDone(); c.action() },
            onDismiss = onConfirmDone,
        )
    }
}

internal fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

/** The CRM setup guide on the account's server. */
internal fun crmGuideUrl(serverUrl: String): String? = serverUrl.takeIf { it.isNotBlank() }?.trimEnd('/')?.plus("/help/crm")
