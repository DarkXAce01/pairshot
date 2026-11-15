package com.greasepencil.pairshot.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.greasepencil.pairshot.BuildConfig
import com.greasepencil.pairshot.ui.brand.BrandScreen
import com.greasepencil.pairshot.ui.brand.MessageScreen
import com.greasepencil.pairshot.ui.camera.CameraScreen
import com.greasepencil.pairshot.ui.components.ps.PsTabBar
import com.greasepencil.pairshot.ui.theme.Ps
import com.greasepencil.pairshot.ui.dev.ComponentGalleryScreen
import com.greasepencil.pairshot.ui.home.HomeScreen
import com.greasepencil.pairshot.ui.job.JobScreen
import com.greasepencil.pairshot.ui.job.NewJobScreen
import com.greasepencil.pairshot.ui.onboarding.OnboardingScreen
import com.greasepencil.pairshot.ui.pair.PairScreen
import com.greasepencil.pairshot.ui.preview.PreviewScreen
import com.greasepencil.pairshot.ui.account.accountGraph
import com.greasepencil.pairshot.ui.settings.AboutScreen
import com.greasepencil.pairshot.ui.settings.CameraSettingsScreen
import com.greasepencil.pairshot.ui.settings.SettingsNav
import com.greasepencil.pairshot.ui.settings.SettingsScreen
import com.greasepencil.pairshot.ui.settings.StorageScreen

@Composable
fun AppNav(onboarded: Boolean, openJob: String? = null, onOpenedJob: () -> Unit = {}) {
    val nav = rememberNavController()
    // Fixed at first composition: finishing onboarding navigates, it doesn't rebuild the graph.
    val start: Any = remember { if (onboarded) HomeRoute else OnboardingRoute }
    val current by nav.currentBackStackEntryAsState()
    val tab = AppTab.of(current?.destination)
    // The tab last shown, so the bar keeps its selection while it fades out over a pushed screen.
    var lastTab by remember { mutableStateOf(AppTab.JOBS) }
    if (tab != null && tab != lastTab) lastTab = tab
    val tabFade = Ps.motion.tab
    Column(Modifier.fillMaxSize()) {
        // The tab bar takes the navigation bar inset itself, so the tab screens above it mustn't.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (tab != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            AppGraph(nav, start, tabFade)
        }
        AnimatedVisibility(tab != null, enter = fadeIn(tween(tabFade)), exit = fadeOut(tween(tabFade))) {
            PsTabBar(AppTab.psTabs, current = lastTab.ordinal, onSelect = { nav.selectTab(AppTab.entries[it]) })
        }
    }
    // After the NavHost, so the graph exists before we navigate.
    LaunchedEffect(openJob) {
        if (openJob != null && onboarded) {
            nav.navigate(JobRoute(openJob)) { popUpTo(HomeRoute) }
            onOpenedJob()
        }
    }
}

/** Every screen. Switching between tabs is a short cross-fade with no slide. */
@Composable
private fun AppGraph(nav: NavHostController, start: Any, tabFade: Int) {
    fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
        AppTab.of(initialState.destination) != null && AppTab.of(targetState.destination) != null
    NavHost(
        nav,
        startDestination = start,
        enterTransition = { if (betweenTabs()) fadeIn(tween(tabFade)) else fadeIn(tween(700)) },
        exitTransition = { if (betweenTabs()) fadeOut(tween(tabFade)) else fadeOut(tween(700)) },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(onDone = {
                nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
            })
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenJob = { nav.navigate(JobRoute(it)) },
                onNewJob = { nav.navigate(NewJobRoute()) },
                onFreeUpSpace = { nav.navigate(StorageRoute) },
                onComponentGallery = if (BuildConfig.DEBUG) {
                    { nav.navigate(ComponentGalleryRoute) }
                } else {
                    null
                },
            )
        }
        composable<NewJobRoute> {
            NewJobScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.navigate(JobRoute(id)) { popUpTo<NewJobRoute> { inclusive = true } }
                },
            )
        }
        composable<JobRoute> { entry ->
            val jobId = entry.toRoute<JobRoute>().jobId
            JobScreen(
                onBack = { nav.popBackStack() },
                onCamera = { after, pairId -> nav.navigate(CameraRoute(jobId, after, pairId)) },
                onOpenPair = { nav.navigate(PairRoute(it)) },
                onEdit = { nav.navigate(NewJobRoute(editJobId = jobId)) },
                onPreview = { nav.navigate(PreviewRoute(jobId)) },
            )
        }
        composable<CameraRoute> { CameraScreen(onClose = { nav.popBackStack() }) }
        composable<PairRoute> {
            PairScreen(
                onBack = { nav.popBackStack() },
                onRetakeAfter = { jobId, pairId -> nav.navigate(CameraRoute(jobId, after = true, pairId = pairId)) },
            )
        }
        composable<PreviewRoute> { PreviewScreen(onBack = { nav.popBackStack() }) }
        composable<BrandRoute> { BrandScreen(onMessage = { nav.navigate(MessageRoute) }, onSeePro = { nav.navigate(ProRoute) }) }
        composable<MessageRoute> { MessageScreen(onBack = { nav.popBackStack() }) }
        composable<SettingsRoute> {
            SettingsScreen(
                SettingsNav(
                    onAccount = { nav.navigate(AccountRoute) },
                    onPro = { nav.navigate(ProRoute) },
                    onCamera = { nav.navigate(CameraSettingsRoute) },
                    onStorage = { nav.navigate(StorageRoute) },
                    onConnections = { nav.navigate(ConnectionsRoute) },
                    onAbout = { nav.navigate(AboutRoute) },
                    onDeveloper = if (BuildConfig.DEBUG) {
                        { nav.navigate(DeveloperRoute) }
                    } else {
                        null
                    },
                ),
            )
        }
        composable<CameraSettingsRoute> { CameraSettingsScreen(onBack = { nav.popBackStack() }) }
        composable<StorageRoute> { StorageScreen(onBack = { nav.popBackStack() }) }
        composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
        accountGraph(nav)
        if (BuildConfig.DEBUG) {
            composable<ComponentGalleryRoute> { ComponentGalleryScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
