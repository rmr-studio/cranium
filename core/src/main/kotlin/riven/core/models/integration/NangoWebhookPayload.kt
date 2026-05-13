package cranium.core.models.integration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Inbound webhook payload from Nango.
 *
 * Covers both `auth` events (OAuth connection created/refreshed) and `sync` events
 * (sync execution completed). Fields not relevant to a particular event type will be null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoWebhookPayload(
    val type: String,                                              // "auth" | "sync"
    val operation: String? = null,                                 // "creation" | "refresh" | "override" for auth events
    val connectionId: String? = null,                              // Nango connection ID
    val providerConfigKey: String? = null,                         // Integration key (e.g. "hubspot")
    val provider: String? = null,
    val environment: String? = null,                               // "DEV" | "PROD"
    val success: Boolean? = null,
    val tags: NangoWebhookTags? = null,                            // Custom tags set during frontend auth initiation
    val syncName: String? = null,                                  // Present for sync events
    val model: String? = null,                                     // Present for sync events
    val modifiedAfter: String? = null,                             // Present for sync events
    val responseResults: NangoSyncResults? = null
)

/**
 * Custom tags propagated through the Nango auth flow.
 *
 * These tags are set by the frontend when initiating an OAuth flow and are echoed
 * back in the auth webhook. The three available tag fields are mapped to Cranium concepts:
 *
 * - [endUserId] = userId (UUID as string) — the user who initiated the auth flow
 * - [organizationId] = workspaceId (UUID as string) — the workspace context
 * - [endUserEmail] = integrationDefinitionId (UUID as string) — pragmatic reuse of the
 *   third available tag field to carry the integration definition ID
 *
 * This mapping is enforced in the webhook handler (Plan 03).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoWebhookTags(
    @JsonProperty("end_user_id") val endUserId: String? = null,
    @JsonProperty("end_user_email") val endUserEmail: String? = null,
    @JsonProperty("organization_id") val organizationId: String? = null
)

/**
 * Record change counts included in Nango sync webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoSyncResults(
    val added: Int? = null,
    val updated: Int? = null,
    val deleted: Int? = null
)

/**
 * Request body for POST /sync/trigger.
 */
data class NangoTriggerSyncRequest(
    @JsonProperty("provider_config_key") val providerConfigKey: String,
    @JsonProperty("connection_id") val connectionId: String? = null,
    val syncs: List<String>
)
