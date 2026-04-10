package riven.core.service.enrichment

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.enrichment.EntityEmbeddingEntity
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.EntityTypeSemanticMetadataEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.entity.entity.RelationshipTargetRuleEntity
import riven.core.entity.identity.IdentityClusterMemberEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.workflow.ExecutionQueueStatus
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.integration.SourceType
import riven.core.exceptions.NotFoundException
import riven.core.models.common.validation.Schema
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.enrichment.EntityEmbeddingRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityAttributeService
import riven.core.service.enrichment.provider.EmbeddingProvider
import riven.core.service.util.BaseServiceTest
import riven.core.service.workflow.enrichment.EnrichmentWorkflow
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.enrichment.EnrichmentFactory
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.identity.IdentityFactory
import riven.core.service.util.factory.workflow.ExecutionQueueFactory
import java.time.ZonedDateTime
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EnrichmentService::class,
    ]
)
class EnrichmentServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var entityEmbeddingRepository: EntityEmbeddingRepository

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

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
    private lateinit var embeddingProvider: EmbeddingProvider

    @MockitoBean
    private lateinit var workflowClient: WorkflowClient

    @Autowired
    private lateinit var enrichmentService: EnrichmentService

    // ------------------------------------------------------------------
    // enqueueAndProcess: embeddability gating
    // ------------------------------------------------------------------

    @Test
    fun `enqueueAndProcess skips INTEGRATION entities silently`() {
        val entityId = UUID.randomUUID()
        val entity = buildEntityEntity(entityId, sourceType = SourceType.INTEGRATION)
        whenever(entityRepository.findByIdAndWorkspaceId(entityId, workspaceId)).thenReturn(Optional.of(entity))

        enrichmentService.enqueueAndProcess(entityId, workspaceId)

        verify(executionQueueRepository, never()).save(any())
        verify(workflowClient, never()).newWorkflowStub(any<Class<*>>(), any<WorkflowOptions>())
    }

    @Test
    fun `enqueueAndProcess creates queue item and starts workflow for USER_CREATED entity`() {
        val entityId = UUID.randomUUID()
        val queueItemId = UUID.randomUUID()
        val entity = buildEntityEntity(entityId, sourceType = SourceType.USER_CREATED)
        val savedQueueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)

        whenever(entityRepository.findByIdAndWorkspaceId(entityId, workspaceId)).thenReturn(Optional.of(entity))
        whenever(executionQueueRepository.save(any<ExecutionQueueEntity>())).thenReturn(savedQueueItem)

        val workflowStub = mock<EnrichmentWorkflow>()
        whenever(workflowClient.newWorkflowStub(eq(EnrichmentWorkflow::class.java), any<WorkflowOptions>())).thenReturn(workflowStub)

        enrichmentService.enqueueAndProcess(entityId, workspaceId)

        verify(executionQueueRepository).save(any())
        verify(workflowClient).newWorkflowStub(eq(EnrichmentWorkflow::class.java), any<WorkflowOptions>())
    }

    @Test
    fun `enqueueAndProcess creates queue item for IMPORT entity`() {
        val entityId = UUID.randomUUID()
        val queueItemId = UUID.randomUUID()
        val entity = buildEntityEntity(entityId, sourceType = SourceType.IMPORT)
        val savedQueueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)

        whenever(entityRepository.findByIdAndWorkspaceId(entityId, workspaceId)).thenReturn(Optional.of(entity))
        whenever(executionQueueRepository.save(any<ExecutionQueueEntity>())).thenReturn(savedQueueItem)

        val workflowStub = mock<EnrichmentWorkflow>()
        whenever(workflowClient.newWorkflowStub(eq(EnrichmentWorkflow::class.java), any<WorkflowOptions>())).thenReturn(workflowStub)

        enrichmentService.enqueueAndProcess(entityId, workspaceId)

        verify(executionQueueRepository).save(any())
    }

    /**
     * Regression test for PR #174 (r3056654527 / r3056654536): enqueueAndProcess must
     * verify workspace ownership. When the entity belongs to a different workspace,
     * findByIdAndWorkspaceId returns empty and ServiceUtil.findOrThrow raises
     * NotFoundException — which does not leak entity existence across tenants.
     */
    @Test
    fun `enqueueAndProcess throws NotFoundException when entity belongs to a different workspace`() {
        val entityId = UUID.randomUUID()
        whenever(entityRepository.findByIdAndWorkspaceId(entityId, workspaceId)).thenReturn(Optional.empty())

        assertThrows(NotFoundException::class.java) {
            enrichmentService.enqueueAndProcess(entityId, workspaceId)
        }

        verify(executionQueueRepository, never()).save(any())
        verify(workflowClient, never()).newWorkflowStub(any<Class<*>>(), any<WorkflowOptions>())
    }

    // ------------------------------------------------------------------
    // fetchContext: context assembly and status transition
    // ------------------------------------------------------------------

    @Test
    fun `fetchContext returns populated EnrichmentContext`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entityEntity))
        whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())

        val context = enrichmentService.fetchContext(queueItemId)

        assertNotNull(context)
        assertEquals(queueItemId, context.queueItemId)
        assertEquals(entityId, context.entityId)
        assertEquals(workspaceId, context.workspaceId)
        assertEquals(typeId, context.entityTypeId)
    }

    @Test
    fun `fetchContext sets queue item to CLAIMED with timestamp`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entityEntity))
        whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())

        enrichmentService.fetchContext(queueItemId)

        val captor = ArgumentCaptor.forClass(ExecutionQueueEntity::class.java)
        verify(executionQueueRepository, atLeast(1)).save(captor.capture())
        val claimedItem = captor.allValues.first { it.status == ExecutionQueueStatus.CLAIMED }
        assertEquals(ExecutionQueueStatus.CLAIMED, claimedItem.status)
        assertNotNull(claimedItem.claimedAt)
    }

    @Test
    fun `fetchContext uses EntityTypeEntity version as schemaVersion`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val version = 5
        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId, version = version)

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entityEntity))
        whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(version, context.schemaVersion)
    }

    @Test
    fun `fetchContext resolves semantic labels from metadata`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val attrId = UUID.randomUUID()
        val semanticLabel = "Acquisition Channel"

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val metadata = buildSemanticMetadata(typeId, SemanticMetadataTargetType.ATTRIBUTE, attrId, definition = semanticLabel)
        val attribute = EntityAttributePrimitivePayload(value = "Social Media", schemaType = SchemaType.TEXT)

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entityEntity))
        whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(listOf(metadata))
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(attrId to attribute))
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.attributes.size)
        assertEquals(semanticLabel, context.attributes[0].semanticLabel)
        assertEquals("Social Media", context.attributes[0].value)
    }

    @Test
    fun `fetchContext falls back to attribute key when no metadata`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val attrId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)
        val attribute = EntityAttributePrimitivePayload(value = "Some Value", schemaType = SchemaType.TEXT)

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entityEntity))
        whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(attrId to attribute))
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.attributes.size)
        // Falls back to attrId.toString() when no metadata found
        assertEquals(attrId.toString(), context.attributes[0].semanticLabel)
    }

    @Test
    fun `fetchContext groups relationships by definition with counts`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val definitionId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val relationship1 = buildRelationshipEntity(entityId, UUID.randomUUID(), definitionId, workspaceId)
        val relationship2 = buildRelationshipEntity(entityId, UUID.randomUUID(), definitionId, workspaceId)
        val definition = buildRelationshipDefinition(definitionId, typeId, "Support Tickets", workspaceId)

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entityEntity))
        whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(listOf(relationship1, relationship2))
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(listOf(definition))

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.relationshipSummaries.size)
        assertEquals("Support Tickets", context.relationshipSummaries[0].relationshipName)
        assertEquals(2, context.relationshipSummaries[0].count)
    }

    @Test
    fun `fetchContext throws NotFoundException for missing queue item`() {
        val queueItemId = UUID.randomUUID()
        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.empty())

        assertThrows(NotFoundException::class.java) {
            enrichmentService.fetchContext(queueItemId)
        }
    }

    // ------------------------------------------------------------------
    // fetchContext: Phase 3 — attribute classification mapping
    // ------------------------------------------------------------------

    /**
     * Bug prevention: classification field on EnrichmentAttributeContext must be populated
     * from semantic metadata. Previously it was always null (field existed on model but
     * loadAttributeContexts didn't map it). This test verifies the fix.
     */
    @Test
    fun `fetchContext maps classification from semantic metadata onto EnrichmentAttributeContext`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val attrId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val metadata = buildSemanticMetadata(
            typeId, SemanticMetadataTargetType.ATTRIBUTE, attrId,
            definition = "Industry", classification = SemanticAttributeClassification.CATEGORICAL
        )
        val attribute = EntityAttributePrimitivePayload(value = "Technology", schemaType = SchemaType.SELECT)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(listOf(metadata))
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(attrId to attribute))
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.attributes.size)
        assertEquals(SemanticAttributeClassification.CATEGORICAL, context.attributes[0].classification)
    }

    @Test
    fun `fetchContext sets classification to null on EnrichmentAttributeContext when metadata has no classification`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val attrId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val metadata = buildSemanticMetadata(typeId, SemanticMetadataTargetType.ATTRIBUTE, attrId, definition = "Name")
        val attribute = EntityAttributePrimitivePayload(value = "Acme", schemaType = SchemaType.TEXT)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(listOf(metadata))
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(attrId to attribute))
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

        val context = enrichmentService.fetchContext(queueItemId)

        assertNull(context.attributes[0].classification)
    }

    // ------------------------------------------------------------------
    // fetchContext: Phase 3 — cluster member loading
    // ------------------------------------------------------------------

    @Test
    fun `fetchContext populates clusterMembers when entity is in a cluster`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val clusterId = UUID.randomUUID()
        val memberEntityId = UUID.randomUUID()
        val memberTypeId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val membership = buildClusterMember(entityId, clusterId)
        val memberMembership = buildClusterMember(memberEntityId, clusterId)
        val memberEntity = buildEntityEntity(memberEntityId, typeId = memberTypeId, sourceType = SourceType.INTEGRATION)
        val memberEntityType = buildEntityTypeEntity(memberTypeId, displayName = "Company")

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(membership)
        whenever(identityClusterMemberRepository.findByClusterId(clusterId)).thenReturn(listOf(membership, memberMembership))
        whenever(entityRepository.findAllById(listOf(memberEntityId))).thenReturn(listOf(memberEntity))
        whenever(entityTypeRepository.findAllById(listOf(memberTypeId))).thenReturn(listOf(memberEntityType))

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.clusterMembers.size)
        assertEquals(SourceType.INTEGRATION, context.clusterMembers[0].sourceType)
        assertEquals("Company", context.clusterMembers[0].entityTypeName)
    }

    @Test
    fun `fetchContext populates clusterMembers as empty list when entity has no cluster`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

        val context = enrichmentService.fetchContext(queueItemId)

        assertTrue(context.clusterMembers.isEmpty())
    }

    @Test
    fun `fetchContext excludes the entity itself from clusterMembers`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val clusterId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val membership = buildClusterMember(entityId, clusterId)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(membership)
        // Only the entity itself is in the cluster — no other members
        whenever(identityClusterMemberRepository.findByClusterId(clusterId)).thenReturn(listOf(membership))

        val context = enrichmentService.fetchContext(queueItemId)

        assertTrue(context.clusterMembers.isEmpty())
    }

    // ------------------------------------------------------------------
    // fetchContext: Phase 3 — RELATIONAL_REFERENCE resolution
    // ------------------------------------------------------------------

    @Test
    fun `fetchContext resolves RELATIONAL_REFERENCE attribute value to identifier display string`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val attrId = UUID.randomUUID()
        val referencedEntityId = UUID.randomUUID()
        val referencedTypeId = UUID.randomUUID()
        val identifierAttrId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val attrMetadata = buildSemanticMetadata(
            typeId, SemanticMetadataTargetType.ATTRIBUTE, attrId,
            definition = "Company", classification = SemanticAttributeClassification.RELATIONAL_REFERENCE
        )
        val attribute = EntityAttributePrimitivePayload(value = referencedEntityId.toString(), schemaType = SchemaType.TEXT)

        val referencedEntity = buildEntityEntity(referencedEntityId, typeId = referencedTypeId)
        val identifierAttrMetadata = buildSemanticMetadata(
            referencedTypeId, SemanticMetadataTargetType.ATTRIBUTE, identifierAttrId,
            definition = "Company Name", classification = SemanticAttributeClassification.IDENTIFIER
        )
        val identifierAttrValue = EntityAttributePrimitivePayload(value = "Acme Corp", schemaType = SchemaType.TEXT)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(listOf(attrMetadata))
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(attrId to attribute))
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
        whenever(entityRepository.findAllById(listOf(referencedEntityId))).thenReturn(listOf(referencedEntity))
        whenever(semanticMetadataRepository.findByEntityTypeIdIn(listOf(referencedTypeId))).thenReturn(listOf(identifierAttrMetadata))
        whenever(entityAttributeService.getAttributesForEntities(listOf(referencedEntityId))).thenReturn(
            mapOf(referencedEntityId to mapOf(identifierAttrId to identifierAttrValue))
        )

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.referencedEntityIdentifiers.size)
        assertEquals("Acme Corp", context.referencedEntityIdentifiers[referencedEntityId])
    }

    @Test
    fun `fetchContext falls back to reference not resolved when referenced entity has no IDENTIFIER attribute`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val attrId = UUID.randomUUID()
        val referencedEntityId = UUID.randomUUID()
        val referencedTypeId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val attrMetadata = buildSemanticMetadata(
            typeId, SemanticMetadataTargetType.ATTRIBUTE, attrId,
            definition = "Company", classification = SemanticAttributeClassification.RELATIONAL_REFERENCE
        )
        val attribute = EntityAttributePrimitivePayload(value = referencedEntityId.toString(), schemaType = SchemaType.TEXT)
        val referencedEntity = buildEntityEntity(referencedEntityId, typeId = referencedTypeId)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(listOf(attrMetadata))
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(attrId to attribute))
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
        whenever(entityRepository.findAllById(listOf(referencedEntityId))).thenReturn(listOf(referencedEntity))
        // No IDENTIFIER attribute metadata for referenced type
        whenever(semanticMetadataRepository.findByEntityTypeIdIn(listOf(referencedTypeId))).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributesForEntities(listOf(referencedEntityId))).thenReturn(emptyMap())

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.referencedEntityIdentifiers.size)
        assertEquals("[reference not resolved]", context.referencedEntityIdentifiers[referencedEntityId])
    }

    @Test
    fun `fetchContext returns empty referencedEntityIdentifiers when no RELATIONAL_REFERENCE attributes`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

        val context = enrichmentService.fetchContext(queueItemId)

        assertTrue(context.referencedEntityIdentifiers.isEmpty())
    }

    // ------------------------------------------------------------------
    // fetchContext: Phase 3 — relationship definitions
    // ------------------------------------------------------------------

    @Test
    fun `fetchContext populates relationshipDefinitions from RELATIONSHIP semantic metadata`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val definitionId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val relMetadata = buildSemanticMetadata(
            typeId, SemanticMetadataTargetType.RELATIONSHIP, definitionId,
            definition = "Escalation records from the help desk system."
        )
        val definition = buildRelationshipDefinition(definitionId, typeId, "Support Tickets", workspaceId)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(listOf(relMetadata))
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(listOf(definition))
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.relationshipDefinitions.size)
        assertEquals("Support Tickets", context.relationshipDefinitions[0].name)
        assertEquals("Escalation records from the help desk system.", context.relationshipDefinitions[0].definition)
    }

    @Test
    fun `fetchContext returns empty relationshipDefinitions when no RELATIONSHIP metadata exists`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

        val context = enrichmentService.fetchContext(queueItemId)

        assertTrue(context.relationshipDefinitions.isEmpty())
    }

    // ------------------------------------------------------------------
    // fetchContext: Phase 3 — relationship aggregate enrichment
    // ------------------------------------------------------------------

    @Test
    fun `fetchContext populates latestActivityAt with ISO timestamp of most recently created relationship`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val definitionId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val olderTime = ZonedDateTime.now().minusDays(2)
        val newerTime = ZonedDateTime.now().minusDays(1)

        val relationship1 = buildRelationshipEntity(entityId, UUID.randomUUID(), definitionId, workspaceId, createdAt = olderTime)
        val relationship2 = buildRelationshipEntity(entityId, UUID.randomUUID(), definitionId, workspaceId, createdAt = newerTime)
        val definition = buildRelationshipDefinition(definitionId, typeId, "Support Tickets", workspaceId)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(listOf(relationship1, relationship2))
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(listOf(definition))
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.relationshipSummaries.size)
        assertNotNull(context.relationshipSummaries[0].latestActivityAt)
    }

    @Test
    fun `fetchContext populates topCategories with formatted categorical breakdowns for relationships`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val definitionId = UUID.randomUUID()
        val targetEntityId1 = UUID.randomUUID()
        val targetEntityId2 = UUID.randomUUID()
        val targetEntityId3 = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()
        val statusAttrId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val relationship1 = buildRelationshipEntity(entityId, targetEntityId1, definitionId, workspaceId)
        val relationship2 = buildRelationshipEntity(entityId, targetEntityId2, definitionId, workspaceId)
        val relationship3 = buildRelationshipEntity(entityId, targetEntityId3, definitionId, workspaceId)
        val definition = buildRelationshipDefinition(definitionId, typeId, "Support Tickets", workspaceId)
        val targetRule = buildRelationshipTargetRule(definitionId, targetTypeId)

        val statusMetadata = buildSemanticMetadata(
            targetTypeId, SemanticMetadataTargetType.ATTRIBUTE, statusAttrId,
            definition = "Status", classification = SemanticAttributeClassification.CATEGORICAL
        )
        val openPayload = EntityAttributePrimitivePayload(value = "Open", schemaType = SchemaType.SELECT)
        val closedPayload = EntityAttributePrimitivePayload(value = "Closed", schemaType = SchemaType.SELECT)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(listOf(relationship1, relationship2, relationship3))
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(listOf(definition))
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
        whenever(relationshipTargetRuleRepository.findByRelationshipDefinitionIdIn(listOf(definitionId))).thenReturn(listOf(targetRule))
        whenever(semanticMetadataRepository.findByEntityTypeId(targetTypeId)).thenReturn(listOf(statusMetadata))
        whenever(entityAttributeService.getAttributesForEntities(any())).thenReturn(
            mapOf(
                targetEntityId1 to mapOf(statusAttrId to openPayload),
                targetEntityId2 to mapOf(statusAttrId to openPayload),
                targetEntityId3 to mapOf(statusAttrId to closedPayload),
            )
        )

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.relationshipSummaries.size)
        val summary = context.relationshipSummaries[0]
        assertEquals(1, summary.topCategories.size)
        assertTrue(summary.topCategories[0].contains("Status"))
        assertTrue(summary.topCategories[0].contains("Open"))
        assertTrue(summary.topCategories[0].contains("Closed"))
    }

    @Test
    fun `fetchContext returns empty topCategories when target entity type has no CATEGORICAL attributes`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val definitionId = UUID.randomUUID()
        val targetEntityId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId)

        val relationship = buildRelationshipEntity(entityId, targetEntityId, definitionId, workspaceId)
        val definition = buildRelationshipDefinition(definitionId, typeId, "Support Tickets", workspaceId)
        val targetRule = buildRelationshipTargetRule(definitionId, targetTypeId)

        setupFetchContextMocks(queueItemId, entityId, typeId, queueItem, entityEntity, entityType)
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(listOf(relationship))
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(listOf(definition))
        whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
        whenever(relationshipTargetRuleRepository.findByRelationshipDefinitionIdIn(listOf(definitionId))).thenReturn(listOf(targetRule))
        // No CATEGORICAL metadata for target type
        whenever(semanticMetadataRepository.findByEntityTypeId(targetTypeId)).thenReturn(emptyList())

        val context = enrichmentService.fetchContext(queueItemId)

        assertEquals(1, context.relationshipSummaries.size)
        assertTrue(context.relationshipSummaries[0].topCategories.isEmpty())
    }

    // ------------------------------------------------------------------
    // storeEmbedding: upsert and status transition
    // ------------------------------------------------------------------

    @Test
    fun `storeEmbedding deletes old and saves new embedding with correct metadata`() {
        val queueItemId = UUID.randomUUID()
        val context = EnrichmentFactory.enrichmentContext(workspaceId = workspaceId)
        val embedding = FloatArray(1536) { 0.1f }
        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = context.entityId, workspaceId = workspaceId)
        val modelName = "text-embedding-3-small"

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityEmbeddingRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(embeddingProvider.getModelName()).thenReturn(modelName)

        enrichmentService.storeEmbedding(queueItemId, context, embedding, truncated = false)

        verify(entityEmbeddingRepository).deleteByEntityId(context.entityId)
        val captor = ArgumentCaptor.forClass(EntityEmbeddingEntity::class.java)
        verify(entityEmbeddingRepository).save(captor.capture())
        val saved = captor.value
        assertEquals(context.entityId, saved.entityId)
        assertEquals(context.workspaceId, saved.workspaceId)
        assertEquals(context.entityTypeId, saved.entityTypeId)
        assertEquals(context.schemaVersion, saved.schemaVersion)
        assertEquals(modelName, saved.embeddingModel)
        assertFalse(saved.truncated)
    }

    @Test
    fun `storeEmbedding passes truncated true to EntityEmbeddingEntity when text was truncated`() {
        val queueItemId = UUID.randomUUID()
        val context = EnrichmentFactory.enrichmentContext(workspaceId = workspaceId)
        val embedding = FloatArray(1536) { 0.1f }
        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = context.entityId, workspaceId = workspaceId)

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityEmbeddingRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(embeddingProvider.getModelName()).thenReturn("text-embedding-3-small")

        enrichmentService.storeEmbedding(queueItemId, context, embedding, truncated = true)

        val captor = ArgumentCaptor.forClass(EntityEmbeddingEntity::class.java)
        verify(entityEmbeddingRepository).save(captor.capture())
        assertTrue(captor.value.truncated, "EntityEmbeddingEntity.truncated should be true when truncated=true was passed")
    }

    @Test
    fun `storeEmbedding marks queue item COMPLETED`() {
        val queueItemId = UUID.randomUUID()
        val context = EnrichmentFactory.enrichmentContext(workspaceId = workspaceId)
        val embedding = FloatArray(1536) { 0.1f }
        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = context.entityId, workspaceId = workspaceId)

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityEmbeddingRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(embeddingProvider.getModelName()).thenReturn("text-embedding-3-small")

        enrichmentService.storeEmbedding(queueItemId, context, embedding, truncated = false)

        val captor = ArgumentCaptor.forClass(ExecutionQueueEntity::class.java)
        verify(executionQueueRepository, atLeast(1)).save(captor.capture())
        val completedItem = captor.allValues.first { it.status == ExecutionQueueStatus.COMPLETED }
        assertEquals(ExecutionQueueStatus.COMPLETED, completedItem.status)
    }

    // ------------------------------------------------------------------
    // Private builder helpers
    // ------------------------------------------------------------------

    private fun setupFetchContextMocks(
        queueItemId: UUID,
        entityId: UUID,
        typeId: UUID,
        queueItem: ExecutionQueueEntity,
        entityEntity: EntityEntity,
        entityType: EntityTypeEntity,
    ) {
        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entityEntity))
        whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
    }

    // ------------------------------------------------------------------
    // Test fixture helpers — thin wrappers over shared factories so each
    // test can express only the parameters it cares about. JPA entity
    // construction itself lives in the factory classes per CLAUDE.md.
    // ------------------------------------------------------------------

    private fun buildEntityEntity(
        id: UUID,
        sourceType: SourceType = SourceType.USER_CREATED,
        typeId: UUID = UUID.randomUUID(),
    ): EntityEntity = EntityFactory.createEntityEntity(
        id = id,
        workspaceId = workspaceId,
        typeId = typeId,
        typeKey = "test_type",
        sourceType = sourceType,
    )

    private fun buildEntityTypeEntity(
        id: UUID,
        version: Int = 1,
        displayName: String = "Test Entity",
    ): EntityTypeEntity = EntityFactory.createEntityType(
        id = id,
        key = "test_type",
        displayNameSingular = displayName,
        displayNamePlural = "${displayName}s",
        workspaceId = workspaceId,
        schema = Schema(key = SchemaType.TEXT),
        version = version,
        semanticGroup = SemanticGroup.UNCATEGORIZED,
    )

    private fun buildSemanticMetadata(
        entityTypeId: UUID,
        targetType: SemanticMetadataTargetType,
        targetId: UUID,
        definition: String? = null,
        classification: SemanticAttributeClassification? = null,
    ): EntityTypeSemanticMetadataEntity = IdentityFactory.createEntityTypeSemanticMetadataEntity(
        workspaceId = workspaceId,
        entityTypeId = entityTypeId,
        targetType = targetType,
        targetId = targetId,
        definition = definition,
        classification = classification,
    )

    private fun buildRelationshipEntity(
        sourceId: UUID,
        targetId: UUID,
        definitionId: UUID,
        workspaceId: UUID,
        createdAt: ZonedDateTime = ZonedDateTime.now(),
    ): EntityRelationshipEntity = EntityFactory.createRelationshipEntity(
        workspaceId = workspaceId,
        sourceId = sourceId,
        targetId = targetId,
        definitionId = definitionId,
        createdAt = createdAt,
    )

    private fun buildRelationshipDefinition(
        id: UUID,
        sourceEntityTypeId: UUID,
        name: String,
        workspaceId: UUID,
    ): RelationshipDefinitionEntity = EntityFactory.createRelationshipDefinitionEntity(
        id = id,
        workspaceId = workspaceId,
        sourceEntityTypeId = sourceEntityTypeId,
        name = name,
    )

    private fun buildRelationshipTargetRule(
        definitionId: UUID,
        targetEntityTypeId: UUID,
    ): RelationshipTargetRuleEntity = EntityFactory.createTargetRuleEntity(
        relationshipDefinitionId = definitionId,
        targetEntityTypeId = targetEntityTypeId,
    )

    private fun buildClusterMember(
        entityId: UUID,
        clusterId: UUID,
    ): IdentityClusterMemberEntity = IdentityFactory.createIdentityClusterMemberEntity(
        clusterId = clusterId,
        entityId = entityId,
    )
}
