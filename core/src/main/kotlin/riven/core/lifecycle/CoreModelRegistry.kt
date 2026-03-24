package riven.core.lifecycle

import riven.core.enums.catalog.ManifestType
import riven.core.models.catalog.NormalizedRelationship
import riven.core.models.catalog.ResolvedManifest

/**
 * Central registry of all core lifecycle model definitions.
 *
 * Responsibilities:
 * - Collects all model sets (B2C SaaS, DTC E-commerce)
 * - Validates cross-references at boot time (fail fast)
 * - Converts model sets to ResolvedManifest for catalog population
 * - Provides lookup by key for downstream services
 *
 * Models with the same key may appear across different model sets (e.g., SaasBillingEventModel
 * and DtcBillingEventModel both use key "billing-event"). This is expected — each business type
 * gets a tailored variant of the same conceptual entity type.
 */
object CoreModelRegistry {

    private val allModelSets: List<CoreModelSet> = listOf(
        B2C_SAAS_MODELS,
        DTC_ECOMMERCE_MODELS,
    )

    /** All unique core model object instances across all model sets. Validates on first access. */
    val allModels: List<CoreModelDefinition> by lazy {
        validate()
        allModelSets.flatMap { it.models }.distinctBy { System.identityHashCode(it) }
    }

    /** Find a model set by its manifest key. */
    fun findModelSet(manifestKey: String): CoreModelSet? {
        return allModelSets.find { it.manifestKey == manifestKey }
    }

    /** Find a model definition by entity type key within a specific model set. */
    fun findModel(manifestKey: String, modelKey: String): CoreModelDefinition? {
        return findModelSet(manifestKey)?.models?.find { it.key == modelKey }
    }

    // ------ Validation (runs automatically on first access to allModels) ------

    private fun validate() {
        validateNoDuplicateKeysWithinSets()
        allModelSets.forEach { validateModelSetRelationships(it) }
    }

    private fun validateNoDuplicateKeysWithinSets() {
        // Validate no duplicate keys within a single model set
        for (modelSet in allModelSets) {
            val keys = modelSet.models.map { it.key }
            val duplicates = keys.groupBy { it }.filter { it.value.size > 1 }.keys
            check(duplicates.isEmpty()) {
                "Duplicate model keys within set '${modelSet.manifestKey}': $duplicates"
            }
        }

        // Validate no duplicate model set keys
        val setKeys = allModelSets.map { it.manifestKey }
        val dupSets = setKeys.groupBy { it }.filter { it.value.size > 1 }.keys
        check(dupSets.isEmpty()) {
            "Duplicate core model set keys found: $dupSets"
        }
    }

    internal fun validateModelSetRelationships(modelSet: CoreModelSet) {
        val modelKeys = modelSet.models.map { it.key }.toSet()

        // Validate relationships declared on models
        for (model in modelSet.models) {
            for (rel in model.relationships) {
                check(rel.targetModelKey in modelKeys) {
                    "Model '${model.key}' relationship '${rel.key}' targets '${rel.targetModelKey}' " +
                        "which is not in model set '${modelSet.manifestKey}'"
                }
            }
        }

        // Validate additional relationships declared on the model set
        for (rel in modelSet.additionalRelationships) {
            check(rel.sourceModelKey in modelKeys) {
                "Model set '${modelSet.manifestKey}' additional relationship '${rel.key}' has source " +
                    "'${rel.sourceModelKey}' which is not in this model set"
            }
            check(rel.targetModelKey in modelKeys) {
                "Model set '${modelSet.manifestKey}' additional relationship '${rel.key}' targets " +
                    "'${rel.targetModelKey}' which is not in this model set"
            }
        }
    }

    // ------ Conversion to ResolvedManifest ------

    /**
     * Converts a CoreModelSet to a ResolvedManifest for consumption by ManifestUpsertService.
     * This is the bridge between Kotlin core model definitions and the existing catalog pipeline.
     */
    fun toResolvedManifest(modelSet: CoreModelSet): ResolvedManifest {
        val entityTypes = modelSet.models.map { it.toResolvedEntityType() }
        val relationships = collectRelationships(modelSet)

        return ResolvedManifest(
            key = modelSet.manifestKey,
            name = modelSet.name,
            description = modelSet.description,
            type = ManifestType.TEMPLATE,
            manifestVersion = "1.0",
            entityTypes = entityTypes,
            relationships = relationships,
            fieldMappings = emptyList(),
            stale = false,
        )
    }

    /** All ResolvedManifests for boot-time catalog population. */
    fun allResolvedManifests(): List<ResolvedManifest> {
        return allModelSets.map { toResolvedManifest(it) }
    }

    private fun collectRelationships(modelSet: CoreModelSet): List<NormalizedRelationship> {
        val modelRelationships = modelSet.models.flatMap { it.toNormalizedRelationships() }
        val additionalRelationships = modelSet.additionalRelationships.map { it.toNormalized() }
        return modelRelationships + additionalRelationships
    }
}
