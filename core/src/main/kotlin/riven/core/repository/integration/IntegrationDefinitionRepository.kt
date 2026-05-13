package cranium.core.repository.integration

import org.springframework.data.jpa.repository.JpaRepository
import cranium.core.entity.integration.IntegrationDefinitionEntity
import cranium.core.enums.integration.IntegrationCategory
import java.util.*

/**
 * Repository for integration definitions (global catalog).
 *
 * Provides queries for finding integrations by slug, active status, and category.
 */
interface IntegrationDefinitionRepository : JpaRepository<IntegrationDefinitionEntity, UUID> {

    /**
     * Find an integration by its unique slug.
     */
    fun findBySlug(slug: String): IntegrationDefinitionEntity?

    /**
     * Find all active (non-stale) integrations.
     */
    fun findByStaleIsFalse(): List<IntegrationDefinitionEntity>

    /**
     * Find all integrations in a specific category.
     */
    fun findByCategory(category: IntegrationCategory): List<IntegrationDefinitionEntity>
}
