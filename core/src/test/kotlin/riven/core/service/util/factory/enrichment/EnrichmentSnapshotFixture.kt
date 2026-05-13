package cranium.core.service.util.factory.enrichment

import cranium.core.entity.entity.EntityTypeSemanticMetadataEntity
import cranium.core.entity.entity.RelationshipDefinitionEntity
import cranium.core.entity.entity.RelationshipTargetRuleEntity
import cranium.core.entity.identity.IdentityClusterMemberEntity
import cranium.core.entity.workflow.ExecutionQueueEntity
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.connotation.ConnotationStatus
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.enums.entity.semantics.SemanticMetadataTargetType
import cranium.core.enums.integration.SourceType
import cranium.core.enums.workflow.ExecutionJobType
import cranium.core.enums.workflow.ExecutionQueueStatus
import cranium.core.models.catalog.ConnotationSignals
import cranium.core.models.catalog.ScaleMappingType
import cranium.core.models.catalog.SentimentScale
import cranium.core.models.connotation.AnalysisTier
import cranium.core.models.connotation.SentimentMetadata
import cranium.core.models.entity.payload.EntityAttributePrimitivePayload
import cranium.core.service.util.factory.WorkspaceFactory
import cranium.core.service.util.factory.entity.EntityFactory
import cranium.core.service.util.factory.identity.IdentityFactory
import cranium.core.service.util.factory.workflow.ExecutionQueueFactory
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Deterministic in-memory fixture for [cranium.core.service.enrichment.EnrichmentService.analyzeSemantics] snapshot tests.
 *
 * All IDs are FIXED so the produced JSON is reproducible across runs. This fixture exercises:
 * - A primary entity with ≥ 2 attributes, including one RELATIONAL_REFERENCE (resolves to a real identifier).
 * - ≥ 1 relationship summary with topCategories populated (target type has a CATEGORICAL attribute) and latestActivityAt set.
 * - ≥ 1 cluster member of a different entity type name.
 * - ≥ 1 relationshipDefinitions entry (RELATIONSHIP-targetType metadata).
 * - A non-null sentiment with status = ANALYZED (workspace.connotationEnabled = true, manifest signals present,
 *   attributeKeyMapping resolves the sentiment attribute, ConnotationAnalysisService stub returns ANALYZED).
 *
 * Use [build] to obtain a [SnapshotFixture] bundle; each field is accessible by name so tests can wire mocks.
 */
object EnrichmentSnapshotFixture {

    // ---- Fixed UUIDs (stable across test runs) ----
    val QUEUE_ITEM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val WORKSPACE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val ENTITY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    val ENTITY_TYPE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000004")

    // Attribute UUIDs
    val ATTR_NAME_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
    val ATTR_SENTIMENT_SOURCE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011")
    val ATTR_RELATIONAL_REF_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000012")

    // Referenced entity (target of RELATIONAL_REFERENCE)
    val REF_ENTITY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000020")
    val REF_ENTITY_TYPE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000021")
    val REF_ENTITY_IDENTIFIER_ATTR_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000022")

    // Relationship definition
    val REL_DEFINITION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000030")
    val REL_TARGET_RULE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000031")
    val REL_TARGET_TYPE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000032")

    // Related entity (target of relationship, has CATEGORICAL attribute)
    val RELATED_ENTITY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000040")
    val CATEGORICAL_ATTR_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000041")

    // Cluster
    val CLUSTER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000050")
    val CLUSTER_MEMBER_ENTITY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000051")
    val CLUSTER_MEMBER_TYPE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000052")

    // Semantic metadata
    val META_ENTITY_TYPE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000060")  // entity-level metadata
    val META_NAME_ATTR_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000061")    // IDENTIFIER attr meta
    val META_SENTIMENT_ATTR_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000062") // SENTIMENT_SOURCE attr meta
    val META_REL_REF_ATTR_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000063") // RELATIONAL_REFERENCE attr meta
    val META_RELATIONSHIP_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000064") // RELATIONSHIP metadata
    val META_REF_TYPE_IDENTIFIER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000065") // IDENTIFIER on ref type

    // Manifest sentiment key (must match attributeKeyMapping)
    const val SENTIMENT_MANIFEST_KEY = "nps_score"

    /**
     * Builds the complete fixture bundle. All returned values are pre-constructed with FIXED UUIDs.
     */
    fun build(): SnapshotFixture {
        // Workspace with connotationEnabled = true
        val workspace = WorkspaceFactory.createWorkspace(
            id = WORKSPACE_ID,
            name = "Snapshot Workspace",
            connotationEnabled = true,
        )

        // Primary entity (the entity being enriched)
        val entity = EntityFactory.createEntityEntity(
            id = ENTITY_ID,
            workspaceId = WORKSPACE_ID,
            typeId = ENTITY_TYPE_ID,
            typeKey = "customer",
            sourceType = SourceType.USER_CREATED,
        )

        // Primary entity type with attributeKeyMapping so sentiment short-circuit doesn't fire
        val entityType = EntityFactory.createEntityType(
            id = ENTITY_TYPE_ID,
            key = "customer",
            displayNameSingular = "Customer",
            displayNamePlural = "Customers",
            workspaceId = WORKSPACE_ID,
            version = 3,
            semanticGroup = SemanticGroup.CUSTOMER,
            lifecycleDomain = LifecycleDomain.ACQUISITION,
            attributeKeyMapping = mapOf(SENTIMENT_MANIFEST_KEY to ATTR_SENTIMENT_SOURCE_ID.toString()),
        )

        // Execution queue item
        val queueItem = ExecutionQueueFactory.createEnrichmentJob(
            id = QUEUE_ITEM_ID,
            workspaceId = WORKSPACE_ID,
            entityId = ENTITY_ID,
            status = ExecutionQueueStatus.PENDING,
        )

        // Semantic metadata for primary entity type:
        // 1. ENTITY_TYPE metadata (provides entityTypeDefinition)
        val metaEntityType = IdentityFactory.createEntityTypeSemanticMetadataEntity(
            id = META_ENTITY_TYPE_ID,
            workspaceId = WORKSPACE_ID,
            entityTypeId = ENTITY_TYPE_ID,
            targetType = SemanticMetadataTargetType.ENTITY_TYPE,
            targetId = ENTITY_TYPE_ID,
            classification = null,
            definition = "A person or company that has purchased or inquired about services.",
        )

        // 2. ATTRIBUTE metadata: IDENTIFIER (name)
        val metaNameAttr = IdentityFactory.createEntityTypeSemanticMetadataEntity(
            id = META_NAME_ATTR_ID,
            workspaceId = WORKSPACE_ID,
            entityTypeId = ENTITY_TYPE_ID,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = ATTR_NAME_ID,
            classification = SemanticAttributeClassification.IDENTIFIER,
            definition = "Customer Name",
        )

        // 3. ATTRIBUTE metadata: SENTIMENT_SOURCE (nps score used as source)
        val metaSentimentAttr = IdentityFactory.createEntityTypeSemanticMetadataEntity(
            id = META_SENTIMENT_ATTR_ID,
            workspaceId = WORKSPACE_ID,
            entityTypeId = ENTITY_TYPE_ID,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = ATTR_SENTIMENT_SOURCE_ID,
            classification = SemanticAttributeClassification.CONNOTATION_SOURCE,
            definition = "NPS Score",
        )

        // 4. ATTRIBUTE metadata: RELATIONAL_REFERENCE
        val metaRelRefAttr = IdentityFactory.createEntityTypeSemanticMetadataEntity(
            id = META_REL_REF_ATTR_ID,
            workspaceId = WORKSPACE_ID,
            entityTypeId = ENTITY_TYPE_ID,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = ATTR_RELATIONAL_REF_ID,
            classification = SemanticAttributeClassification.RELATIONAL_REFERENCE,
            definition = "Account Manager",
        )

        // 5. RELATIONSHIP metadata (for loadRelationshipDefinitions)
        val metaRelationship = IdentityFactory.createEntityTypeSemanticMetadataEntity(
            id = META_RELATIONSHIP_ID,
            workspaceId = WORKSPACE_ID,
            entityTypeId = ENTITY_TYPE_ID,
            targetType = SemanticMetadataTargetType.RELATIONSHIP,
            targetId = REL_DEFINITION_ID,
            classification = null,
            definition = "Support tickets raised by this customer, indicating activity and engagement level.",
        )

        val allPrimaryMetadata = listOf(metaEntityType, metaNameAttr, metaSentimentAttr, metaRelRefAttr, metaRelationship)

        // Attribute payloads for primary entity
        val entityAttributes = mapOf(
            ATTR_NAME_ID to EntityAttributePrimitivePayload(
                schemaType = SchemaType.TEXT,
                value = "Acme Corporation",
            ),
            ATTR_SENTIMENT_SOURCE_ID to EntityAttributePrimitivePayload(
                schemaType = SchemaType.NUMBER,
                value = 72,
            ),
            ATTR_RELATIONAL_REF_ID to EntityAttributePrimitivePayload(
                schemaType = SchemaType.TEXT,
                value = REF_ENTITY_ID.toString(),
            ),
        )

        // Referenced entity (target of RELATIONAL_REFERENCE attribute)
        val referencedEntity = EntityFactory.createEntityEntity(
            id = REF_ENTITY_ID,
            workspaceId = WORKSPACE_ID,
            typeId = REF_ENTITY_TYPE_ID,
            typeKey = "account_manager",
            sourceType = SourceType.USER_CREATED,
        )
        val referencedEntityType = EntityFactory.createEntityType(
            id = REF_ENTITY_TYPE_ID,
            key = "account_manager",
            displayNameSingular = "Account Manager",
            workspaceId = WORKSPACE_ID,
            version = 1,
        )

        // Semantic metadata for referenced entity type (IDENTIFIER attr so resolution works)
        val metaRefTypeIdentifier = IdentityFactory.createEntityTypeSemanticMetadataEntity(
            id = META_REF_TYPE_IDENTIFIER_ID,
            workspaceId = WORKSPACE_ID,
            entityTypeId = REF_ENTITY_TYPE_ID,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = REF_ENTITY_IDENTIFIER_ATTR_ID,
            classification = SemanticAttributeClassification.IDENTIFIER,
            definition = "Manager Name",
        )

        // Attribute payloads for referenced entity (the IDENTIFIER value)
        val referencedEntityAttributes = mapOf(
            REF_ENTITY_IDENTIFIER_ATTR_ID to EntityAttributePrimitivePayload(
                schemaType = SchemaType.TEXT,
                value = "Jane Smith",
            ),
        )

        // Relationship definition
        val relationshipDefinition = EntityFactory.createRelationshipDefinitionEntity(
            id = REL_DEFINITION_ID,
            workspaceId = WORKSPACE_ID,
            sourceEntityTypeId = ENTITY_TYPE_ID,
            name = "Support Tickets",
        )

        // Relationship target rule (points to REL_TARGET_TYPE_ID so topCategories are loaded)
        val targetRule = EntityFactory.createTargetRuleEntity(
            id = REL_TARGET_RULE_ID,
            relationshipDefinitionId = REL_DEFINITION_ID,
            targetEntityTypeId = REL_TARGET_TYPE_ID,
        )

        // The relationship instance (linking primary entity to RELATED_ENTITY_ID)
        val relationshipCreatedAt = ZonedDateTime.parse("2025-03-15T10:30:00Z")
        val relationshipEntity = EntityFactory.createRelationshipEntity(
            workspaceId = WORKSPACE_ID,
            sourceId = ENTITY_ID,
            targetId = RELATED_ENTITY_ID,
            definitionId = REL_DEFINITION_ID,
            createdAt = relationshipCreatedAt,
        )

        // Related entity (target of relationship, has CATEGORICAL attribute)
        val relatedEntity = EntityFactory.createEntityEntity(
            id = RELATED_ENTITY_ID,
            workspaceId = WORKSPACE_ID,
            typeId = REL_TARGET_TYPE_ID,
            typeKey = "support_ticket",
            sourceType = SourceType.USER_CREATED,
        )

        // Semantic metadata for relationship target type (CATEGORICAL attr for topCategories)
        val metaCategoricalAttr = IdentityFactory.createEntityTypeSemanticMetadataEntity(
            workspaceId = WORKSPACE_ID,
            entityTypeId = REL_TARGET_TYPE_ID,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = CATEGORICAL_ATTR_ID,
            classification = SemanticAttributeClassification.CATEGORICAL,
            definition = "Priority",
        )

        // Attribute payloads for related entity (has CATEGORICAL value)
        val relatedEntityAttributes = mapOf(
            CATEGORICAL_ATTR_ID to EntityAttributePrimitivePayload(
                schemaType = SchemaType.SELECT,
                value = "High",
            ),
        )

        // Cluster membership
        val primaryClusterMember = IdentityFactory.createIdentityClusterMemberEntity(
            clusterId = CLUSTER_ID,
            entityId = ENTITY_ID,
        )
        val secondaryClusterMember = IdentityFactory.createIdentityClusterMemberEntity(
            clusterId = CLUSTER_ID,
            entityId = CLUSTER_MEMBER_ENTITY_ID,
        )
        val allClusterMembers = listOf(primaryClusterMember, secondaryClusterMember)

        // Cluster member entity (different entityTypeName from primary)
        val clusterMemberEntity = EntityFactory.createEntityEntity(
            id = CLUSTER_MEMBER_ENTITY_ID,
            workspaceId = WORKSPACE_ID,
            typeId = CLUSTER_MEMBER_TYPE_ID,
            typeKey = "company",
            sourceType = SourceType.INTEGRATION,
        )
        val clusterMemberEntityType = EntityFactory.createEntityType(
            id = CLUSTER_MEMBER_TYPE_ID,
            key = "company",
            displayNameSingular = "Company",
            workspaceId = WORKSPACE_ID,
            version = 1,
        )

        // Connotation signals (DETERMINISTIC tier matching the sentiment attribute manifest key)
        val connotationSignals = ConnotationSignals(
            tier = AnalysisTier.DETERMINISTIC,
            sentimentAttribute = SENTIMENT_MANIFEST_KEY,
            sentimentScale = SentimentScale(
                sourceMin = 0.0,
                sourceMax = 100.0,
                targetMin = -1.0,
                targetMax = 1.0,
                mappingType = ScaleMappingType.LINEAR,
            ),
            themeAttributes = emptyList(),
        )

        // Pre-built ANALYZED sentiment (stub returns this directly)
        val analyzedSentiment = SentimentMetadata(
            sentiment = 0.44,
            sentimentLabel = null,
            themes = emptyList(),
            analysisVersion = "v1",
            analysisModel = "deterministic-linear",
            analysisTier = AnalysisTier.DETERMINISTIC,
            status = ConnotationStatus.ANALYZED,
            analyzedAt = ZonedDateTime.parse("2025-03-15T10:30:00Z"),
        )

        return SnapshotFixture(
            queueItemId = QUEUE_ITEM_ID,
            workspaceId = WORKSPACE_ID,
            entityId = ENTITY_ID,
            entityTypeId = ENTITY_TYPE_ID,
            workspace = workspace,
            entity = entity,
            entityType = entityType,
            queueItem = queueItem,
            allPrimaryMetadata = allPrimaryMetadata,
            entityAttributes = entityAttributes,
            referencedEntity = referencedEntity,
            referencedEntityType = referencedEntityType,
            metaRefTypeIdentifier = metaRefTypeIdentifier,
            referencedEntityAttributes = referencedEntityAttributes,
            relationshipDefinition = relationshipDefinition,
            targetRule = targetRule,
            relationshipEntity = relationshipEntity,
            relatedEntity = relatedEntity,
            metaCategoricalAttr = metaCategoricalAttr,
            relatedEntityAttributes = relatedEntityAttributes,
            primaryClusterMember = primaryClusterMember,
            allClusterMembers = allClusterMembers,
            clusterMemberEntity = clusterMemberEntity,
            clusterMemberEntityType = clusterMemberEntityType,
            connotationSignals = connotationSignals,
            analyzedSentiment = analyzedSentiment,
        )
    }

    /**
     * Bundle of all entities and payloads needed to wire mocks for [EnrichmentContextSnapshotTest].
     */
    data class SnapshotFixture(
        val queueItemId: UUID,
        val workspaceId: UUID,
        val entityId: UUID,
        val entityTypeId: UUID,

        val workspace: cranium.core.entity.workspace.WorkspaceEntity,
        val entity: cranium.core.entity.entity.EntityEntity,
        val entityType: cranium.core.entity.entity.EntityTypeEntity,
        val queueItem: ExecutionQueueEntity,

        val allPrimaryMetadata: List<EntityTypeSemanticMetadataEntity>,
        val entityAttributes: Map<UUID, cranium.core.models.entity.payload.EntityAttributePrimitivePayload>,

        val referencedEntity: cranium.core.entity.entity.EntityEntity,
        val referencedEntityType: cranium.core.entity.entity.EntityTypeEntity,
        val metaRefTypeIdentifier: EntityTypeSemanticMetadataEntity,
        val referencedEntityAttributes: Map<UUID, cranium.core.models.entity.payload.EntityAttributePrimitivePayload>,

        val relationshipDefinition: RelationshipDefinitionEntity,
        val targetRule: RelationshipTargetRuleEntity,
        val relationshipEntity: cranium.core.entity.entity.EntityRelationshipEntity,

        val relatedEntity: cranium.core.entity.entity.EntityEntity,
        val metaCategoricalAttr: EntityTypeSemanticMetadataEntity,
        val relatedEntityAttributes: Map<UUID, cranium.core.models.entity.payload.EntityAttributePrimitivePayload>,

        val primaryClusterMember: IdentityClusterMemberEntity,
        val allClusterMembers: List<IdentityClusterMemberEntity>,
        val clusterMemberEntity: cranium.core.entity.entity.EntityEntity,
        val clusterMemberEntityType: cranium.core.entity.entity.EntityTypeEntity,

        val connotationSignals: ConnotationSignals,
        val analyzedSentiment: SentimentMetadata,
    )
}
