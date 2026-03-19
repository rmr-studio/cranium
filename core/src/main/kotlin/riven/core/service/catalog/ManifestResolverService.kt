package riven.core.service.catalog

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.enums.catalog.ManifestType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.models.catalog.*

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
     * Resolves a single scanned manifest into a fully resolved manifest.
     * Returns a manifest with stale=true if resolution fails (missing $ref, invalid relationships, etc.).
     */
    fun resolveManifest(scanned: ScannedManifest, modelIndex: Map<String, JsonNode>): ResolvedManifest {
        val json = scanned.json

        val (entityTypes, refFailed) = resolveEntityTypes(json, scanned.type, modelIndex)
        if (refFailed) {
            return buildStaleManifest(scanned)
        }

        val relationships = normalizeRelationships(json, scanned.type)
        if (relationships == null) {
            return buildStaleManifest(scanned)
        }

        val localEntityTypeKeys = entityTypes.map { it.key }.toSet()
        val validEntityTypeKeys = if (scanned.type == ManifestType.TEMPLATE) {
            localEntityTypeKeys + modelIndex.keys
        } else {
            localEntityTypeKeys
        }
        if (relationships.isNotEmpty() && !validateRelationships(relationships, validEntityTypeKeys)) {
            return buildStaleManifest(scanned)
        }

        val entityTypeAttributeIndex = entityTypes.associate { it.key to it.schema.keys }
        val fieldMappings = resolveFieldMappings(json, entityTypeAttributeIndex)

        return ResolvedManifest(
            key = scanned.key,
            name = json.get("name")?.asText() ?: scanned.key,
            description = json.get("description")?.asText(),
            type = scanned.type,
            manifestVersion = json.get("manifestVersion")?.asText(),
            entityTypes = entityTypes,
            relationships = relationships,
            fieldMappings = fieldMappings,
            stale = false
        )
    }

    /**
     * Resolves a bundle manifest into a ResolvedBundle.
     * Bundles are lightweight — they only store a list of template keys.
     * No entity type resolution needed; that happens at installation time.
     */
    fun resolveBundle(scanned: ScannedManifest): ResolvedBundle {
        require(scanned.type == ManifestType.BUNDLE) { "Expected BUNDLE manifest, got ${scanned.type}" }
        val json = scanned.json

        return ResolvedBundle(
            key = json.get("key").asText(),
            name = json.get("name").asText(),
            description = json.get("description")?.asText(),
            manifestVersion = json.get("manifestVersion")?.asText(),
            templateKeys = json.get("templates").map { it.asText() },
        )
    }

    // ------ Entity Type Resolution ------

    /**
     * Resolves entity types from the manifest JSON.
     * Returns the list of resolved entity types and whether any $ref failed.
     */
    private fun resolveEntityTypes(
        json: JsonNode,
        type: ManifestType,
        modelIndex: Map<String, JsonNode>
    ): Pair<List<ResolvedEntityType>, Boolean> {
        return when (type) {
            ManifestType.MODEL -> {
                val entityType = parseEntityType(json, readonlyDefault = false)
                listOf(entityType) to false
            }
            ManifestType.TEMPLATE -> resolveTemplateEntityTypes(json, modelIndex)
            ManifestType.INTEGRATION -> resolveIntegrationEntityTypes(json)
            ManifestType.BUNDLE -> emptyList<ResolvedEntityType>() to false
            ManifestType.LIFECYCLE_SPINE -> resolveTemplateEntityTypes(json, modelIndex)
        }
    }

    private fun resolveTemplateEntityTypes(
        json: JsonNode,
        modelIndex: Map<String, JsonNode>
    ): Pair<List<ResolvedEntityType>, Boolean> {
        val entityTypesArray = json.get("entityTypes") ?: return emptyList<ResolvedEntityType>() to false
        val resolved = mutableListOf<ResolvedEntityType>()

        for (entry in entityTypesArray) {
            val ref = entry.get("\$ref")?.asText()
            if (ref != null) {
                val entityType = resolveRefEntityType(entry, modelIndex)
                if (entityType == null) {
                    logger.warn { "Unresolved \$ref '$ref' in template" }
                    return emptyList<ResolvedEntityType>() to true
                }
                resolved.add(entityType)
            } else {
                resolved.add(parseEntityType(entry, readonlyDefault = false))
            }
        }
        return resolved to false
    }

    private fun resolveIntegrationEntityTypes(json: JsonNode): Pair<List<ResolvedEntityType>, Boolean> {
        val entityTypesArray = json.get("entityTypes") ?: return emptyList<ResolvedEntityType>() to false
        val resolved = entityTypesArray.map { parseEntityType(it, readonlyDefault = true) }
        return resolved to false
    }

    /** Looks up $ref in model index. Applies extend if present. Returns null if model missing. */
    private fun resolveRefEntityType(entry: JsonNode, modelIndex: Map<String, JsonNode>): ResolvedEntityType? {
        val ref = entry.get("\$ref")?.asText() ?: return null
        val modelKey = ref.removePrefix("models/")
        val model = modelIndex[modelKey] ?: return null

        val extend = entry.get("extend")
        return if (extend != null) {
            val merged = applyExtend(model, extend)
            parseEntityType(merged, readonlyDefault = false)
        } else {
            parseEntityType(model, readonlyDefault = false)
        }
    }

    /**
     * Shallow additive merge of extend onto base model.
     * Scalar overrides: description, icon, semanticGroup, identifierKey.
     * Attributes: new keys added, existing keys preserved (base wins on conflict).
     * Semantic tags: appended from extend to base.
     */
    private fun applyExtend(base: JsonNode, extend: JsonNode): JsonNode {
        val merged = base.deepCopy<ObjectNode>()

        // Scalar overrides
        extend.get("description")?.let { merged.set<JsonNode>("description", it) }
        extend.get("icon")?.let { merged.set<JsonNode>("icon", it) }
        extend.get("semanticGroup")?.let { merged.set<JsonNode>("semanticGroup", it) }
        extend.get("lifecycleDomain")?.let { merged.set<JsonNode>("lifecycleDomain", it) }
        extend.get("identifierKey")?.let { merged.set<JsonNode>("identifierKey", it) }

        // Additive attributes (base wins on conflict)
        val extendAttrs = extend.get("attributes")
        if (extendAttrs != null) {
            val baseAttrs = merged.get("attributes") as? ObjectNode
                ?: objectMapper.createObjectNode().also { merged.set<JsonNode>("attributes", it) }
            extendAttrs.properties().forEach { (key, value) ->
                if (!baseAttrs.has(key)) {
                    baseAttrs.set<JsonNode>(key, value)
                }
            }
        }

        // Append semantic tags
        val extendTags = extend.at("/semantics/tags")
        if (extendTags.isArray) {
            val baseTags = base.at("/semantics/tags")
            val mergedSemantics = (merged.get("semantics")?.deepCopy() as? ObjectNode)
                ?: objectMapper.createObjectNode()
            val tagArray = objectMapper.createArrayNode()
            if (baseTags.isArray) {
                baseTags.forEach { tagArray.add(it) }
            }
            extendTags.forEach { tagArray.add(it) }
            mergedSemantics.set<JsonNode>("tags", tagArray)
            merged.set<JsonNode>("semantics", mergedSemantics)
        }

        return merged
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
                definition = semanticsNode.get("definition")?.asText(),
                tags = semanticsNode.get("tags")?.map { it.asText() } ?: emptyList()
            )
        } else null

        return ResolvedEntityType(
            key = json.get("key")?.asText() ?: "",
            displayNameSingular = displayName?.get("singular")?.asText() ?: "",
            displayNamePlural = displayName?.get("plural")?.asText() ?: "",
            iconType = icon?.get("type")?.asText() ?: "BOX",
            iconColour = icon?.get("colour")?.asText() ?: "NEUTRAL",
            semanticGroup = json.get("semanticGroup")?.asText() ?: "UNCATEGORIZED",
            lifecycleDomain = json.get("lifecycleDomain")?.asText(),
            identifierKey = json.get("identifierKey")?.asText(),
            readonly = json.get("readonly")?.asBoolean() ?: readonlyDefault,
            schema = attributesMap,
            columns = null,
            semantics = semantics
        )
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
            logger.warn { "Relationship '${rel.get("key")?.asText()}' has both shorthand and full format — skipping" }
            return null
        }

        if (!hasShorthand && !hasFullFormat) {
            logger.warn { "Relationship '${rel.get("key")?.asText()}' has neither shorthand nor full format — skipping" }
            return null
        }

        val protectedDefault = manifestType == ManifestType.INTEGRATION
        val isProtected = rel.get("protected")?.asBoolean() ?: protectedDefault

        val icon = rel.get("icon")
        val semanticsNode = rel.get("semantics")
        val semantics = if (semanticsNode != null) {
            ResolvedRelationshipSemantics(
                definition = semanticsNode.get("definition")?.asText(),
                tags = semanticsNode.get("tags")?.map { it.asText() } ?: emptyList()
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
        val key = rel.get("key")?.asText()
        val targetKey = rel.get("targetEntityTypeKey")?.asText()
        if (targetKey == null) {
            logger.warn { "Relationship '$key' shorthand format missing targetEntityTypeKey" }
            return null
        }
        val cardinalityStr = rel.get("cardinality")?.asText()
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
            key = rel.get("key").asText(),
            sourceEntityTypeKey = rel.get("sourceEntityTypeKey").asText(),
            name = rel.get("name").asText(),
            iconType = icon?.get("type")?.asText() ?: "LINK",
            iconColour = icon?.get("colour")?.asText() ?: "NEUTRAL",
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
        val targetRules = targetRulesArray.map { rule ->
            val cardinalityOverrideStr = rule.get("cardinalityOverride")?.asText()
            NormalizedTargetRule(
                targetEntityTypeKey = rule.get("targetEntityTypeKey").asText(),
                cardinalityOverride = cardinalityOverrideStr?.let {
                    try { EntityRelationshipCardinality.valueOf(it) } catch (_: IllegalArgumentException) { null }
                },
                inverseVisible = rule.get("inverseVisible")?.asBoolean(),
                inverseName = rule.get("inverseName")?.asText()
            )
        }

        // For full format without explicit cardinality, default to ONE_TO_MANY
        val cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY

        return NormalizedRelationship(
            key = rel.get("key").asText(),
            sourceEntityTypeKey = rel.get("sourceEntityTypeKey").asText(),
            name = rel.get("name").asText(),
            iconType = icon?.get("type")?.asText() ?: "LINK",
            iconColour = icon?.get("colour")?.asText() ?: "NEUTRAL",
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
            val entityTypeKey = mappingEntry.get("entityTypeKey")?.asText() ?: continue
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

    // ------ Helpers ------

    private fun buildStaleManifest(scanned: ScannedManifest): ResolvedManifest {
        return ResolvedManifest(
            key = scanned.key,
            name = scanned.json.get("name")?.asText() ?: scanned.key,
            description = scanned.json.get("description")?.asText(),
            type = scanned.type,
            manifestVersion = scanned.json.get("manifestVersion")?.asText(),
            entityTypes = emptyList(),
            relationships = emptyList(),
            fieldMappings = emptyList(),
            stale = true
        )
    }
}
