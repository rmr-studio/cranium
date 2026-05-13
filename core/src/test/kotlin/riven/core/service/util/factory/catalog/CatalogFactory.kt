package cranium.core.service.util.factory.catalog

import cranium.core.entity.catalog.*
import cranium.core.enums.catalog.ManifestType
import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.enums.entity.semantics.SemanticMetadataTargetType
import cranium.core.models.catalog.ConnotationSignals
import cranium.core.models.catalog.ScaleMappingType
import cranium.core.models.catalog.SentimentScale
import cranium.core.models.connotation.AnalysisTier
import java.util.*

object CatalogFactory {

    fun createManifestEntity(
        type: ManifestType,
        id: UUID? = UUID.randomUUID(),
        key: String = "test-manifest",
        name: String = "Test Manifest",
        description: String = "A test manifest",
        manifestVersion: String = "1.0.0",
        stale: Boolean = false,
        templateKeys: List<String>? = null
    ) = ManifestCatalogEntity(
        id = id,
        key = key,
        name = name,
        description = description,
        manifestType = type,
        manifestVersion = manifestVersion,
        stale = stale,
        templateKeys = templateKeys
    )

    fun createEntityTypeEntity(
        manifestId: UUID,
        id: UUID? = UUID.randomUUID(),
        key: String = "test-entity-type",
        displayNameSingular: String = "Test Entity",
        displayNamePlural: String = "Test Entities",
        iconType: IconType = IconType.CIRCLE_DASHED,
        iconColour: IconColour = IconColour.NEUTRAL,
        semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,
        lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,
        identifierKey: String? = null,
        readonly: Boolean = false,
        schema: Map<String, Any> = mapOf("type" to "object"),
        columns: List<Map<String, Any>> = listOf(mapOf("key" to "name", "label" to "Name")),
        connotationSignals: ConnotationSignals? = null,
        schemaHash: String? = null
    ) = CatalogEntityTypeEntity(
        id = id,
        manifestId = manifestId,
        key = key,
        displayNameSingular = displayNameSingular,
        displayNamePlural = displayNamePlural,
        iconType = iconType,
        iconColour = iconColour,
        semanticGroup = semanticGroup,
        lifecycleDomain = lifecycleDomain,
        identifierKey = identifierKey,
        readonly = readonly,
        schema = schema,
        columns = columns,
        connotationSignals = connotationSignals,
        schemaHash = schemaHash
    )

    /**
     * Convenience helper for tests that need a CatalogEntityTypeEntity with
     * a specific (or null) connotation signals payload.
     */
    fun catalogEntityTypeEntityWithSignals(
        signals: ConnotationSignals?,
        manifestId: UUID = UUID.randomUUID(),
    ): CatalogEntityTypeEntity = createEntityTypeEntity(
        manifestId = manifestId,
        connotationSignals = signals,
    )

    /** Realistic default ConnotationSignals payload for tests. */
    fun connotationSignals(
        tier: AnalysisTier = AnalysisTier.DETERMINISTIC,
        sentimentAttribute: String = "rating",
        sentimentScale: SentimentScale = sentimentScale(),
        themeAttributes: List<String> = listOf("review_text"),
    ) = ConnotationSignals(
        tier = tier,
        sentimentAttribute = sentimentAttribute,
        sentimentScale = sentimentScale,
        themeAttributes = themeAttributes,
    )

    fun sentimentScale(
        sourceMin: Double = 1.0,
        sourceMax: Double = 5.0,
        targetMin: Double = -1.0,
        targetMax: Double = 1.0,
        mappingType: ScaleMappingType = ScaleMappingType.LINEAR,
    ) = SentimentScale(
        sourceMin = sourceMin,
        sourceMax = sourceMax,
        targetMin = targetMin,
        targetMax = targetMax,
        mappingType = mappingType,
    )

    fun createRelationshipEntity(
        manifestId: UUID,
        id: UUID = UUID.randomUUID(),
        key: String = "test-relationship",
        sourceEntityTypeKey: String = "test-entity-type",
        name: String = "Test Relationship",
        cardinalityDefault: EntityRelationshipCardinality = EntityRelationshipCardinality.ONE_TO_MANY
    ) = CatalogRelationshipEntity(
        id = id,
        manifestId = manifestId,
        key = key,
        sourceEntityTypeKey = sourceEntityTypeKey,
        name = name,
        cardinalityDefault = cardinalityDefault
    )

    fun createTargetRuleEntity(
        catalogRelationshipId: UUID,
        id: UUID = UUID.randomUUID(),
        targetEntityTypeKey: String = "target-entity-type",
        cardinalityOverride: EntityRelationshipCardinality = EntityRelationshipCardinality.ONE_TO_ONE,
        inverseVisible: Boolean = true,
        inverseName: String = "reverse-test"
    ) = CatalogRelationshipTargetRuleEntity(
        id = id,
        catalogRelationshipId = catalogRelationshipId,
        targetEntityTypeKey = targetEntityTypeKey,
        cardinalityOverride = cardinalityOverride,
        inverseVisible = inverseVisible,
        inverseName = inverseName
    )

    fun createSemanticMetadataEntity(
        catalogEntityTypeId: UUID,
        id: UUID = UUID.randomUUID(),
        targetType: SemanticMetadataTargetType = SemanticMetadataTargetType.ENTITY_TYPE,
        targetId: String = "test-entity-type",
        definition: String = "A test entity type",
        classification: SemanticAttributeClassification = SemanticAttributeClassification.IDENTIFIER,
        tags: List<String> = listOf("test", "core")
    ) = CatalogSemanticMetadataEntity(
        id = id,
        catalogEntityTypeId = catalogEntityTypeId,
        targetType = targetType,
        targetId = targetId,
        definition = definition,
        classification = classification,
        tags = tags
    )

    fun createFieldMappingEntity(
        manifestId: UUID,
        id: UUID = UUID.randomUUID(),
        entityTypeKey: String = "test-entity-type",
        nangoModel: String? = null,
        mappings: Map<String, Any> = mapOf("externalField" to "internalField")
    ) = CatalogFieldMappingEntity(
        id = id,
        manifestId = manifestId,
        entityTypeKey = entityTypeKey,
        nangoModel = nangoModel,
        mappings = mappings
    )
}
