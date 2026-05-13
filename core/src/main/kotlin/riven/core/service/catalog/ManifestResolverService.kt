package cranium.core.service.catalog

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import cranium.core.enums.catalog.ManifestType
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.models.catalog.*

/**
 * Resolves scanned manifests into fully resolved manifests ready for persistence.
 * Handles $ref resolution from in-memory model index, extend merge,
 * relationship normalization/validation, and field mapping validation.
 * Pure in-memory transformation -- no repository dependencies.
 */
@Service
class ManifestResolverService(
    private val objectMapper: ObjectMapper,
    private val logger: KLogger
) {

    // ------ Public API ------

    /**
     * Resolves a single scanned integration manifest into a fully resolved manifest.
     * Returns a manifest with stale=true if resolution fails (invalid relationships, etc.).
     */
    fun resolveManifest(scanned: ScannedManifest): ResolvedManifest {
        val json = scanned.json

        val entityTypes = resolveIntegrationEntityTypes(json)

        val relationships = normalizeRelationships(json, scanned.type)
        if (relationships == null) {
            return buildStaleManifest(scanned)
        }

        val entityTypeKeys = entityTypes.map { it.key }.toSet()
        if (relationships.isNotEmpty() && !validateRelationships(relationships, entityTypeKeys)) {
            return buildStaleManifest(scanned)
        }

        val entityTypeAttributeIndex = entityTypes.associate { it.key to it.schema.keys }
        val fieldMappings = resolveFieldMappings(json, entityTypeAttributeIndex)
        val syncModels = resolveSyncModels(json)

        return ResolvedManifest(
            key = scanned.key,
            name = json.get("name")?.asString() ?: scanned.key,
            description = json.get("description")?.asString(),
            type = scanned.type,
            manifestVersion = json.get("manifestVersion")?.asString(),
            entityTypes = entityTypes,
            relationships = relationships,
            fieldMappings = fieldMappings,
            syncModels = syncModels,
            stale = false
        )
    }

    // ------ Entity Type Resolution ------

    private fun resolveIntegrationEntityTypes(json: JsonNode): List<ResolvedEntityType> {
        val entityTypesArray = json.get("entityTypes") ?: return emptyList()
        return entityTypesArray.toList().map { parseEntityType(it, readonlyDefault = true) }
    }

    /** Extracts all fields from a JsonNode into a ResolvedEntityType. */
    @Suppress("UNCHECKED_CAST")
    private fun parseEntityType(json: JsonNode, readonlyDefault: Boolean): ResolvedEntityType {
        val displayName = json.get("displayName")
        val icon = json.get("icon")
        val semanticsNode = json.get("semantics")

        val attributesMap: Map<String, Any> = if (json.has("attributes")) {
            objectMapper.convertValue(json.get("attributes"), Map::class.java) as Map<String, Any>
        } else {
            emptyMap()
        }

        val semantics = if (semanticsNode != null) {
            ResolvedSemantics(
                definition = semanticsNode.get("definition")?.asString(),
                tags = semanticsNode.get("tags")?.toList()?.map { it.asString() } ?: emptyList()
            )
        } else null

        val entityTypeKey = json.get("key")?.asString()
        val connotationSignals = json.get("connotationSignals")
            ?.let { parseConnotationSignals(it, attributesMap.keys, entityTypeKey) }

        return ResolvedEntityType(
            key = entityTypeKey ?: "",
            displayNameSingular = displayName?.get("singular")?.asString() ?: "",
            displayNamePlural = displayName?.get("plural")?.asString() ?: "",
            iconType = icon?.get("type")?.asString() ?: "BOX",
            iconColour = icon?.get("colour")?.asString() ?: "NEUTRAL",
            semanticGroup = json.get("semanticGroup")?.asString() ?: "UNCATEGORIZED",
            lifecycleDomain = json.get("lifecycleDomain")?.asString()?.let { value ->
                enumValues<LifecycleDomain>().firstOrNull { it.name == value }
            },
            identifierKey = json.get("identifierKey")?.asString(),
            readonly = json.get("readonly")?.asBoolean() ?: readonlyDefault,
            schema = attributesMap,
            columns = null,
            semantics = semantics,
            connotationSignals = connotationSignals
        )
    }

    /**
     * Parses and cross-validates a `connotationSignals` block against the entity type's
     * declared attributes. Returns null (with a WARN log) on any cross-validation failure
     * or malformed payload — consistent with relationship/field-mapping handling.
     * Structural rejection happens earlier at JSON-schema validation time.
     */
    private fun parseConnotationSignals(
        node: JsonNode,
        knownAttributeKeys: Set<String>,
        entityTypeKey: String?,
    ): ConnotationSignals? {
        val signals = try {
            objectMapper.treeToValue(node, ConnotationSignals::class.java)
        } catch (e: JacksonException) {
            logger.warn { "Entity type '$entityTypeKey' has malformed connotationSignals: ${e.message} — skipping" }
            return null
        }

        if (signals.sentimentAttribute !in knownAttributeKeys) {
            logger.warn {
                "Entity type '$entityTypeKey' connotationSignals.sentimentAttribute " +
                    "'${signals.sentimentAttribute}' does not match any declared attribute — skipping"
            }
            return null
        }

        val unknownThemes = signals.themeAttributes.filterNot { it in knownAttributeKeys }
        if (unknownThemes.isNotEmpty()) {
            logger.warn {
                "Entity type '$entityTypeKey' connotationSignals.themeAttributes contain unknown " +
                    "attribute keys: $unknownThemes — skipping"
            }
            return null
        }

        val scale = signals.sentimentScale
        if (scale.sourceMin >= scale.sourceMax) {
            logger.warn {
                "Entity type '$entityTypeKey' connotationSignals.sentimentScale requires sourceMin < sourceMax " +
                    "(got ${scale.sourceMin} >= ${scale.sourceMax}) — skipping"
            }
            return null
        }
        if (scale.targetMin >= scale.targetMax) {
            logger.warn {
                "Entity type '$entityTypeKey' connotationSignals.sentimentScale requires targetMin < targetMax " +
                    "(got ${scale.targetMin} >= ${scale.targetMax}) — skipping"
            }
            return null
        }

        return signals
    }

    // ------ Relationship Resolution ------

    /**
     * Normalizes all relationships from JSON. Returns null if any has mutual exclusivity violation.
     */
    private fun normalizeRelationships(json: JsonNode, manifestType: ManifestType): List<NormalizedRelationship>? {
        val relationshipsArray = json.get("relationships") ?: return emptyList()
        val result = mutableListOf<NormalizedRelationship>()

        for (rel in relationshipsArray) {
            val normalized = normalizeRelationship(rel, manifestType)
            if (normalized == null) {
                return null
            }
            result.add(normalized)
        }
        return result
    }

    /**
     * Normalizes a single relationship. Returns null on mutual exclusivity violation.
     * Applies protected default: true for INTEGRATION, false for TEMPLATE.
     */
    private fun normalizeRelationship(rel: JsonNode, manifestType: ManifestType): NormalizedRelationship? {
        val hasShorthand = rel.has("targetEntityTypeKey") || rel.has("cardinality")
        val hasFullFormat = rel.has("targetRules")

        if (hasShorthand && hasFullFormat) {
            logger.warn { "Relationship '${rel.get("key")?.asString()}' has both shorthand and full format — skipping" }
            return null
        }

        if (!hasShorthand && !hasFullFormat) {
            logger.warn {
                "Relationship '${
                    rel.get("key")?.asString()
                }' has neither shorthand nor full format — skipping"
            }
            return null
        }

        val protectedDefault = manifestType == ManifestType.INTEGRATION
        val isProtected = rel.get("protected")?.asBoolean() ?: protectedDefault

        val icon = rel.get("icon")
        val semanticsNode = rel.get("semantics")
        val semantics = if (semanticsNode != null) {
            ResolvedRelationshipSemantics(
                definition = semanticsNode.get("definition")?.asString(),
                tags = semanticsNode.get("tags")?.toList()?.map { it.asString() } ?: emptyList()
            )
        } else null

        if (hasShorthand) {
            return normalizeShorthandRelationship(rel, isProtected, icon, semantics)
        }

        return normalizeFullFormatRelationship(rel, isProtected, icon, semantics)
    }

    private fun normalizeShorthandRelationship(
        rel: JsonNode,
        isProtected: Boolean,
        icon: JsonNode?,
        semantics: ResolvedRelationshipSemantics?
    ): NormalizedRelationship? {
        val key = rel.get("key")?.asString()
        val targetKey = rel.get("targetEntityTypeKey")?.asString()
        if (targetKey == null) {
            logger.warn { "Relationship '$key' shorthand format missing targetEntityTypeKey" }
            return null
        }
        val cardinalityStr = rel.get("cardinality")?.asString()
        if (cardinalityStr == null) {
            logger.warn { "Relationship '$key' shorthand format missing cardinality" }
            return null
        }
        val cardinality = try {
            EntityRelationshipCardinality.valueOf(cardinalityStr)
        } catch (_: IllegalArgumentException) {
            logger.warn { "Relationship '$key' has invalid cardinality '$cardinalityStr'" }
            return null
        }

        return NormalizedRelationship(
            key = rel.get("key").asString(),
            sourceEntityTypeKey = rel.get("sourceEntityTypeKey").asString(),
            name = rel.get("name").asString(),
            iconType = icon?.get("type")?.asString() ?: "LINK",
            iconColour = icon?.get("colour")?.asString() ?: "NEUTRAL",
            cardinalityDefault = cardinality,
            `protected` = isProtected,
            targetRules = listOf(NormalizedTargetRule(targetEntityTypeKey = targetKey)),
            semantics = semantics
        )
    }

    private fun normalizeFullFormatRelationship(
        rel: JsonNode,
        isProtected: Boolean,
        icon: JsonNode?,
        semantics: ResolvedRelationshipSemantics?
    ): NormalizedRelationship {
        val targetRulesArray = rel.get("targetRules") as? ArrayNode ?: objectMapper.createArrayNode()
        val targetRules = targetRulesArray.toList().map { rule ->
            val cardinalityOverrideStr = rule.get("cardinalityOverride")?.asString()
            NormalizedTargetRule(
                targetEntityTypeKey = rule.get("targetEntityTypeKey").asString(),
                cardinalityOverride = cardinalityOverrideStr?.let {
                    try {
                        EntityRelationshipCardinality.valueOf(it)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                },
                inverseVisible = rule.get("inverseVisible")?.asBoolean(),
                inverseName = rule.get("inverseName")?.asString()
            )
        }

        // For full format without explicit cardinality, default to ONE_TO_MANY
        val cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY

        return NormalizedRelationship(
            key = rel.get("key").asString(),
            sourceEntityTypeKey = rel.get("sourceEntityTypeKey").asString(),
            name = rel.get("name").asString(),
            iconType = icon?.get("type")?.asString() ?: "LINK",
            iconColour = icon?.get("colour")?.asString() ?: "NEUTRAL",
            cardinalityDefault = cardinalityDefault,
            `protected` = isProtected,
            targetRules = targetRules,
            semantics = semantics
        )
    }

    /**
     * Validates that all relationship source/target keys exist in the entity type set.
     * Also checks for duplicate relationship keys.
     */
    private fun validateRelationships(
        relationships: List<NormalizedRelationship>,
        entityTypeKeys: Set<String>
    ): Boolean {
        val seenKeys = mutableSetOf<String>()

        for (rel in relationships) {
            // Check for duplicate keys
            if (!seenKeys.add(rel.key)) {
                logger.warn { "Duplicate relationship key '${rel.key}'" }
                return false
            }

            // Validate source key
            if (rel.sourceEntityTypeKey !in entityTypeKeys) {
                logger.warn { "Relationship '${rel.key}' references unknown source entity type '${rel.sourceEntityTypeKey}'" }
                return false
            }

            // Validate target keys
            for (rule in rel.targetRules) {
                if (rule.targetEntityTypeKey !in entityTypeKeys) {
                    logger.warn { "Relationship '${rel.key}' references unknown target entity type '${rule.targetEntityTypeKey}'" }
                    return false
                }
            }
        }
        return true
    }

    // ------ Field Mapping Resolution ------

    /**
     * Resolves field mappings for integration manifests.
     * Validates that mapping keys exist in the target entity type's attribute set.
     * Invalid keys are skipped with WARN.
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveFieldMappings(
        json: JsonNode,
        entityTypeAttributeIndex: Map<String, Set<String>>
    ): List<ResolvedFieldMapping> {
        val fieldMappingsArray = json.get("fieldMappings") ?: return emptyList()
        val result = mutableListOf<ResolvedFieldMapping>()

        for (mappingEntry in fieldMappingsArray) {
            val entityTypeKey = mappingEntry.get("entityTypeKey")?.asString() ?: continue
            val mappingsNode = mappingEntry.get("mappings") ?: continue
            val validAttributeKeys = entityTypeAttributeIndex[entityTypeKey] ?: continue

            val validMappings = mutableMapOf<String, Any>()
            mappingsNode.properties().forEach { (key, value) ->
                if (key in validAttributeKeys) {
                    validMappings[key] = objectMapper.convertValue(value, Map::class.java) as Map<String, Any>
                } else {
                    logger.warn { "Field mapping key '$key' not found in entity type '$entityTypeKey' attributes, skipping" }
                }
            }

            if (validMappings.isNotEmpty()) {
                result.add(ResolvedFieldMapping(entityTypeKey = entityTypeKey, mappings = validMappings))
            }
        }
        return result
    }

    // ------ Sync Model Resolution ------

    /** Extracts the syncModels map (Nango model name -> entity type key) from the manifest JSON. */
    private fun resolveSyncModels(json: JsonNode): Map<String, String> {
        val node = json.get("syncModels") ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        node.properties().forEach { (nangoModel, entityTypeKeyNode) ->
            map[nangoModel] = entityTypeKeyNode.asString()
        }
        return map.toMap()
    }

    // ------ Helpers ------

    private fun buildStaleManifest(scanned: ScannedManifest): ResolvedManifest {
        return ResolvedManifest(
            key = scanned.key,
            name = scanned.json.get("name")?.asString() ?: scanned.key,
            description = scanned.json.get("description")?.asString(),
            type = scanned.type,
            manifestVersion = scanned.json.get("manifestVersion")?.asString(),
            entityTypes = emptyList(),
            relationships = emptyList(),
            fieldMappings = emptyList(),
            stale = true
        )
    }
}
