package cranium.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import cranium.core.entity.entity.EntityTypeSemanticMetadataEntity
import cranium.core.enums.entity.semantics.SemanticMetadataTargetType
import java.util.*

/**
 * Repository for semantic metadata records.
 *
 * Derived queries automatically apply the @SQLRestriction("deleted = false") filter
 * from AuditableSoftDeletableEntity, making deleted rows invisible to all standard methods.
 *
 * The hardDeleteByTarget method is used when an attribute or relationship is removed from
 * an entity type — the orphaned metadata record is physically deleted (per locked decision).
 *
 * The softDeleteByEntityTypeId method is used for entity type deletion cascades, preserving
 * metadata rows for audit purposes while hiding them from live queries.
 */
interface EntityTypeSemanticMetadataRepository : JpaRepository<EntityTypeSemanticMetadataEntity, UUID> {

    fun findByEntityTypeIdAndTargetTypeAndTargetId(
        entityTypeId: UUID,
        targetType: SemanticMetadataTargetType,
        targetId: UUID
    ): Optional<EntityTypeSemanticMetadataEntity>

    fun findByEntityTypeIdIn(entityTypeIds: List<UUID>): List<EntityTypeSemanticMetadataEntity>

    fun findByEntityTypeIdAndTargetType(
        entityTypeId: UUID,
        targetType: SemanticMetadataTargetType
    ): List<EntityTypeSemanticMetadataEntity>

    fun findByEntityTypeId(entityTypeId: UUID): List<EntityTypeSemanticMetadataEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "DELETE FROM EntityTypeSemanticMetadataEntity e " +
            "WHERE e.entityTypeId = :entityTypeId " +
            "AND e.targetType = :targetType " +
            "AND e.targetId = :targetId"
    )
    fun hardDeleteByTarget(
        entityTypeId: UUID,
        targetType: SemanticMetadataTargetType,
        targetId: UUID
    )

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE EntityTypeSemanticMetadataEntity e " +
            "SET e.deleted = true, e.deletedAt = CURRENT_TIMESTAMP " +
            "WHERE e.entityTypeId = :entityTypeId AND e.deleted = false"
    )
    fun softDeleteByEntityTypeId(entityTypeId: UUID): Int
}
