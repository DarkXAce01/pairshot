package com.greasepencil.pairshot.data.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the Pairshot server sends and takes. Field names match server/src exactly. */

internal val CloudJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

@Serializable data class AccountDto(val id: String, val plan: String, val email: String? = null, val inboundUrl: String? = null)
/** [integrityToken]: Play Integrity proof that this is the real app from Play (null when unavailable). */
@Serializable data class AuthRequest(val deviceId: String, val googleIdToken: String? = null, val integrityToken: String? = null)
@Serializable data class AuthResponse(val token: String, val account: AccountDto)

@Serializable data class UsageDto(
    val plan: String,
    val galleriesThisMonth: Int,
    val galleriesPerMonth: Int? = null,
    val photosPerGallery: Int,
    val storageBytes: Long,
    val storageLimitBytes: Long,
    val galleryLifetimeDays: Int? = null,
)

/** The client details that go with a send, for the business's own CRM. Never shown on the page. */
@Serializable data class SendJob(
    val clientName: String,
    val clientPhone: String? = null,
    val clientEmail: String? = null,
    val address: String? = null,
    val service: String? = null,
)

@Serializable data class SendRequest(
    val jobId: String,
    val externalId: String? = null,
    val job: SendJob,
    val manifest: kotlinx.serialization.json.JsonElement,
)

@Serializable data class PrepareResponse(val missing: List<String>)

@Serializable data class GalleryDto(
    val id: String,
    val jobId: String,
    val externalId: String? = null,
    val url: String,
    /** live, expired, revoked or draft */
    val state: String,
    val publishedAt: Long? = null,
    val updatedAt: Long? = null,
    val expiresAt: Long? = null,
    val firstViewedAt: Long? = null,
    val viewCount: Int = 0,
)
@Serializable data class GalleriesResponse(val galleries: List<GalleryDto>)

@Serializable data class CrmJobDto(
    val externalId: String,
    val clientName: String? = null,
    val clientPhone: String? = null,
    val clientEmail: String? = null,
    val address: String? = null,
    val service: String? = null,
    val scheduledAt: Long? = null,
    val notes: String? = null,
    val cancelled: Boolean = false,
)
@Serializable data class CrmPullResponse(val jobs: List<CrmJobDto>, val cursor: Long, val more: Boolean)
@Serializable data class InboundResponse(val inboundUrl: String)

@Serializable data class WebhookDto(val id: String, val url: String, val events: String, @SerialName("created_at") val createdAt: Long)
@Serializable data class DeliveryDto(
    val id: String,
    @SerialName("webhook_id") val webhookId: String,
    val event: String,
    /** pending, ok or failed */
    val status: String,
    val attempts: Int,
    @SerialName("last_status") val lastStatus: Int? = null,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("created_at") val createdAt: Long,
)
@Serializable data class WebhooksResponse(val webhooks: List<WebhookDto>, val deliveries: List<DeliveryDto>)
@Serializable data class AddWebhookRequest(val url: String, val events: List<String>? = null)
@Serializable data class NewWebhookDto(val id: String, val url: String, val events: List<String>, val secret: String)

@Serializable data class ApiKeyDto(
    val id: String,
    val prefix: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("last_used_at") val lastUsedAt: Long? = null,
)
@Serializable data class ApiKeysResponse(val keys: List<ApiKeyDto>)
@Serializable data class NewApiKeyDto(val id: String, val key: String, val prefix: String)

@Serializable data class ClaimProRequest(val purchaseToken: String, val productId: String)
/** status: "pro" (done) or "pending" (Google is still waiting for the money). */
@Serializable data class ClaimProResponse(val status: String, val account: AccountDto)

@Serializable data class OkDto(val ok: Boolean = true)
@Serializable data class PlanRequest(val plan: String)
@Serializable internal data class ErrorDto(val error: String = "error", val message: String? = null)
