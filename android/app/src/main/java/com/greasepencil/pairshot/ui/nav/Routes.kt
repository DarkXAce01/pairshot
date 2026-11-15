package com.greasepencil.pairshot.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data class NewJobRoute(val editJobId: String? = null)
@Serializable data class JobRoute(val jobId: String)
@Serializable data class CameraRoute(val jobId: String, val after: Boolean, val pairId: String? = null)
@Serializable data class PairRoute(val pairId: String)
@Serializable data object SettingsRoute
@Serializable data object BrandRoute
@Serializable data object MessageRoute
@Serializable data class PreviewRoute(val jobId: String)
@Serializable data object CameraSettingsRoute
@Serializable data object StorageRoute
@Serializable data object AboutRoute

/** The account pages share one CloudViewModel, scoped to this graph. */
@Serializable data object AccountGraph
@Serializable data object AccountRoute
@Serializable data object ProRoute
@Serializable data object ConnectionsRoute
@Serializable data object IncomingJobsRoute
@Serializable data object CrmUpdatesRoute
@Serializable data object DeliveryLogRoute
@Serializable data object ApiKeysRoute
/** Debug builds only. */
@Serializable data object DeveloperRoute

/** Debug builds only: every Ps component in every state. */
@Serializable data object ComponentGalleryRoute
