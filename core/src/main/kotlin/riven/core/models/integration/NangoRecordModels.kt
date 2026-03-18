package riven.core.models.integration

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Paginated response from GET /records.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoRecordsPage(
    val records: List<NangoRecord> = emptyList(),
    @JsonProperty("next_cursor") val nextCursor: String? = null
)

/**
 * A single record returned from Nango's GET /records endpoint.
 *
 * Nango records have a known `_nango_metadata` field plus an arbitrary set of
 * provider-specific fields (e.g. `id`, `email`, `name` for a HubSpot contact).
 * The provider-specific fields are captured generically in [payload] via @JsonAnySetter.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoRecord(
    @JsonProperty("_nango_metadata") val nangoMetadata: NangoRecordMetadata,
    val payload: MutableMap<String, Any?> = mutableMapOf()
) {
    @JsonAnySetter
    fun setPayloadField(key: String, value: Any?) {
        payload[key] = value
    }
}

/**
 * Nango-managed metadata attached to every record.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoRecordMetadata(
    @JsonProperty("last_action") val lastAction: NangoRecordAction,
    @JsonProperty("cursor") val cursor: String,
    @JsonProperty("first_seen_at") val firstSeenAt: String? = null,
    @JsonProperty("last_modified_at") val lastModifiedAt: String? = null,
    @JsonProperty("deleted_at") val deletedAt: String? = null
)

/**
 * Actions that Nango records can have — indicates what happened to the record in the last sync.
 */
enum class NangoRecordAction {
    @JsonProperty("ADDED")
    ADDED,

    @JsonProperty("UPDATED")
    UPDATED,

    @JsonProperty("DELETED")
    DELETED
}
