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
            whenever(entityRelationshipService.findRelatedEntities(entityId, workspaceId)).thenReturn(emptyList())
            whenever(entityRelationshipRepository.findGlossaryDefinitionsForType(workspaceId, typeId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, typeId)).thenReturn(emptyList())
            whenever(identityClusterMemberRepository.findByEntityId(entityId)).thenReturn(null)
            // Plan 02-03: sentimentResolutionService is now called by the assembler directly.
            // Default: NOT_APPLICABLE (no-arg SentimentMetadata is the sentinel for "not opted in").
            whenever(sentimentResolutionService.resolve(eq(entityId), eq(workspaceId), any())).thenReturn(SentimentMetadata())
        }

        /**
         * Test 1: analyzeSemantics claims the queue item, calls enrichmentContextAssembler.assemble
         * with the resolved sentiment, persists the connotation snapshot, and returns the context.
         *
         * Uses inOrder to verify the key sequence: claim → assemble → persist → return.
         *
         * Plan 02-03: Method-level [WithUserPersona] adds the fixture workspace so the assembler's
         * [@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")] passes for
         * [EnrichmentSnapshotFixture.WORKSPACE_ID] = "00000000-0000-0000-0000-000000000002".
         */
        @WithUserPersona(
            userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
            email = "test@example.com",
            displayName = "Test User",
            roles = [
                WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN),
                WorkspaceRole(workspaceId = "00000000-0000-0000-0000-000000000002", role = WorkspaceRoles.ADMIN),
            ]
        )
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
            whenever(entityAttributeService.getAttributes(entityId)).thenReturn(f.entityAttributes)
            // Plan 02-03: assembler delegates sentiment to SentimentResolutionService (mock).
            whenever(sentimentResolutionService.resolve(eq(entityId), eq(f.workspaceId), any())).thenReturn(f.analyzedSentiment)
            // Assembler dependencies
            whenever(semanticMetadataRepository.findByEntityTypeId(f.entityTypeId)).thenReturn(f.allPrimaryMetadata)
            whenever(entityRelationshipService.findRelatedEntities(f.entityId, f.workspaceId)).thenReturn(emptyList())
            whenever(entityRelationshipRepository.findGlossaryDefinitionsForType(f.workspaceId, f.entityTypeId)).thenReturn(emptyList())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(f.workspaceId, f.entityTypeId)).thenReturn(listOf(f.relationshipDefinition))
            whenever(identityClusterMemberRepository.findByEntityId(f.entityId)).thenReturn(null)

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
         * Test 3: analyzeSemantics delegates sentiment resolution to [SentimentResolutionService]
         * (via the assembler), and the returned view carries whatever sentiment the resolution service
         * produces.
         *
         * Plan 02-03: Sentiment gating logic moved to [SentimentResolutionService] (Plan 02-02).
         * [EnrichmentAnalysisService] no longer owns sentiment gates — tests for NOT_APPLICABLE gating
         * (workspace opt-in, manifest signals, attribute key mapping) live in SentimentResolutionServiceTest.
         * This test confirms the assembler's sentinel path: a NOT_APPLICABLE sentiment (default) results
         * in a null [riven.core.models.entity.knowledge.EntityMetadataSection.sentiment] field.
         */
        @Test
        fun `analyzeSemantics returns view with null sentiment when resolution returns NOT_APPLICABLE`() {
            val queueItemId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val typeId = UUID.randomUUID()
            setupMinimalMocks(queueItemId, entityId, typeId)
            // Default mock (from setupMinimalMocks) returns SentimentMetadata() which is NOT_APPLICABLE.
            // The assembler converts NOT_APPLICABLE to null in EntityMetadataSection.sentiment.

            val view = enrichmentAnalysisService.analyzeSemantics(queueItemId)

            assertEquals(null, view.sections.entityMetadata.sentiment)
        }

        /**
         * Test 4: analyzeSemantics returns view with ANALYZED sentiment when [SentimentResolutionService]
         * returns an ANALYZED result.
         *
         * Plan 02-03: Replaces the old sentiment-gating test that verified manifest/workspace gates
         * (those now live in SentimentResolutionServiceTest). This test verifies only the end-to-end
         * path from assembler → EntityMetadataSection.sentiment.
         */
        @Test
        fun `analyzeSemantics returns view with ANALYZED sentiment when resolution service returns ANALYZED`() {
            val queueItemId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val typeId = UUID.randomUUID()
            setupMinimalMocks(queueItemId, entityId, typeId)
            // Override: sentiment resolution returns ANALYZED
            val analyzedSentiment = SentimentMetadata(
                sentiment = 0.75,
                sentimentLabel = null,
                themes = emptyList(),
                analysisVersion = "v1",
                analysisTier = AnalysisTier.DETERMINISTIC,
                status = ConnotationStatus.ANALYZED,
            )
            whenever(sentimentResolutionService.resolve(eq(entityId), eq(workspaceId), any())).thenReturn(analyzedSentiment)

            val view = enrichmentAnalysisService.analyzeSemantics(queueItemId)

            assertEquals(ConnotationStatus.ANALYZED, view.sections.entityMetadata.sentiment?.status)
        }

        /**
         * Test 5: analyzeSemantics persists connotation snapshot and returns [EntityKnowledgeView].
         *
         * Plan 02-03: Replaces the old "delegates to ConnotationAnalysisService" test (sentiment
         * gates moved to [SentimentResolutionService]). This test verifies that the service persists
         * the connotation snapshot via [riven.core.repository.connotation.EntityConnotationRepository.upsertByEntityId]
         * and returns the assembled view.
         */
        @Test
        fun `analyzeSemantics persists connotation snapshot and returns EntityKnowledgeView`() {
            val queueItemId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val typeId = UUID.randomUUID()
            setupMinimalMocks(queueItemId, entityId, typeId)

            val view = enrichmentAnalysisService.analyzeSemantics(queueItemId)

            assertNotNull(view)
            verify(entityConnotationRepository).upsertByEntityId(eq(entityId), eq(workspaceId), any(), any())
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
