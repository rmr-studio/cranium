package riven.core.repository.catalog

import org.springframework.data.jpa.repository.JpaRepository
import riven.core.entity.catalog.CatalogSemanticMetadataEntity
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import java.util.*

/**
 * Repository for catalog semantic metadata.
 *
 * Provides queries for finding semantic metadata by entity type and by
 * the unique combination of entity type + target type + target ID.
 */
interface CatalogSemanticMetadataRepository : JpaRepository<CatalogSemanticMetadataEntity, UUID> {

    /**
     * Find all semantic metadata for a given catalog entity type.
     */
    fun findByCatalogEntityTypeId(catalogEntityTypeId: UUID): List<CatalogSemanticMetadataEntity>

    /**
     * Find a specific semantic metadata entry by its unique composite key.
     */
    fun findByCatalogEntityTypeIdAndTargetTypeAndTargetId(
        catalogEntityTypeId: UUID,
        targetType: SemanticMetadataTargetType,
        targetId: String
    ): CatalogSemanticMetadataEntity?

    /**
     * Batch-load semantic metadata for multiple entity types.
     * Used by ManifestCatalogService to avoid N+1 queries during hydration.
     */
    fun findByCatalogEntityTypeIdIn(catalogEntityTypeIds: List<UUID>): List<CatalogSemanticMetadataEntity>

    /**
     * Delete all semantic metadata for the given catalog entity type IDs.
     * Used for delete-reinsert reconciliation (delete metadata before deleting entity types).
     */
    fun deleteByCatalogEntityTypeIdIn(catalogEntityTypeIds: List<UUID>)
}
