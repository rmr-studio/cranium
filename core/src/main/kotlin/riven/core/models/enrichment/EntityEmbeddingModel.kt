package riven.core.models.enrichment

import java.time.ZonedDateTime
import java.util.*

/**
 * Domain model for entity embeddings.
 */
data class EntityEmbeddingModel(
    val id: UUID,
    val workspaceId: UUID,
    val entityId: UUID,
    val entityTypeId: UUID,
    val embedding: FloatArray,
    val embeddedAt: ZonedDateTime,
    val embeddingModel: String,
    val schemaVersion: Int,
    val truncated: Boolean
)
