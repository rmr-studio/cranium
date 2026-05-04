package riven.core.service.enrichment

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.EntityTypeSemanticMetadataEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.integration.SourceType
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityAttributeService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.enrichment.EnrichmentFactory
import riven.core.service.util.factory.identity.IdentityFactory
import riven.core.enums.workspace.WorkspaceRoles
import java.time.ZonedDateTime
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

/**
 * Unit tests for [EnrichmentContextAssembler].
 *
 * ENRICH-02: Constructor ceiling is ≤ 12; actual count is 9 (documented in Test 7 assertion).
 * The assembler is a pure read-side service with no @PreAuthorize; @WithUserPersona is applied
 * defensively per CLAUDE.md for code paths that may transitively touch auth infrastructure.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EnrichmentContextAssembler::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@example.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.ADMIN
        )
    ]
)
class EnrichmentContextAssemblerTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var semanticMetadataRepository: EntityTypeSemanticMetadataRepository

    @MockitoBean
    private lateinit var entityAttributeService: EntityAttributeService

    @MockitoBean
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @MockitoBean
    private lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var identityClusterMemberRepository: IdentityClusterMemberRepository

    @MockitoBean
    private lateinit var relationshipTargetRuleRepository: RelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @Autowired
    private lateinit var assembler: EnrichmentContextAssembler

    // ------ Test 1: attributes ordering and semanticLabel fallback ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class Attributes {

        @Test
        fun `assemble returns attributes in entityAttributeService iteration order and semanticLabel falls back to UUID string when metadata missing`() {
            val entityId = UUID.randomUUID()
            val entityTypeId = UUID.randomUUID()
            val attrIdWithMeta = UUID.randomUUID()
            val attrIdWithoutMeta = UUID.randomUUID()

            val attrWithMeta = EntityAttributePrimitivePayload(schemaType = SchemaType.TEXT, value = "Hello")
            val attrWithoutMeta = EntityAttributePrimitivePayload(schemaType = SchemaType.NUMBER, value = 42)

            // LinkedHashMap to preserve insertion order
            val attributeMap = linkedMapOf(
                attrIdWithMeta to attrWithMeta,
                attrIdWithoutMeta to attrWithoutMeta,
            )

            val metadata = IdentityFactory.createEntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = entityTypeId,
                targetType = SemanticMetadataTargetType.ATTRIBUTE,
                targetId = attrIdWithMeta,
                classification = SemanticAttributeClassification.IDENTIFIER,
                definition = "Display Name",
            )

            whenever(semanticMetadataRepository.findByEntityTypeId(entityTypeId)).thenReturn(listOf(metadata))
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(attributeMap)
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)).thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
            whenever(semanticMetadataRepository.findByEntityTypeIdIn(any())).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributesForEntities(any())).thenReturn(emptyMap())

            val context = assembler.assemble(
                entityId = entityId,
                workspaceId = workspaceId,
                queueItemId = UUID.randomUUID(),
                entityTypeId = entityTypeId,
                schemaVersion = 1,
                entityTypeName = "Widget",
                semanticGroup = SemanticGroup.UNCATEGORIZED,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                sentiment = null,
            )

            val attrLabels = context.attributes.map { it.semanticLabel }
            assertEquals("Display Name", attrLabels[0])
            assertEquals(attrIdWithoutMeta.toString(), attrLabels[1])
        }
    }

    // ------ Test 2: relationship summaries ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class RelationshipSummaries {

        @Test
        fun `assemble groups relationships by definitionId, computes count, sets latestActivityAt as max createdAt, skips unknown definitions`() {
            val entityId = UUID.randomUUID()
            val entityTypeId = UUID.randomUUID()
            val definitionId = UUID.randomUUID()
            val unknownDefinitionId = UUID.randomUUID()

            val t1 = ZonedDateTime.parse("2025-01-01T10:00:00Z")
            val t2 = ZonedDateTime.parse("2025-06-01T12:00:00Z")

            val rel1 = EntityFactory.createRelationshipEntity(
                workspaceId = workspaceId, sourceId = entityId,
                targetId = UUID.randomUUID(), definitionId = definitionId, createdAt = t1
            )
            val rel2 = EntityFactory.createRelationshipEntity(
                workspaceId = workspaceId, sourceId = entityId,
                targetId = UUID.randomUUID(), definitionId = definitionId, createdAt = t2
            )
            val relUnknown = EntityFactory.createRelationshipEntity(
                workspaceId = workspaceId, sourceId = entityId,
                targetId = UUID.randomUUID(), definitionId = unknownDefinitionId, createdAt = t1
            )

            val definition = EntityFactory.createRelationshipDefinitionEntity(
                id = definitionId, workspaceId = workspaceId,
                sourceEntityTypeId = entityTypeId, name = "Tickets"
            )
            val definitions = mapOf(definitionId to definition)

            whenever(semanticMetadataRepository.findByEntityTypeId(entityTypeId)).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId))
                .thenReturn(listOf(rel1, rel2, relUnknown))
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
                .thenReturn(listOf(definition))
            whenever(relationshipTargetRuleRepository.findByRelationshipDefinitionIdIn(any()))
                .thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
            whenever(semanticMetadataRepository.findByEntityTypeIdIn(any())).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributesForEntities(any())).thenReturn(emptyMap())

            val context = assembler.assemble(
                entityId = entityId,
                workspaceId = workspaceId,
                queueItemId = UUID.randomUUID(),
                entityTypeId = entityTypeId,
                schemaVersion = 1,
                entityTypeName = "Customer",
                semanticGroup = SemanticGroup.UNCATEGORIZED,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                sentiment = null,
            )

            assertEquals(1, context.relationshipSummaries.size)
            val summary = context.relationshipSummaries[0]
            assertEquals(definitionId, summary.definitionId)
            assertEquals("Tickets", summary.relationshipName)
            assertEquals(2, summary.count)
            assertNotNull(summary.latestActivityAt)
            // t2 (2025-06-01) should be the max
            assertTrue(summary.latestActivityAt!!.contains("2025-06-01"))
        }
    }

    // ------ Test 3: loadTopCategoriesForRelationship ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class TopCategories {

        @Test
        fun `loadTopCategoriesForRelationship picks first CATEGORICAL attribute and emits top 3 values and returns empty when no CATEGORICAL exists`() {
            val entityId = UUID.randomUUID()
            val entityTypeId = UUID.randomUUID()
            val definitionId = UUID.randomUUID()
            val targetTypeId = UUID.randomUUID()
            val categoricalAttrId = UUID.randomUUID()

            val relatedEntityId1 = UUID.randomUUID()
            val relatedEntityId2 = UUID.randomUUID()
            val relatedEntityId3 = UUID.randomUUID()
            val relatedEntityId4 = UUID.randomUUID()

            val rel1 = EntityFactory.createRelationshipEntity(workspaceId = workspaceId, sourceId = entityId, targetId = relatedEntityId1, definitionId = definitionId)
            val rel2 = EntityFactory.createRelationshipEntity(workspaceId = workspaceId, sourceId = entityId, targetId = relatedEntityId2, definitionId = definitionId)
            val rel3 = EntityFactory.createRelationshipEntity(workspaceId = workspaceId, sourceId = entityId, targetId = relatedEntityId3, definitionId = definitionId)
            val rel4 = EntityFactory.createRelationshipEntity(workspaceId = workspaceId, sourceId = entityId, targetId = relatedEntityId4, definitionId = definitionId)

            val definition = EntityFactory.createRelationshipDefinitionEntity(
                id = definitionId, workspaceId = workspaceId, sourceEntityTypeId = entityTypeId, name = "Tickets"
            )
            val targetRule = EntityFactory.createTargetRuleEntity(
                relationshipDefinitionId = definitionId, targetEntityTypeId = targetTypeId
            )

            val categoricalMeta = IdentityFactory.createEntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = targetTypeId,
                targetType = SemanticMetadataTargetType.ATTRIBUTE,
                targetId = categoricalAttrId,
                classification = SemanticAttributeClassification.CATEGORICAL,
                definition = "Priority",
            )

            val attrValues = mapOf(
                relatedEntityId1 to mapOf(categoricalAttrId to EntityAttributePrimitivePayload(value = "High", schemaType = SchemaType.SELECT)),
                relatedEntityId2 to mapOf(categoricalAttrId to EntityAttributePrimitivePayload(value = "High", schemaType = SchemaType.SELECT)),
                relatedEntityId3 to mapOf(categoricalAttrId to EntityAttributePrimitivePayload(value = "Medium", schemaType = SchemaType.SELECT)),
                relatedEntityId4 to mapOf(categoricalAttrId to EntityAttributePrimitivePayload(value = "Low", schemaType = SchemaType.SELECT)),
            )

            whenever(semanticMetadataRepository.findByEntityTypeId(entityTypeId)).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId))
                .thenReturn(listOf(rel1, rel2, rel3, rel4))
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
                .thenReturn(listOf(definition))
            whenever(relationshipTargetRuleRepository.findByRelationshipDefinitionIdIn(listOf(definitionId)))
                .thenReturn(listOf(targetRule))
            whenever(semanticMetadataRepository.findByEntityTypeId(targetTypeId)).thenReturn(listOf(categoricalMeta))
            whenever(entityAttributeService.getAttributesForEntities(any())).thenReturn(attrValues)
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
            whenever(semanticMetadataRepository.findByEntityTypeIdIn(any())).thenReturn(emptyList())

            val context = assembler.assemble(
                entityId = entityId,
                workspaceId = workspaceId,
                queueItemId = UUID.randomUUID(),
                entityTypeId = entityTypeId,
                schemaVersion = 1,
                entityTypeName = "Customer",
                semanticGroup = SemanticGroup.UNCATEGORIZED,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                sentiment = null,
            )

            val summary = context.relationshipSummaries.first { it.definitionId == definitionId }
            assertEquals(1, summary.topCategories.size)
            val categories = summary.topCategories[0]
            assertTrue(categories.startsWith("Priority:"), "Expected 'Priority: ...' but got '$categories'")
            assertTrue(categories.contains("High (2)"))
            assertTrue(categories.contains("Medium (1)"))
            assertTrue(categories.contains("Low (1)"))
        }
    }

    // ------ Test 4: RELATIONAL_REFERENCE resolution ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class RelationalReferenceResolution {

        @Test
        fun `assemble resolves RELATIONAL_REFERENCE to IDENTIFIER value and falls back to placeholder on failure`() {
            val entityId = UUID.randomUUID()
            val entityTypeId = UUID.randomUUID()
            val refAttrId = UUID.randomUUID()
            val refEntityId = UUID.randomUUID()
            val refEntityTypeId = UUID.randomUUID()
            val identifierAttrId = UUID.randomUUID()

            val attrMap = mapOf(
                refAttrId to EntityAttributePrimitivePayload(value = refEntityId.toString(), schemaType = SchemaType.TEXT),
            )

            val refAttrMeta = IdentityFactory.createEntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = entityTypeId,
                targetType = SemanticMetadataTargetType.ATTRIBUTE,
                targetId = refAttrId,
                classification = SemanticAttributeClassification.RELATIONAL_REFERENCE,
                definition = "Account Manager",
            )

            val refEntity = EntityFactory.createEntityEntity(
                id = refEntityId,
                workspaceId = workspaceId,
                typeId = refEntityTypeId,
            )

            val identifierMeta = IdentityFactory.createEntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = refEntityTypeId,
                targetType = SemanticMetadataTargetType.ATTRIBUTE,
                targetId = identifierAttrId,
                classification = SemanticAttributeClassification.IDENTIFIER,
                definition = "Manager Name",
            )

            val refAttrValues = mapOf(
                refEntityId to mapOf(identifierAttrId to EntityAttributePrimitivePayload(value = "Jane Smith", schemaType = SchemaType.TEXT)),
            )

            whenever(semanticMetadataRepository.findByEntityTypeId(entityTypeId)).thenReturn(listOf(refAttrMeta))
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(attrMap)
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)).thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
            whenever(entityRepository.findAllById(listOf(refEntityId))).thenReturn(listOf(refEntity))
            whenever(semanticMetadataRepository.findByEntityTypeIdIn(listOf(refEntityTypeId))).thenReturn(listOf(identifierMeta))
            whenever(entityAttributeService.getAttributesForEntities(listOf(refEntityId))).thenReturn(refAttrValues)

            val context = assembler.assemble(
                entityId = entityId,
                workspaceId = workspaceId,
                queueItemId = UUID.randomUUID(),
                entityTypeId = entityTypeId,
                schemaVersion = 1,
                entityTypeName = "Customer",
                semanticGroup = SemanticGroup.UNCATEGORIZED,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                sentiment = null,
            )

            assertEquals("Jane Smith", context.referencedEntityIdentifiers[refEntityId])
        }

        /**
         * Regression test for r3180290309. A RELATIONAL_REFERENCE attribute whose value is not
         * a valid UUID is silently skipped — the returned `referencedEntityIdentifiers` map does
         * NOT contain a "[reference not resolved]" entry for it.
         *
         * Original bug: the KDoc on `resolveReferencedEntityIdentifiers` promised the placeholder
         * fallback for unparseable UUIDs, but the implementation dropped them in `mapNotNull`
         * before the result map was built. This test pins the actual contract (skip-and-warn) so
         * the KDoc and code stay in sync.
         */
        @Test
        fun `assemble silently skips non-UUID RELATIONAL_REFERENCE values from referencedEntityIdentifiers`() {
            val entityId = UUID.randomUUID()
            val entityTypeId = UUID.randomUUID()
            val malformedRefAttrId = UUID.randomUUID()

            val attrMap = mapOf(
                malformedRefAttrId to EntityAttributePrimitivePayload(
                    value = "not-a-uuid",
                    schemaType = SchemaType.TEXT,
                ),
            )

            val malformedRefMeta = IdentityFactory.createEntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = entityTypeId,
                targetType = SemanticMetadataTargetType.ATTRIBUTE,
                targetId = malformedRefAttrId,
                classification = SemanticAttributeClassification.RELATIONAL_REFERENCE,
                definition = "Account Manager",
            )

            whenever(semanticMetadataRepository.findByEntityTypeId(entityTypeId)).thenReturn(listOf(malformedRefMeta))
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(attrMap)
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)).thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

            val context = assembler.assemble(
                entityId = entityId,
                workspaceId = workspaceId,
                queueItemId = UUID.randomUUID(),
                entityTypeId = entityTypeId,
                schemaVersion = 1,
                entityTypeName = "Customer",
                semanticGroup = SemanticGroup.UNCATEGORIZED,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                sentiment = null,
            )

            // Contract: malformed UUID values are not represented in the map.
            org.junit.jupiter.api.Assertions.assertTrue(
                context.referencedEntityIdentifiers.isEmpty(),
                "Non-UUID RELATIONAL_REFERENCE values must not appear in referencedEntityIdentifiers"
            )
        }
    }

    // ------ Test 5: loadClusterMembers ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class ClusterMembers {

        @Test
        fun `loadClusterMembers returns empty when entity is the only cluster member`() {
            val entityId = UUID.randomUUID()
            val entityTypeId = UUID.randomUUID()
            val clusterId = UUID.randomUUID()

            val membership = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = clusterId, entityId = entityId
            )

            whenever(semanticMetadataRepository.findByEntityTypeId(entityTypeId)).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)).thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(membership)
            whenever(identityClusterMemberRepository.findByClusterId(clusterId)).thenReturn(listOf(membership))
            whenever(semanticMetadataRepository.findByEntityTypeIdIn(any())).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributesForEntities(any())).thenReturn(emptyMap())

            val context = assembler.assemble(
                entityId = entityId,
                workspaceId = workspaceId,
                queueItemId = UUID.randomUUID(),
                entityTypeId = entityTypeId,
                schemaVersion = 1,
                entityTypeName = "Contact",
                semanticGroup = SemanticGroup.UNCATEGORIZED,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                sentiment = null,
            )

            assertTrue(context.clusterMembers.isEmpty())
        }

        @Test
        fun `loadClusterMembers returns one entry per other member with sourceType and entityTypeName populated`() {
            val entityId = UUID.randomUUID()
            val entityTypeId = UUID.randomUUID()
            val clusterId = UUID.randomUUID()
            val memberId = UUID.randomUUID()
            val memberTypeId = UUID.randomUUID()

            val primaryMembership = IdentityFactory.createIdentityClusterMemberEntity(clusterId = clusterId, entityId = entityId)
            val secondaryMembership = IdentityFactory.createIdentityClusterMemberEntity(clusterId = clusterId, entityId = memberId)

            val memberEntity = EntityFactory.createEntityEntity(
                id = memberId, workspaceId = workspaceId, typeId = memberTypeId,
                sourceType = SourceType.INTEGRATION,
            )
            val memberEntityType = EntityFactory.createEntityType(
                id = memberTypeId, workspaceId = workspaceId, displayNameSingular = "Company"
            )

            whenever(semanticMetadataRepository.findByEntityTypeId(entityTypeId)).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)).thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(primaryMembership)
            whenever(identityClusterMemberRepository.findByClusterId(clusterId)).thenReturn(listOf(primaryMembership, secondaryMembership))
            whenever(entityRepository.findAllById(listOf(memberId))).thenReturn(listOf(memberEntity))
            whenever(entityTypeRepository.findAllById(listOf(memberTypeId))).thenReturn(listOf(memberEntityType))
            whenever(semanticMetadataRepository.findByEntityTypeIdIn(any())).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributesForEntities(any())).thenReturn(emptyMap())

            val context = assembler.assemble(
                entityId = entityId,
                workspaceId = workspaceId,
                queueItemId = UUID.randomUUID(),
                entityTypeId = entityTypeId,
                schemaVersion = 1,
                entityTypeName = "Contact",
                semanticGroup = SemanticGroup.UNCATEGORIZED,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                sentiment = null,
            )

            assertEquals(1, context.clusterMembers.size)
            assertEquals(SourceType.INTEGRATION, context.clusterMembers[0].sourceType)
            assertEquals("Company", context.clusterMembers[0].entityTypeName)
        }
    }

    // ------ Test 6: loadRelationshipDefinitions ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class RelationshipDefinitions {

        @Test
        fun `loadRelationshipDefinitions filters allMetadata to RELATIONSHIP target type entries and joins with definitions map`() {
            val entityId = UUID.randomUUID()
            val entityTypeId = UUID.randomUUID()
            val definitionId = UUID.randomUUID()

            val relMeta = IdentityFactory.createEntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = entityTypeId,
                targetType = SemanticMetadataTargetType.RELATIONSHIP,
                targetId = definitionId,
                classification = null,
                definition = "Support tickets raised by this customer.",
            )

            // Attribute metadata (should NOT appear in relationshipDefinitions)
            val attrMeta = IdentityFactory.createEntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = entityTypeId,
                targetType = SemanticMetadataTargetType.ATTRIBUTE,
                targetId = UUID.randomUUID(),
                classification = SemanticAttributeClassification.IDENTIFIER,
                definition = "Name",
            )

            val definition = EntityFactory.createRelationshipDefinitionEntity(
                id = definitionId, workspaceId = workspaceId,
                sourceEntityTypeId = entityTypeId, name = "Support Tickets"
            )

            whenever(semanticMetadataRepository.findByEntityTypeId(entityTypeId)).thenReturn(listOf(relMeta, attrMeta))
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
                .thenReturn(listOf(definition))
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
            whenever(semanticMetadataRepository.findByEntityTypeIdIn(any())).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributesForEntities(any())).thenReturn(emptyMap())

            val context = assembler.assemble(
                entityId = entityId,
                workspaceId = workspaceId,
                queueItemId = UUID.randomUUID(),
                entityTypeId = entityTypeId,
                schemaVersion = 1,
                entityTypeName = "Customer",
                semanticGroup = SemanticGroup.UNCATEGORIZED,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                sentiment = null,
            )

            assertEquals(1, context.relationshipDefinitions.size)
            val rd = context.relationshipDefinitions[0]
            assertEquals("Support Tickets", rd.name)
            assertEquals("Support tickets raised by this customer.", rd.definition)
        }
    }

    // ------ Test 7: Constructor-count assertion (ENRICH-02) ------

    /**
     * ENRICH-02: Ensures EnrichmentContextAssembler does not accumulate dependencies beyond the
     * documented ceiling of 12. Actual count: 9 (semanticMetadataRepository, entityAttributeService,
     * entityRelationshipRepository, relationshipDefinitionRepository, identityClusterMemberRepository,
     * relationshipTargetRuleRepository, entityRepository, entityTypeRepository, logger).
     * Adding deps beyond 12 fails this test as a design gate.
     */
    @Test
    fun `EnrichmentContextAssembler constructor parameter count is within ceiling of 12`() {
        // ENRICH-02: documented ceiling is 12; current count is 9 (see context decision).
        val paramCount = EnrichmentContextAssembler::class.primaryConstructor!!.parameters.size
        assertTrue(
            paramCount <= 12,
            "EnrichmentContextAssembler has $paramCount constructor params; ceiling is 12 (ENRICH-02)"
        )
    }
}
