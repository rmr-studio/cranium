package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.entity.integration.IntegrationDefinitionEntity
import riven.core.enums.integration.IntegrationCategory
import riven.core.models.catalog.ScannedManifest
import riven.core.repository.integration.IntegrationDefinitionRepository
import java.time.ZonedDateTime

/**
 * Seeds integration_definitions rows from scanned integration manifests.
 *
 * Reads category, nangoProviderKey, and iconUrl from the raw manifest JSON
 * and creates or updates the corresponding IntegrationDefinitionEntity.
 * Keyed on slug (manifest key) for idempotent upsert.
 *
 * Extracted as a separate service to ensure @Transactional is honored
 * via Spring AOP proxy (same pattern as IntegrationDefinitionStaleSyncService).
 */
@Service
class IntegrationDefinitionSeederService(
    private val integrationDefinitionRepository: IntegrationDefinitionRepository,
    private val logger: KLogger
) {

    /**
     * Seeds or updates integration definitions from scanned manifests.
     * Only processes non-stale manifests that were successfully loaded.
     */
    fun seedFromManifests(scannedManifests: List<ScannedManifest>) {
        var created = 0
        var updated = 0

        for (scanned in scannedManifests) {
            try {
                val result = upsertDefinition(scanned)
                if (result == UpsertResult.CREATED) created++ else if (result == UpsertResult.UPDATED) updated++
            } catch (e: Exception) {
                logger.warn(e) { "Failed to seed integration definition for: ${scanned.key}" }
            }
        }

        logger.info { "Integration definition seeding complete: $created created, $updated updated" }
    }

    // ------ Private Helpers ------

    private enum class UpsertResult { CREATED, UPDATED, UNCHANGED }

    private fun upsertDefinition(scanned: ScannedManifest): UpsertResult {
        val json = scanned.json
        val slug = scanned.key
        val name = json.get("name")?.asString() ?: slug
        val description = json.get("description")?.asString()
        val category = parseCategory(json.get("category")?.asString())
        val nangoProviderKey = json.get("nangoProviderKey")?.asString() ?: ""
        val iconUrl = json.get("iconUrl")?.asString()

        val existing = integrationDefinitionRepository.findBySlug(slug)

        if (existing != null) {
            return updateIfChanged(existing, name, description, category, nangoProviderKey, iconUrl)
        }

        integrationDefinitionRepository.save(
            IntegrationDefinitionEntity(
                slug = slug,
                name = name,
                iconUrl = iconUrl,
                description = description,
                category = category,
                nangoProviderKey = nangoProviderKey,
                capabilities = emptyMap(),
                syncConfig = emptyMap(),
                authConfig = emptyMap()
            )
        )
        return UpsertResult.CREATED
    }

    private fun updateIfChanged(
        existing: IntegrationDefinitionEntity,
        name: String,
        description: String?,
        category: IntegrationCategory,
        nangoProviderKey: String,
        iconUrl: String?
    ): UpsertResult {
        val changed = existing.name != name ||
            existing.description != description ||
            existing.category != category ||
            existing.nangoProviderKey != nangoProviderKey ||
            existing.iconUrl != iconUrl

        if (!changed) return UpsertResult.UNCHANGED

        existing.name = name
        existing.description = description
        existing.category = category
        existing.nangoProviderKey = nangoProviderKey
        existing.iconUrl = iconUrl
        existing.updatedAt = ZonedDateTime.now()
        integrationDefinitionRepository.save(existing)
        return UpsertResult.UPDATED
    }

    private fun parseCategory(value: String?): IntegrationCategory {
        if (value == null) return IntegrationCategory.OTHER
        return try {
            IntegrationCategory.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Unknown integration category: $value — defaulting to OTHER" }
            IntegrationCategory.OTHER
        }
    }
}
