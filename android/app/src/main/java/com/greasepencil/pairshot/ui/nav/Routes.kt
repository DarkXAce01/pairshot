package com.greasepencil.pairshot.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data class NewJobRoute(val editJobId: String? = null)
@Serializable data class JobRoute(val jobId: String)
@Serializable data class CameraRoute(val jobId: String, val after: Boolean, val pairId: String? = null)
@Serializable data class PairRoute(val pairId: String)
@Serializable data object SettingsRoute
