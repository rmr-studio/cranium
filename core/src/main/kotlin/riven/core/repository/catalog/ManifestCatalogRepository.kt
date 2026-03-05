package riven.core.repository.catalog

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.catalog.ManifestCatalogEntity
import riven.core.enums.catalog.ManifestType
import java.util.*

/**
 * Repository for manifest catalog entries.
 *
 * Provides queries for finding manifests by key, type, and key+type combination,
 * as well as bulk stale-marking operations for reconciliation.
 */
interface ManifestCatalogRepository : JpaRepository<ManifestCatalogEntity, UUID> {

    /**
     * Find all manifests with the given key (may span multiple manifest types).
     */
    fun findByKey(key: String): List<ManifestCatalogEntity>

    /**
     * Find all manifests of a given type (MODEL, TEMPLATE, INTEGRATION).
     */
    fun findByManifestType(manifestType: ManifestType): List<ManifestCatalogEntity>

    /**
     * Find a single manifest by its unique key + type combination.
     */
    fun findByKeyAndManifestType(key: String, manifestType: ManifestType): ManifestCatalogEntity?

    // ------ Query Surface (stale-filtered) ------

    /**
     * Find all non-stale manifests of a given type.
     * Used by ManifestCatalogService for listing active templates/models.
     */
    fun findByManifestTypeAndStaleFalse(manifestType: ManifestType): List<ManifestCatalogEntity>

    /**
     * Find a single non-stale manifest by key.
     * Returns null if the manifest doesn't exist or is stale.
     */
    fun findByKeyAndStaleFalse(key: String): ManifestCatalogEntity?

    // ------ Stale Reconciliation ------

    /**
     * Mark all catalog entries as stale. Called at the start of a load cycle
     * so that entries not present on disk remain stale after loading.
     */
    @Modifying
    @Transactional
    @Query("UPDATE ManifestCatalogEntity m SET m.stale = true")
    fun markAllStale()

    /**
     * Find all catalog entries that are currently stale (not present on disk after last load).
     */
    fun findByStaleTrue(): List<ManifestCatalogEntity>

    /**
     * Find stale catalog entries of a specific manifest type.
     * Used for syncing integration_definitions.stale after load cycle.
     */
    fun findByManifestTypeAndStaleTrue(manifestType: ManifestType): List<ManifestCatalogEntity>
}
