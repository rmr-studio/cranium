package cranium.core.repository.catalog

import org.springframework.data.jpa.repository.JpaRepository
import cranium.core.entity.catalog.CatalogRelationshipEntity
import java.util.*

/**
 * Repository for catalog relationship definitions.
 *
 * Provides queries for finding relationships by manifest and by manifest+key.
 */
interface CatalogRelationshipRepository : JpaRepository<CatalogRelationshipEntity, UUID> {

    /**
     * Find all relationships belonging to a manifest.
     */
    fun findByManifestId(manifestId: UUID): List<CatalogRelationshipEntity>

    /**
     * Find a specific relationship within a manifest by its key.
     */
    fun findByManifestIdAndKey(manifestId: UUID, key: String): CatalogRelationshipEntity?

    /**
     * Delete all relationships belonging to a manifest. Used for delete-reinsert reconciliation.
     */
    fun deleteByManifestId(manifestId: UUID)
}
