package riven.core.repository.catalog

import org.springframework.data.jpa.repository.JpaRepository
import riven.core.entity.catalog.CatalogRelationshipTargetRuleEntity
import java.util.*

/**
 * Repository for catalog relationship target rules.
 *
 * Provides queries for finding target rules by their parent relationship.
 */
interface CatalogRelationshipTargetRuleRepository : JpaRepository<CatalogRelationshipTargetRuleEntity, UUID> {

    /**
     * Find all target rules for a given catalog relationship.
     */
    fun findByCatalogRelationshipId(catalogRelationshipId: UUID): List<CatalogRelationshipTargetRuleEntity>

    /**
     * Batch-load target rules for multiple relationships.
     * Used by ManifestCatalogService to avoid N+1 queries during hydration.
     */
    fun findByCatalogRelationshipIdIn(catalogRelationshipIds: List<UUID>): List<CatalogRelationshipTargetRuleEntity>

    /**
     * Delete all target rules for the given catalog relationship IDs.
     * Used for delete-reinsert reconciliation (delete target rules before deleting relationships).
     */
    fun deleteByCatalogRelationshipIdIn(catalogRelationshipIds: List<UUID>)
}
