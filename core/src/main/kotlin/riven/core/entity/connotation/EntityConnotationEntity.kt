package riven.core.entity.connotation

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import riven.core.models.connotation.ConnotationMetadataSnapshot
import java.time.ZonedDateTime
import java.util.UUID

/**
 * JPA entity for the `entity_connotation` table.
 *
 * Persists the polymorphic semantic snapshot (SENTIMENT + RELATIONAL + STRUCTURAL metadata
 * categories) captured at last enrichment time. One row per entity (upsert pattern via UNIQUE
 * on `entity_id`).
 *
 * System-managed: does NOT extend AuditableEntity, does NOT implement SoftDeletable.
 * Living on a sibling table avoids polluting `entities.last_modified_at` / `last_modified_by`
 * when re-enrichment writes occur. Per CLAUDE.md guidance: connotation is system-write-only.
 */
@Entity
@Table(name = "entity_connotation")
data class EntityConnotationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    val entityId: UUID,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Type(JsonBinaryType::class)
    @Column(name = "connotation_metadata", columnDefinition = "jsonb", nullable = false)
    val connotationMetadata: ConnotationMetadataSnapshot,

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    val updatedAt: ZonedDateTime = ZonedDateTime.now(),
)
