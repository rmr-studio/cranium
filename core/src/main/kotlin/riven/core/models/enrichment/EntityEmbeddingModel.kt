package riven.core.models.enrichment

import java.time.ZonedDateTime
import java.util.*

/**
 * Domain model for entity embeddings.
 *
 * Implemented as a regular class (not a `data class`) so that the [embedding]
 * `FloatArray` field uses content-based equality and hashing rather than
 * reference identity. Two instances with identical embedding vectors will
 * compare equal — important for collection operations, caching, and tests.
 */
class EntityEmbeddingModel(
    val id: UUID,
    val workspaceId: UUID,
    val entityId: UUID,
    val entityTypeId: UUID,
    val embedding: FloatArray,
    val embeddedAt: ZonedDateTime,
    val embeddingModel: String,
    val schemaVersion: Int,
    val truncated: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntityEmbeddingModel) return false
        return id == other.id &&
            workspaceId == other.workspaceId &&
            entityId == other.entityId &&
            entityTypeId == other.entityTypeId &&
            embedding.contentEquals(other.embedding) &&
            embeddedAt == other.embeddedAt &&
            embeddingModel == other.embeddingModel &&
            schemaVersion == other.schemaVersion &&
            truncated == other.truncated
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + workspaceId.hashCode()
        result = 31 * result + entityId.hashCode()
        result = 31 * result + entityTypeId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + embeddedAt.hashCode()
        result = 31 * result + embeddingModel.hashCode()
        result = 31 * result + schemaVersion
        result = 31 * result + truncated.hashCode()
        return result
    }
}
