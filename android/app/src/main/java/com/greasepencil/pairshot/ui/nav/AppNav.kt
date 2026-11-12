package com.greasepencil.pairshot.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.greasepencil.pairshot.ui.camera.CameraScreen
import com.greasepencil.pairshot.ui.home.HomeScreen
import com.greasepencil.pairshot.ui.job.JobScreen
import com.greasepencil.pairshot.ui.job.NewJobScreen
import com.greasepencil.pairshot.ui.onboarding.OnboardingScreen
import com.greasepencil.pairshot.ui.pair.PairScreen
import com.greasepencil.pairshot.ui.preview.PreviewScreen
import com.greasepencil.pairshot.ui.settings.SettingsScreen

@Composable
fun AppNav(onboarded: Boolean) {
    val nav = rememberNavController()
    // Fixed at first composition: finishing onboarding navigates, it doesn't rebuild the graph.
    val start: Any = remember { if (onboarded) HomeRoute else OnboardingRoute }
    NavHost(nav, startDestination = start) {
        composable<OnboardingRoute> {
            OnboardingScreen(onDone = {
                nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
            })
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenJob = { nav.navigate(JobRoute(it)) },
                onNewJob = { nav.navigate(NewJobRoute()) },
                onSettings = { nav.navigate(SettingsRoute) },
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
        composable<PairRoute> { entry ->
            PairScreen(
                onBack = { nav.popBackStack() },
                onRetakeAfter = { jobId, pairId -> nav.navigate(CameraRoute(jobId, after = true, pairId = pairId)) },
            )
        }
        composable<PreviewRoute> { PreviewScreen(onBack = { nav.popBackStack() }) }
        composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
