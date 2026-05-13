package cranium.core.repository.catalog

import org.springframework.data.jpa.repository.JpaRepository
import cranium.core.entity.catalog.CatalogEntityTypeEntity
import java.util.*

/**
 * Repository for catalog entity type definitions.
 *
 * Provides queries for finding entity types by manifest and by manifest+key.
 */
interface CatalogEntityTypeRepository : JpaRepository<CatalogEntityTypeEntity, UUID> {

    /**
     * Find all entity types belonging to a manifest.
     */
    fun findByManifestId(manifestId: UUID): List<CatalogEntityTypeEntity>

    /**
     * Find a specific entity type within a manifest by its key.
     */
    fun findByManifestIdAndKey(manifestId: UUID, key: String): CatalogEntityTypeEntity?

    /**
     * Delete all entity types belonging to a manifest. Used for delete-reinsert reconciliation.
     */
    fun deleteByManifestId(manifestId: UUID)
}
