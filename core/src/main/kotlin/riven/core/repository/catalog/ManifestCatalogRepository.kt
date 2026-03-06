package riven.core.repository.catalog

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
    @Query("SELECT m FROM ManifestCatalogEntity m WHERE m.key = :key")
    fun findByKey(@Param("key") key: String): List<ManifestCatalogEntity>

    /**
     * Find all manifests of a given type (MODEL, TEMPLATE, INTEGRATION).
     */
    @Query("SELECT m FROM ManifestCatalogEntity m WHERE m.manifestType = :manifestType")
    fun findByManifestType(@Param("manifestType") manifestType: ManifestType): List<ManifestCatalogEntity>

    /**
     * Find a single manifest by its unique key + type combination.
     */
    @Query("SELECT m FROM ManifestCatalogEntity m WHERE m.key = :key AND m.manifestType = :manifestType")
    fun findByKeyAndManifestType(@Param("key") key: String, @Param("manifestType") manifestType: ManifestType): ManifestCatalogEntity?

    // ------ Query Surface (stale-filtered) ------

    /**
     * Find all non-stale manifests of a given type.
     * Used by ManifestCatalogService for listing active templates/models.
     */
    @Query("SELECT m FROM ManifestCatalogEntity m WHERE m.manifestType = :manifestType AND m.stale = false")
    fun findByManifestTypeAndStaleFalse(@Param("manifestType") manifestType: ManifestType): List<ManifestCatalogEntity>

    /**
     * Find a single non-stale manifest by key and type.
     * Returns null if the manifest doesn't exist or is stale.
     */
    @Query("SELECT m FROM ManifestCatalogEntity m WHERE m.key = :key AND m.manifestType = :manifestType AND m.stale = false")
    fun findByKeyAndManifestTypeAndStaleFalse(@Param("key") key: String, @Param("manifestType") manifestType: ManifestType): ManifestCatalogEntity?

    // ------ Stale Reconciliation ------

    /**
     * Find all catalog entries that are currently stale (not present on disk after last load).
     */
    @Query("SELECT m FROM ManifestCatalogEntity m WHERE m.stale = true")
    fun findByStaleTrue(): List<ManifestCatalogEntity>

    /**
     * Find stale catalog entries of a specific manifest type.
     * Used for syncing integration_definitions.stale after load cycle.
     */
    @Query("SELECT m FROM ManifestCatalogEntity m WHERE m.manifestType = :manifestType AND m.stale = true")
    fun findByManifestTypeAndStaleTrue(@Param("manifestType") manifestType: ManifestType): List<ManifestCatalogEntity>
}
