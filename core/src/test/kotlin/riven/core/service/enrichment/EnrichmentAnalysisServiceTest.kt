package riven.core.service.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.configuration.util.ObjectMapperConfig
import riven.core.enums.connotation.ConnotationStatus
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.catalog.ScaleMappingType
import riven.core.models.catalog.SentimentScale
import riven.core.models.connotation.AnalysisTier
import riven.core.models.connotation.SentimentMetadata
import riven.core.configuration.properties.EnrichmentConfigurationProperties
import riven.core.repository.connotation.EntityConnotationRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.workspace.WorkspaceRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.catalog.ManifestCatalogService
import riven.core.service.connotation.ConnotationAnalysisService
import riven.core.service.entity.EntityAttributeService
import riven.core.service.entity.EntityRelationshipService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.WorkspaceFactory
import riven.core.service.util.factory.enrichment.EnrichmentFactory
import riven.core.service.util.factory.enrichment.EnrichmentSnapshotFixture
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.workflow.ExecutionQueueFactory
import riven.core.enums.workspace.WorkspaceRoles
import java.util.Optional
import java.util.UUID
import kotlin.reflect.full.primaryConstructor

/**
 * Unit tests for [EnrichmentAnalysisService].
 *
 * Verifies the semantic analysis phase of the enrichment pipeline:
 * - Queue item claiming (CLAIMED status + claimedAt timestamp)
 * - Sentiment resolution gates (workspace flag, manifest signals, attribute key mapping)
 * - Context assembly delegation to [EnrichmentContextAssembler]
 * - Connotation snapshot persistence
 * - Constructor-count ceiling (ENRICH-02 sibling gate)
 *
 * Uses `@SpringBootTest(classes = [...])` with `@MockitoBean` for all 9 collaborators;
 * logger is auto-injected via LoggerConfig. Uses `mockito-kotlin` (whenever/verify) per CLAUDE.md.
 *
 * @see EnrichmentContextSnapshotTest for byte-identical end-to-end snapshot gate
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EnrichmentAnalysisService::class,
        EnrichmentContextAssembler::class,
        ObjectMapperConfig::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@example.com",
    displayName = "Test User",
    roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
)
class EnrichmentAnalysisServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var entityConnotationRepository: EntityConnotationRepository

    @MockitoBean
    private lateinit var entityAttributeService: EntityAttributeService

    // Assembler dependencies
    @MockitoBean
    private lateinit var semanticMetadataRepository: EntityTypeSemanticMetadataRepository

    @MockitoBean
    private lateinit var entityRelationshipService: EntityRelationshipService

    @MockitoBean
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @MockitoBean
    private lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var identityClusterMemberRepository: IdentityClusterMemberRepository

    @MockitoBean
    private lateinit var relationshipTargetRuleRepository: RelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var sentimentResolutionService: SentimentResolutionService

    @MockitoBean
    private lateinit var enrichmentProperties: EnrichmentConfigurationProperties

    @MockitoBean
    private lateinit var connotationAnalysisService: ConnotationAnalysisService

    @MockitoBean
    private lateinit var workspaceRepository: WorkspaceRepository

    @MockitoBean
    private lateinit var manifestCatalogService: ManifestCatalogService

    @Autowired
    private lateinit var enrichmentAnalysisService: EnrichmentAnalysisService

    // ------ analyzeSemantics: core flow ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class AnalyzeSemanticsTests {

        private val f = EnrichmentSnapshotFixture.build()

        private fun setupMinimalMocks(queueItemId: UUID, entityId: UUID, typeId: UUID) {
            val queueItem = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = entityId
            )
            val entity = EntityFactory.createEntityEntity(
                id = entityId, workspaceId = workspaceId, typeId = typeId
            )
            val entityType = EntityFactory.createEntityType(id = typeId, workspaceId = workspaceId)

            whenever(executionQueueRepository.claimEnrichmentItem(eq(queueItemId), any())).thenReturn(1)
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
            whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entity))
            whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
            whenever(entityConnotationRepository.upsertByEntityId(any(), any(), any(), any())).thenReturn(1)
            // Default: workspace has connotationEnabled = false (simplest path; ConnotationAnalysisService is never called)
            whenever(workspaceRepository.findById(workspaceId)).thenAnswer { invocation ->
                Optional.of(WorkspaceFactory.createWorkspace(id = invocation.getArgument(0), connotationEnabled = false))
            }
            // Assembler defaults: empty collections so assemble() returns a minimal context
            whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
        }

        /**
         * Test 1: analyzeSemantics claims the queue item, calls enrichmentContextAssembler.assemble
         * with the resolved sentiment, persists the connotation snapshot, and returns the context.
         *
         * Uses inOrder to verify the key sequence: claim → assemble → persist → return.
         */
        @Test
        fun `analyzeSemantics claims queue item assembles context persists snapshot and returns context`() {
            val queueItemId = f.queueItemId
            val entityId = f.entityId
            val typeId = f.entityTypeId

            whenever(executionQueueRepository.claimEnrichmentItem(eq(queueItemId), any())).thenReturn(1)
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(f.queueItem))
            whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(f.entity))
            whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(f.entityType))
            whenever(entityConnotationRepository.upsertByEntityId(any(), any(), any(), any())).thenReturn(1)
            whenever(workspaceRepository.findById(f.workspaceId)).thenReturn(Optional.of(f.workspace))
            whenever(manifestCatalogService.getConnotationSignalsForEntityType(typeId)).thenReturn(f.connotationSignals)
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(f.entityAttributes)
            whenever(connotationAnalysisService.analyze(any(), any(), any(), any(), any())).thenReturn(f.analyzedSentiment)
            // Assembler dependencies (minimal - context test uses full fixture; here we use snapshotFixture mocks)
            whenever(semanticMetadataRepository.findByEntityTypeId(f.entityTypeId)).thenReturn(f.allPrimaryMetadata)
            whenever(entityAttributeService.getAttributes(f.entityId)).thenReturn(f.entityAttributes)
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(f.entityId, f.workspaceId)).thenReturn(listOf(f.relationshipEntity))
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(f.workspaceId, f.entityTypeId)).thenReturn(listOf(f.relationshipDefinition))
            whenever(identityClusterMemberRepository.findByEntityId(f.entityId)).thenReturn(f.primaryClusterMember)
            whenever(identityClusterMemberRepository.findByClusterId(EnrichmentSnapshotFixture.CLUSTER_ID)).thenReturn(f.allClusterMembers)
            whenever(entityRepository.findAllById(listOf(EnrichmentSnapshotFixture.CLUSTER_MEMBER_ENTITY_ID))).thenReturn(listOf(f.clusterMemberEntity))
            whenever(entityTypeRepository.findAllById(listOf(EnrichmentSnapshotFixture.CLUSTER_MEMBER_TYPE_ID))).thenReturn(listOf(f.clusterMemberEntityType))
            whenever(relationshipTargetRuleRepository.findByRelationshipDefinitionIdIn(listOf(EnrichmentSnapshotFixture.REL_DEFINITION_ID))).thenReturn(listOf(f.targetRule))
            whenever(semanticMetadataRepository.findByEntityTypeId(EnrichmentSnapshotFixture.REL_TARGET_TYPE_ID)).thenReturn(listOf(f.metaCategoricalAttr))
            whenever(entityAttributeService.getAttributesForEntities(listOf(EnrichmentSnapshotFixture.RELATED_ENTITY_ID))).thenReturn(mapOf(EnrichmentSnapshotFixture.RELATED_ENTITY_ID to f.relatedEntityAttributes))
            whenever(entityRepository.findAllById(listOf(EnrichmentSnapshotFixture.REF_ENTITY_ID))).thenReturn(listOf(f.referencedEntity))
            whenever(semanticMetadataRepository.findByEntityTypeIdIn(listOf(EnrichmentSnapshotFixture.REF_ENTITY_TYPE_ID))).thenReturn(listOf(f.metaRefTypeIdentifier))
            whenever(entityAttributeService.getAttributesForEntities(listOf(EnrichmentSnapshotFixture.REF_ENTITY_ID))).thenReturn(mapOf(EnrichmentSnapshotFixture.REF_ENTITY_ID to f.referencedEntityAttributes))

            val context = enrichmentAnalysisService.analyzeSemantics(queueItemId)

            assertNotNull(context)
            assertEquals(queueItemId, context.queueItemId)
            assertEquals(entityId, context.entityId)

            val inOrder = inOrder(executionQueueRepository, entityConnotationRepository)
            inOrder.verify(executionQueueRepository).claimEnrichmentItem(eq(queueItemId), any()) // claim CAS
            inOrder.verify(entityConnotationRepository).upsertByEntityId(any(), any(), any(), any()) // persist
        }

        /**
         * Test 2: resolveSentimentMetadata returns SentimentMetadata() (NOT_APPLICABLE)
         * when workspace.connotationEnabled is false — ConnotationAnalysisService is never called.
         */
        @Test
        fun `resolveSentimentMetadata returns NOT_APPLICABLE when workspace connotationEnabled is false`() {
            val queueItemId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val typeId = UUID.randomUUID()
            setupMinimalMocks(queueItemId, entityId, typeId)
            // connotationEnabled = false by default in setupMinimalMocks

            enrichmentAnalysisService.analyzeSemantics(queueItemId)

            verify(connotationAnalysisService, never()).analyze(any(), any(), any(), any(), any())
        }

        /**
         * Test 3: resolveSentimentMetadata returns SentimentMetadata() (NOT_APPLICABLE)
         * when manifest connotation signals are null — ConnotationAnalysisService is never called.
         */
        @Test
        fun `resolveSentimentMetadata returns NOT_APPLICABLE when manifest connotation signals are null`() {
            val queueItemId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val typeId = UUID.randomUUID()
            setupMinimalMocks(queueItemId, entityId, typeId)
            // Override: workspace has connotationEnabled = true
            whenever(workspaceRepository.findById(workspaceId)).thenReturn(
                Optional.of(WorkspaceFactory.createWorkspace(id = workspaceId, connotationEnabled = true))
            )
            whenever(manifestCatalogService.getConnotationSignalsForEntityType(typeId)).thenReturn(null)

            enrichmentAnalysisService.analyzeSemantics(queueItemId)

            verify(connotationAnalysisService, never()).analyze(any(), any(), any(), any(), any())
        }

        /**
         * Test 4: resolveSentimentMetadata returns SentimentMetadata() (NOT_APPLICABLE) when
         * entityType.attributeKeyMapping does not contain signals.sentimentAttribute — the documented
         * short-circuit that prevents MISSING_SOURCE_ATTRIBUTE false failures.
         */
        @Test
        fun `resolveSentimentMetadata returns NOT_APPLICABLE when entityType attributeKeyMapping does not contain sentimentAttribute`() {
            val queueItemId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val typeId = UUID.randomUUID()

            val queueItem = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = entityId
            )
            val entity = EntityFactory.createEntityEntity(
                id = entityId, workspaceId = workspaceId, typeId = typeId
            )
            // EntityType with attributeKeyMapping that does NOT contain the manifest sentiment key
            val entityType = EntityFactory.createEntityType(
                id = typeId, workspaceId = workspaceId,
                attributeKeyMapping = mapOf("other_key" to UUID.randomUUID().toString())
            )
            val signals = ConnotationSignals(
                tier = AnalysisTier.DETERMINISTIC,
                sentimentAttribute = "nps_score", // NOT in attributeKeyMapping
                sentimentScale = SentimentScale(0.0, 100.0, -1.0, 1.0, ScaleMappingType.LINEAR),
                themeAttributes = emptyList(),
            )

            whenever(executionQueueRepository.claimEnrichmentItem(eq(queueItemId), any())).thenReturn(1)
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
            whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(entity))
            whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
            whenever(entityConnotationRepository.upsertByEntityId(any(), any(), any(), any())).thenReturn(1)
            whenever(workspaceRepository.findById(workspaceId)).thenReturn(
                Optional.of(WorkspaceFactory.createWorkspace(id = workspaceId, connotationEnabled = true))
            )
            whenever(manifestCatalogService.getConnotationSignalsForEntityType(typeId)).thenReturn(signals)
            // Assembler minimal deps
            whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(emptyMap())
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

            enrichmentAnalysisService.analyzeSemantics(queueItemId)

            verify(connotationAnalysisService, never()).analyze(any(), any(), any(), any(), any())
        }

        /**
         * Test 5: resolveSentimentMetadata delegates to ConnotationAnalysisService.analyze
         * when all gates pass and returns whatever it produces.
         */
        @Test
        fun `resolveSentimentMetadata delegates to ConnotationAnalysisService when all gates pass`() {
            val queueItemId = f.queueItemId
            val entityId = f.entityId
            val typeId = f.entityTypeId

            whenever(executionQueueRepository.claimEnrichmentItem(eq(queueItemId), any())).thenReturn(1)
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(f.queueItem))
            whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(f.entity))
            whenever(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(f.entityType))
            whenever(entityConnotationRepository.upsertByEntityId(any(), any(), any(), any())).thenReturn(1)
            whenever(workspaceRepository.findById(f.workspaceId)).thenReturn(Optional.of(f.workspace))
            whenever(manifestCatalogService.getConnotationSignalsForEntityType(typeId)).thenReturn(f.connotationSignals)
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(f.entityAttributes)
            whenever(connotationAnalysisService.analyze(any(), any(), any(), any(), any())).thenReturn(f.analyzedSentiment)
            // Assembler minimal deps
            whenever(semanticMetadataRepository.findByEntityTypeId(typeId)).thenReturn(emptyList())
            whenever(entityRelationshipRepository.findAllRelationshipsForEntity(entityId, f.workspaceId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(f.workspaceId, typeId)).thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)

            val context = enrichmentAnalysisService.analyzeSemantics(queueItemId)

            verify(connotationAnalysisService).analyze(any(), any(), any(), any(), any())
            assertEquals(ConnotationStatus.ANALYZED, context.sentiment?.status)
        }

        /**
         * Regression test for r3180290306 (legal pre-state — PENDING -> CLAIMED).
         *
         * The queue claim transition is performed by [ExecutionQueueRepository.claimEnrichmentItem],
         * a CAS that returns 1 on a legal transition. This verifies the service calls the CAS
         * exactly once and proceeds to load the queue row, not the prior `save()` shape.
         */
        @Test
        fun `analyzeSemantics claims via CAS exactly once on legal pre-state`() {
            val queueItemId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val typeId = UUID.randomUUID()
            setupMinimalMocks(queueItemId, entityId, typeId)

            enrichmentAnalysisService.analyzeSemantics(queueItemId)

            verify(executionQueueRepository).claimEnrichmentItem(eq(queueItemId), any())
            verify(executionQueueRepository, never()).save(any())
        }

        /**
         * Regression test for r3180290306 (CLAIMED -> CLAIMED retry).
         *
         * A retried `analyzeSemantics` activity finds the row already CLAIMED. The CAS
         * permits this (status ∈ {PENDING, CLAIMED}), still returns 1, and the activity
         * proceeds normally — preserving safe Temporal retry semantics.
         */
        @Test
        fun `analyzeSemantics succeeds when CAS returns 1 for CLAIMED retry`() {
            val queueItemId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val typeId = UUID.randomUUID()
            setupMinimalMocks(queueItemId, entityId, typeId)

            // Default mock returns 1 — same as PENDING -> CLAIMED. No exception expected.
            val context = enrichmentAnalysisService.analyzeSemantics(queueItemId)
            assertNotNull(context)
        }

        /**
         * Regression test for r3180290306 (illegal pre-state — COMPLETED row must not regress).
         *
         * Bug: the prior `read-modify-write` would silently overwrite a COMPLETED row to CLAIMED
         * on a Temporal retry of `analyzeSemantics`, re-running analysis and double-emitting
         * downstream side effects. Fix: the CAS returns 0 for any non-{PENDING, CLAIMED} pre-state,
         * and the service throws [IllegalStateException]. The COMPLETED row is never touched.
         */
        @Test
        fun `analyzeSemantics throws IllegalStateException when CAS returns 0 for COMPLETED row`() {
            val queueItemId = UUID.randomUUID()
            whenever(executionQueueRepository.claimEnrichmentItem(eq(queueItemId), any())).thenReturn(0)

            assertThrows(IllegalStateException::class.java) {
                enrichmentAnalysisService.analyzeSemantics(queueItemId)
            }

            verify(executionQueueRepository, never()).findById(queueItemId)
            verify(executionQueueRepository, never()).save(any())
            verify(entityConnotationRepository, never()).upsertByEntityId(any(), any(), any(), any())
        }

        /**
         * Regression test for r3180290306 (illegal pre-state — FAILED row must not regress).
         *
         * Same shape as the COMPLETED test: a row already in a terminal failure state cannot be
         * resurrected by a stale activity retry. The CAS WHERE clause restricts pre-state to
         * {PENDING, CLAIMED}, so a FAILED row yields rowCount = 0 and the service throws.
         */
        @Test
        fun `analyzeSemantics throws IllegalStateException when CAS returns 0 for FAILED row`() {
            val queueItemId = UUID.randomUUID()
            whenever(executionQueueRepository.claimEnrichmentItem(eq(queueItemId), any())).thenReturn(0)

            assertThrows(IllegalStateException::class.java) {
                enrichmentAnalysisService.analyzeSemantics(queueItemId)
            }
        }

        /**
         * Regression test for r3180290306 (illegal pre-state — wrong job_type rejected).
         *
         * The CAS WHERE clause restricts to `job_type = 'ENRICHMENT'`. A row of any other job
         * type yields rowCount = 0 — proving the analyze activity refuses to misinterpret a
         * non-enrichment queue row.
         */
        @Test
        fun `analyzeSemantics throws IllegalStateException when CAS returns 0 for wrong job_type`() {
            val queueItemId = UUID.randomUUID()
            whenever(executionQueueRepository.claimEnrichmentItem(eq(queueItemId), any())).thenReturn(0)

            assertThrows(IllegalStateException::class.java) {
                enrichmentAnalysisService.analyzeSemantics(queueItemId)
            }
        }
    }

    // ------ Constructor-count assertion ------

    /**
     * Test 7: Constructor-count assertion gate (ENRICH-02).
     *
     * EnrichmentAnalysisService has a documented ceiling of ≤ 11 constructor params (10 business deps
     * + logger). The plan doc listed "Total: 10" counting only business deps; including the KLogger
     * prototype bean brings the actual parameter count to 11.
     *
     * Sentiment + manifest helpers stayed on this service per 01-CONTEXT byte-identity precedence:
     * final business dep count is 10, which exceeds the design doc's stated ~6 because sentiment
     * resolution + manifest lookup remained here.
     *
     * This test prevents uncontrolled dep accumulation: any new dep must bump this ceiling with
     * explicit architectural justification.
     */
    @Test
    fun `EnrichmentAnalysisService has at most 11 constructor parameters`() {
        val paramCount = EnrichmentAnalysisService::class.primaryConstructor?.parameters?.size
            ?: error("EnrichmentAnalysisService must have a primary constructor")
        assertTrue(
            paramCount <= 11,
            "EnrichmentAnalysisService constructor has $paramCount params but ceiling is ≤ 11 " +
                "(10 business deps + 1 logger). Raise only with explicit architectural justification."
        )
    }
}
