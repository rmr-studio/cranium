package riven.core.entity.enrichment

import jakarta.persistence.*
import org.hibernate.annotations.Array
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import riven.core.models.enrichment.EntityEmbeddingModel
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for the entity_embeddings table.
 *
 * Stores vector embeddings for entities. One embedding per entity (upsert pattern).
 * System-managed: no AuditableEntity, no SoftDeletable.
 *
 * Note: Do NOT override equals/hashCode for FloatArray — Hibernate uses entity identity.
 */
@Entity
@Table(name = "entity_embeddings")
data class EntityEmbeddingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    val entityId: UUID,

    @Column(name = "entity_type_id", nullable = false, columnDefinition = "uuid")
    val entityTypeId: UUID,

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536) // Must match EnrichmentConfigurationProperties.vectorDimensions and the SQL column definition. Runtime validation in EnrichmentService.storeEmbedding guards against mismatches.
    @Column(name = "embedding", nullable = false)
    val embedding: FloatArray,

    @Column(name = "embedded_at", nullable = false, columnDefinition = "timestamptz")
    val embeddedAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "embedding_model", nullable = false)
    val embeddingModel: String,

    @Column(name = "schema_version", nullable = false)
    val schemaVersion: Int = 1,

    @Column(name = "truncated", nullable = false)
    val truncated: Boolean = false
) {
    fun toModel(): EntityEmbeddingModel {
        val id = requireNotNull(this.id) { "EntityEmbeddingEntity must have an ID when mapping to model" }
        return EntityEmbeddingModel(
            id = id,
            workspaceId = workspaceId,
            entityId = entityId,
            entityTypeId = entityTypeId,
            embedding = embedding,
            embeddedAt = embeddedAt,
            embeddingModel = embeddingModel,
            schemaVersion = schemaVersion,
            truncated = truncated
        )
    }
}
