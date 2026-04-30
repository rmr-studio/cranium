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
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.models.connotation.MetadataStalenessModel
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.integration.SourceType
import riven.core.exceptions.NotFoundException
import riven.core.models.common.validation.Schema
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.connotation.EntityConnotationRepository
import riven.core.repository.enrichment.EntityEmbeddingRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.catalog.ScaleMappingType
import riven.core.models.catalog.SentimentScale
import riven.core.models.connotation.AnalysisTier
import riven.core.service.auth.AuthTokenService
import riven.core.service.catalog.ManifestCatalogService
import riven.core.service.connotation.ConnotationAnalysisService
import riven.core.service.entity.EntityAttributeService
import riven.core.configuration.properties.EnrichmentConfigurationProperties
import riven.core.service.enrichment.provider.EmbeddingProvider
import riven.core.service.workspace.WorkspaceService
import riven.core.service.util.BaseServiceTest
import riven.core.service.workflow.enrichment.EnrichmentWorkflow
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.enrichment.EnrichmentFactory
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.identity.IdentityFactory
import riven.core.service.util.factory.workflow.ExecutionQueueFactory
import org.junit.jupiter.api.BeforeEach
import riven.core.entity.connotation.EntityConnotationEntity
import riven.core.models.connotation.SentimentMetadata
import java.time.ZonedDateTime
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EnrichmentService::class,
        riven.core.configuration.util.ObjectMapperConfig::class,
    ]
)
class EnrichmentServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var entityEmbeddingRepository: EntityEmbeddingRepository

    @MockitoBean
    private lateinit var entityConnotationRepository: EntityConnotationRepository

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
    private lateinit var enrichmentProperties: EnrichmentConfigurationProperties

    @MockitoBean
    private lateinit var workflowClient: WorkflowClient

    @MockitoBean
    private lateinit var workspaceService: WorkspaceService

    @MockitoBean
    private lateinit var manifestCatalogService: ManifestCatalogService

    @MockitoBean
    private lateinit var connotationAnalysisService: ConnotationAnalysisService

    @Autowired
    private lateinit var enrichmentService: EnrichmentService

    @Autowired
    private lateinit var objectMapper: tools.jackson.databind.ObjectMapper

    @BeforeEach
    fun setUp() {
        whenever(enrichmentProperties.vectorDimensions).thenReturn(1536)
        // Default: SENTIMENT remains NOT_APPLICABLE for tests that don't care about
        // connotation. Individual tests override `isConnotationEnabled` / signals as needed.
        whenever(workspaceService.isConnotationEnabled(any())).thenReturn(false)
    }

    /**
     * Capture the JSON snapshot passed to upsertByEntityId and deserialize it.
     * Verifies the call's entity/workspace identifiers match expectations.
     */
    private fun captureUpsertedSnapshot(
        entityId: UUID,
        workspaceId: UUID,
    ): riven.core.models.connotation.EntityMetadataSnapshot {
        val jsonCaptor = argumentCaptor<String>()
        verify(entityConnotationRepository).upsertByEntityId(
            eq(entityId),
            eq(workspaceId),
            jsonCaptor.capture(),
            any(),
        )
        return objectMapper.readValue(
            jsonCaptor.firstValue,
            riven.core.models.connotation.EntityMetadataSnapshot::class.java,
        )
    }

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
        verify(workflowStub).embed(queueItemId)
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

    /**
     * Validates @PreAuthorize workspace access control on enqueueAndProcess.
     * Calling with a workspaceId not in the test persona's workspace roles
     * must trigger AccessDeniedException from the security annotation.
     */
    @Test
    fun `enqueueAndProcess throws AccessDeniedException for unauthorized workspace`() {
        val entityId = UUID.randomUUID()
        val unauthorizedWorkspaceId = UUID.randomUUID()

        assertThrows(org.springframework.security.access.AccessDeniedException::class.java) {
            enrichmentService.enqueueAndProcess(entityId, unauthorizedWorkspaceId)
        }

        verify(entityRepository, never()).findByIdAndWorkspaceId(any(), any())
    }

    // ------------------------------------------------------------------
    // analyzeSemantics: context assembly and status transition
    // ------------------------------------------------------------------

    @Test
    fun `analyzeSemantics returns populated EnrichmentContext`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertNotNull(context)
        assertEquals(queueItemId, context.queueItemId)
        assertEquals(entityId, context.entityId)
        assertEquals(workspaceId, context.workspaceId)
        assertEquals(typeId, context.entityTypeId)
    }

    @Test
    fun `analyzeSemantics sets queue item to CLAIMED with timestamp`() {
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

        enrichmentService.analyzeSemantics(queueItemId)

        val captor = ArgumentCaptor.forClass(ExecutionQueueEntity::class.java)
        verify(executionQueueRepository, atLeast(1)).save(captor.capture())
        val claimedItem = captor.allValues.first { it.status == ExecutionQueueStatus.CLAIMED }
        assertEquals(ExecutionQueueStatus.CLAIMED, claimedItem.status)
        assertNotNull(claimedItem.claimedAt)
    }

    @Test
    fun `analyzeSemantics uses EntityTypeEntity version as schemaVersion`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(version, context.schemaVersion)
    }

    @Test
    fun `analyzeSemantics resolves semantic labels from metadata`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.attributes.size)
        assertEquals(semanticLabel, context.attributes[0].semanticLabel)
        assertEquals("Social Media", context.attributes[0].value)
    }

    @Test
    fun `analyzeSemantics falls back to attribute key when no metadata`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.attributes.size)
        // Falls back to attrId.toString() when no metadata found
        assertEquals(attrId.toString(), context.attributes[0].semanticLabel)
    }

    @Test
    fun `analyzeSemantics groups relationships by definition with counts`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.relationshipSummaries.size)
        assertEquals("Support Tickets", context.relationshipSummaries[0].relationshipName)
        assertEquals(2, context.relationshipSummaries[0].count)
    }

    @Test
    fun `analyzeSemantics throws NotFoundException for missing queue item`() {
        val queueItemId = UUID.randomUUID()
        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.empty())

        assertThrows(NotFoundException::class.java) {
            enrichmentService.analyzeSemantics(queueItemId)
        }
    }

    // ------------------------------------------------------------------
    // analyzeSemantics: Phase 3 — attribute classification mapping
    // ------------------------------------------------------------------

    /**
     * Bug prevention: classification field on EnrichmentAttributeContext must be populated
     * from semantic metadata. Previously it was always null (field existed on model but
     * loadAttributeContexts didn't map it). This test verifies the fix.
     */
    @Test
    fun `analyzeSemantics maps classification from semantic metadata onto EnrichmentAttributeContext`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.attributes.size)
        assertEquals(SemanticAttributeClassification.CATEGORICAL, context.attributes[0].classification)
    }

    @Test
    fun `analyzeSemantics sets classification to null on EnrichmentAttributeContext when metadata has no classification`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertNull(context.attributes[0].classification)
    }

    // ------------------------------------------------------------------
    // analyzeSemantics: Phase 3 — cluster member loading
    // ------------------------------------------------------------------

    @Test
    fun `analyzeSemantics populates clusterMembers when entity is in a cluster`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.clusterMembers.size)
        assertEquals(SourceType.INTEGRATION, context.clusterMembers[0].sourceType)
        assertEquals("Company", context.clusterMembers[0].entityTypeName)
    }

    @Test
    fun `analyzeSemantics populates clusterMembers as empty list when entity has no cluster`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertTrue(context.clusterMembers.isEmpty())
    }

    @Test
    fun `analyzeSemantics excludes the entity itself from clusterMembers`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertTrue(context.clusterMembers.isEmpty())
    }

    // ------------------------------------------------------------------
    // analyzeSemantics: Phase 3 — RELATIONAL_REFERENCE resolution
    // ------------------------------------------------------------------

    @Test
    fun `analyzeSemantics resolves RELATIONAL_REFERENCE attribute value to identifier display string`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.referencedEntityIdentifiers.size)
        assertEquals("Acme Corp", context.referencedEntityIdentifiers[referencedEntityId])
    }

    @Test
    fun `analyzeSemantics falls back to reference not resolved when referenced entity has no IDENTIFIER attribute`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.referencedEntityIdentifiers.size)
        assertEquals("[reference not resolved]", context.referencedEntityIdentifiers[referencedEntityId])
    }

    @Test
    fun `analyzeSemantics returns empty referencedEntityIdentifiers when no RELATIONAL_REFERENCE attributes`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertTrue(context.referencedEntityIdentifiers.isEmpty())
    }

    // ------------------------------------------------------------------
    // analyzeSemantics: Phase 3 — relationship definitions
    // ------------------------------------------------------------------

    @Test
    fun `analyzeSemantics populates relationshipDefinitions from RELATIONSHIP semantic metadata`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.relationshipDefinitions.size)
        assertEquals("Support Tickets", context.relationshipDefinitions[0].name)
        assertEquals("Escalation records from the help desk system.", context.relationshipDefinitions[0].definition)
    }

    @Test
    fun `analyzeSemantics returns empty relationshipDefinitions when no RELATIONSHIP metadata exists`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertTrue(context.relationshipDefinitions.isEmpty())
    }

    // ------------------------------------------------------------------
    // analyzeSemantics: Phase 3 — relationship aggregate enrichment
    // ------------------------------------------------------------------

    @Test
    fun `analyzeSemantics populates latestActivityAt with ISO timestamp of most recently created relationship`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.relationshipSummaries.size)
        assertNotNull(context.relationshipSummaries[0].latestActivityAt)
    }

    @Test
    fun `analyzeSemantics populates topCategories with formatted categorical breakdowns for relationships`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

        assertEquals(1, context.relationshipSummaries.size)
        val summary = context.relationshipSummaries[0]
        assertEquals(1, summary.topCategories.size)
        assertTrue(summary.topCategories[0].contains("Status"))
        assertTrue(summary.topCategories[0].contains("Open"))
        assertTrue(summary.topCategories[0].contains("Closed"))
    }

    @Test
    fun `analyzeSemantics returns empty topCategories when target entity type has no CATEGORICAL attributes`() {
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

        val context = enrichmentService.analyzeSemantics(queueItemId)

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

    /**
     * Validates that storeEmbedding rejects embeddings whose size doesn't match
     * the configured vector dimensions, preventing schema mismatch at the database level.
     */
    @Test
    fun `storeEmbedding throws when embedding size does not match configured dimensions`() {
        val queueItemId = UUID.randomUUID()
        val context = EnrichmentFactory.enrichmentContext(workspaceId = workspaceId)
        val wrongSizedEmbedding = FloatArray(768) { 0.1f }

        assertThrows(IllegalArgumentException::class.java) {
            enrichmentService.storeEmbedding(queueItemId, context, wrongSizedEmbedding, truncated = false)
        }

        verify(entityEmbeddingRepository, never()).save(any())
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
    // analyzeSemantics: connotation snapshot persistence (Phase A)
    // ------------------------------------------------------------------

    /**
     * Phase A: every analyzeSemantics call upserts a connotation snapshot into entity_connotation.
     * Persistence is a single atomic INSERT ... ON CONFLICT DO UPDATE keyed by entity_id.
     */
    @Test
    fun `analyzeSemantics persists connotation snapshot on every run`() {
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

        enrichmentService.analyzeSemantics(queueItemId)

        val snapshot = captureUpsertedSnapshot(entityId, workspaceId)
        assertEquals("v1", snapshot.snapshotVersion)
        verify(entityConnotationRepository, never()).save(any())
    }

    /**
     * Phase A: SENTIMENT metadata is a placeholder (NOT_APPLICABLE) until Phase B activates the
     * DETERMINISTIC mapper. RELATIONAL + STRUCTURAL metadata are populated deterministically.
     */
    @Test
    fun `analyzeSemantics snapshot has placeholder SENTIMENT and populated RELATIONAL+STRUCTURAL metadata`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId, displayName = "Customer")

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entityEntity))
        whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())

        enrichmentService.analyzeSemantics(queueItemId)

        val snapshot = captureUpsertedSnapshot(entityId, workspaceId)

        val sentiment = requireNotNull(snapshot.metadata.sentiment)
        assertEquals(ConnotationStatus.NOT_APPLICABLE, sentiment.status)
        assertNull(sentiment.sentiment)
        assertNull(sentiment.sentimentLabel)
        assertEquals(MetadataStalenessModel.ON_SOURCE_TEXT_CHANGE, sentiment.stalenessModel)

        val relational = requireNotNull(snapshot.metadata.relational)
        assertEquals(MetadataStalenessModel.ON_NEIGHBOR_CHANGE, relational.stalenessModel)
        assertNotNull(relational.snapshotAt)

        val structural = requireNotNull(snapshot.metadata.structural)
        assertEquals("Customer", structural.entityTypeName)
        assertEquals(SemanticGroup.UNCATEGORIZED, structural.semanticGroup)
        assertEquals(LifecycleDomain.UNCATEGORIZED, structural.lifecycleDomain)
        assertEquals(MetadataStalenessModel.ON_TYPE_METADATA_CHANGE, structural.stalenessModel)
    }

    /**
     * STRUCTURAL metadata snapshots schema version + attribute classifications + relationship
     * semantic definitions captured at embed time. Used by manifest reconciliation to detect drift.
     */
    @Test
    fun `analyzeSemantics STRUCTURAL metadata snapshots schema version and attribute classifications`() {
        val queueItemId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val attrId = UUID.randomUUID()
        val queueItem = ExecutionQueueFactory.createEnrichmentJob(id = queueItemId, entityId = entityId, workspaceId = workspaceId)
        val entityEntity = buildEntityEntity(entityId, typeId = typeId)
        val entityType = buildEntityTypeEntity(typeId, version = 7)
        val metadata = buildSemanticMetadata(
            typeId,
            SemanticMetadataTargetType.ATTRIBUTE,
            attrId,
            definition = "Email",
            classification = SemanticAttributeClassification.IDENTIFIER,
        )
        val attribute = EntityAttributePrimitivePayload(value = "alice@example.com", schemaType = SchemaType.TEXT)

        whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
        whenever(executionQueueRepository.save(any())).thenReturn(queueItem)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entityEntity))
        whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(listOf(metadata))
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(attrId to attribute))
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())

        enrichmentService.analyzeSemantics(queueItemId)

        val snapshot = captureUpsertedSnapshot(entityId, workspaceId)
        val structural = requireNotNull(snapshot.metadata.structural)
        assertEquals(7, structural.schemaVersion)
        assertEquals(1, structural.attributeClassifications.size)
        val attrSnapshot = structural.attributeClassifications[0]
        assertEquals(attrId.toString(), attrSnapshot.attributeId)
        assertEquals("Email", attrSnapshot.semanticLabel)
        assertEquals(SemanticAttributeClassification.IDENTIFIER, attrSnapshot.classification)
        assertEquals(SchemaType.TEXT, attrSnapshot.schemaType)
    }

    // ------------------------------------------------------------------
    // analyzeSemantics: SENTIMENT wiring (Phase B Task 12)
    // ------------------------------------------------------------------

    /**
     * When the workspace flag is enabled and the entity type has manifest connotation signals,
     * the SENTIMENT metadata is delegated to ConnotationAnalysisService and its result persisted
     * verbatim (ANALYZED in this case) onto the connotation snapshot.
     */
    @Test
    fun `persistConnotationSnapshot populates SENTIMENT when workspace flag is on and signals exist`() {
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

        val signals = ConnotationSignals(
            tier = AnalysisTier.DETERMINISTIC,
            sentimentAttribute = "rating",
            sentimentScale = SentimentScale(
                sourceMin = 1.0,
                sourceMax = 5.0,
                targetMin = -1.0,
                targetMax = 1.0,
                mappingType = ScaleMappingType.LINEAR,
            ),
            themeAttributes = emptyList(),
        )
        val analysedMetadata = SentimentMetadata(
            sentiment = 0.75,
            analysisVersion = "tier1-v1",
            analysisTier = AnalysisTier.DETERMINISTIC,
            status = ConnotationStatus.ANALYZED,
            analyzedAt = ZonedDateTime.now(),
        )
        whenever(workspaceService.isConnotationEnabled(workspaceId)).thenReturn(true)
        whenever(manifestCatalogService.getConnotationSignalsForEntityType(typeId)).thenReturn(signals)
        whenever(
            connotationAnalysisService.analyze(
                eq(entityId),
                eq(workspaceId),
                eq(signals),
                anyOrNull(),
                any(),
            )
        ).thenReturn(analysedMetadata)

        enrichmentService.analyzeSemantics(queueItemId)

        val snapshot = captureUpsertedSnapshot(entityId, workspaceId)
        val sentiment = requireNotNull(snapshot.metadata.sentiment)
        assertEquals(ConnotationStatus.ANALYZED, sentiment.status)
        assertEquals(0.75, sentiment.sentiment)
        assertEquals(AnalysisTier.DETERMINISTIC, sentiment.analysisTier)
    }

    /**
     * When the workspace flag is off, the SENTIMENT metadata must remain NOT_APPLICABLE
     * and ConnotationAnalysisService must NOT be invoked at all.
     */
    @Test
    fun `persistConnotationSnapshot leaves SENTIMENT at NOT_APPLICABLE when workspace flag is off`() {
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
        whenever(workspaceService.isConnotationEnabled(workspaceId)).thenReturn(false)

        enrichmentService.analyzeSemantics(queueItemId)

        val snapshot = captureUpsertedSnapshot(entityId, workspaceId)
        val sentiment = requireNotNull(snapshot.metadata.sentiment)
        assertEquals(ConnotationStatus.NOT_APPLICABLE, sentiment.status)

        verify(connotationAnalysisService, never()).analyze(any(), any(), any(), anyOrNull(), any())
    }

    /**
     * Workspace flag is on but the manifest doesn't declare connotation signals for this
     * entity type (e.g. a custom user-defined type). The SENTIMENT metadata must remain
     * NOT_APPLICABLE, and ConnotationAnalysisService must not be invoked.
     */
    @Test
    fun `persistConnotationSnapshot leaves SENTIMENT at NOT_APPLICABLE when manifest has no signals`() {
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
        whenever(workspaceService.isConnotationEnabled(workspaceId)).thenReturn(true)
        whenever(manifestCatalogService.getConnotationSignalsForEntityType(typeId)).thenReturn(null)

        enrichmentService.analyzeSemantics(queueItemId)

        val snapshot = captureUpsertedSnapshot(entityId, workspaceId)
        val sentiment = requireNotNull(snapshot.metadata.sentiment)
        assertEquals(ConnotationStatus.NOT_APPLICABLE, sentiment.status)

        verify(connotationAnalysisService, never()).analyze(any(), any(), any(), anyOrNull(), any())
    }

    /**
     * When SENTIMENT is gated off, RELATIONAL and STRUCTURAL must still be populated
     * — gating the sentiment computation must not regress the deterministic categories.
     */
    @Test
    fun `persistConnotationSnapshot still writes RELATIONAL and STRUCTURAL when SENTIMENT is gated off`() {
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
        whenever(workspaceService.isConnotationEnabled(workspaceId)).thenReturn(false)

        enrichmentService.analyzeSemantics(queueItemId)

        val snapshot = captureUpsertedSnapshot(entityId, workspaceId)
        assertNotNull(snapshot.metadata.relational)
        assertNotNull(snapshot.metadata.structural)
    }

    // ------------------------------------------------------------------
    // enqueueByEntityType: bulk re-enrichment (manifest reconciliation hook)
    // ------------------------------------------------------------------

    /**
     * enqueueByEntityType delegates to the batched ExecutionQueueRepository INSERT...SELECT and
     * returns the inserted-row count. Used by SchemaReconciliationService to invalidate snapshots
     * after a manifest-driven schema change.
     */
    @Test
    fun `enqueueByEntityType returns repository insert count`() {
        val typeId = UUID.randomUUID()
        whenever(executionQueueRepository.enqueueEnrichmentByEntityType(typeId, workspaceId)).thenReturn(42)

        val inserted = enrichmentService.enqueueByEntityType(typeId, workspaceId)

        assertEquals(42, inserted)
        verify(executionQueueRepository).enqueueEnrichmentByEntityType(typeId, workspaceId)
    }

    @Test
    fun `enqueueByEntityType throws AccessDeniedException when caller lacks workspace access`() {
        val typeId = UUID.randomUUID()
        val unauthorizedWorkspaceId = UUID.randomUUID()

        assertThrows(org.springframework.security.access.AccessDeniedException::class.java) {
            enrichmentService.enqueueByEntityType(typeId, unauthorizedWorkspaceId)
        }

        verify(executionQueueRepository, never()).enqueueEnrichmentByEntityType(any(), any())
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
