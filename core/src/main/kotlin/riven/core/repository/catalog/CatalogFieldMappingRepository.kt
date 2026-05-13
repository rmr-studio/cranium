package cranium.core.repository.catalog

import org.springframework.data.jpa.repository.JpaRepository
import cranium.core.entity.catalog.CatalogFieldMappingEntity
import java.util.*

/**
 * Repository for catalog field mappings.
 *
 * Provides queries for finding field mappings by manifest and by manifest+entity type key.
 */
interface CatalogFieldMappingRepository : JpaRepository<CatalogFieldMappingEntity, UUID> {

    /**
     * Find all field mappings belonging to a manifest.
     */
    fun findByManifestId(manifestId: UUID): List<CatalogFieldMappingEntity>

    /**
     * Find a specific field mapping within a manifest by entity type key.
     */
    fun findByManifestIdAndEntityTypeKey(manifestId: UUID, entityTypeKey: String): CatalogFieldMappingEntity?

    /**
     * Find a specific field mapping within a manifest by Nango sync model name.
     * Used by the sync pipeline to resolve which field mapping applies when Nango sends a webhook.
     */
    fun findByManifestIdAndNangoModel(manifestId: UUID, nangoModel: String): CatalogFieldMappingEntity?

    /**
     * Delete all field mappings belonging to a manifest. Used for delete-reinsert reconciliation.
     */
    fun deleteByManifestId(manifestId: UUID)
}
