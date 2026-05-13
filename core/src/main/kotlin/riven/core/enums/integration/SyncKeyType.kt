package cranium.core.enums.integration

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Discriminator for sub-variants of a single entity-type sync run.
 *
 * Stored on [cranium.core.entity.integration.IntegrationSyncStateEntity.syncKey] and propagated
 * through [cranium.core.models.integration.sync.SyncProcessingResult] so that multiple sync
 * artefacts (e.g. the raw entity sync vs. its derived note-embedding sync) can maintain
 * independent state rows for the same (connection, entity_type).
 *
 * Null syncKey indicates the canonical sync for that entity type.
 */
enum class SyncKeyType {
    @JsonProperty("note-embedding")
    NOTE_EMBEDDING
}
