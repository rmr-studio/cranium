package riven.core.service.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.configuration.properties.EnrichmentConfigurationProperties
import riven.core.entity.enrichment.EntityEmbeddingEntity
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.enums.workflow.ExecutionQueueStatus
import riven.core.models.enrichment.EnrichedTextResult
import riven.core.models.enrichment.EnrichmentContext
import riven.core.repository.enrichment.EntityEmbeddingRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.enrichment.provider.EmbeddingProvider
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.EnrichmentFactory
import riven.core.service.util.factory.enrichment.EnrichmentFactory as EnrichmentModelFactory
import riven.core.service.util.factory.workflow.ExecutionQueueFactory
import java.util.Optional
import java.util.UUID
import kotlin.reflect.full.primaryConstructor

/**
 * Unit tests for [EnrichmentEmbeddingService].
 *
 * Verifies that embedAndStore correctly orchestrates text building, embedding generation,
 * upsert persistence (delete + insert), and queue item completion in transactional order.
 * Tests use mockito-kotlin (whenever/verify) per CLAUDE.md conventions.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EnrichmentEmbeddingService::class,
    ]
)
class EnrichmentEmbeddingServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var semanticTextBuilderService: SemanticTextBuilderService

    @MockitoBean
    private lateinit var embeddingProvider: EmbeddingProvider

    @MockitoBean
    private lateinit var entityEmbeddingRepository: EntityEmbeddingRepository

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var enrichmentProperties: EnrichmentConfigurationProperties

    @Autowired
    private lateinit var enrichmentEmbeddingService: EnrichmentEmbeddingService

    // ------ embedAndStore: happy path ------

    @Nested
    inner class EmbedAndStoreTests {

        private val queueItemId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        private val context: EnrichmentContext = EnrichmentFactory.createEnrichmentContext(
            queueItemId = queueItemId,
            workspaceId = workspaceId,
        )
        private val enrichedText = "## Entity Type: Customer\n\nType: Customer"
        private val enrichedTextResult = EnrichedTextResult(text = enrichedText, truncated = false, estimatedTokens = 12)
        private val embedding = FloatArray(1536) { 0.1f }
        private val modelName = "text-embedding-3-small"

        private fun setupHappyPath(queueItem: ExecutionQueueEntity) {
            whenever(enrichmentProperties.vectorDimensions).thenReturn(1536)
            whenever(semanticTextBuilderService.buildText(context)).thenReturn(enrichedTextResult)
            whenever(embeddingProvider.generateEmbedding(enrichedText)).thenReturn(embedding)
            whenever(embeddingProvider.getModelName()).thenReturn(modelName)
            whenever(entityEmbeddingRepository.save(any())).thenAnswer { it.arguments[0] }
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
            whenever(executionQueueRepository.save(any())).thenAnswer { it.arguments[0] }
        }

        /**
         * Test 1: Verifies inOrder contract: buildText → generateEmbedding → deleteByEntityId
         * → save (embedding) → findById (queue) → save (queue completed).
         */
        @Test
        fun `embedAndStore builds text generates embedding upserts and marks queue COMPLETED in order`() {
            val queueItem = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = context.entityId
            )
            setupHappyPath(queueItem)

            enrichmentEmbeddingService.embedAndStore(context, queueItemId)

            val inOrder = inOrder(semanticTextBuilderService, embeddingProvider, entityEmbeddingRepository, executionQueueRepository)
            inOrder.verify(semanticTextBuilderService).buildText(context)
            inOrder.verify(embeddingProvider).generateEmbedding(enrichedText)
            inOrder.verify(entityEmbeddingRepository).deleteByEntityId(context.entityId)
            inOrder.verify(entityEmbeddingRepository).save(any())
            inOrder.verify(executionQueueRepository).findById(queueItemId)
            inOrder.verify(executionQueueRepository).save(any())
        }

        /**
         * Test 2: Validates that an embedding of wrong size causes IllegalArgumentException
         * before any repository write occurs — guards against schema mismatch at the DB level.
         */
        @Test
        fun `embedAndStore throws IllegalArgumentException when embedding size does not match vectorDimensions`() {
            val wrongSizeEmbedding = FloatArray(768) { 0.1f }
            whenever(enrichmentProperties.vectorDimensions).thenReturn(1536)
            whenever(semanticTextBuilderService.buildText(context)).thenReturn(enrichedTextResult)
            whenever(embeddingProvider.generateEmbedding(enrichedText)).thenReturn(wrongSizeEmbedding)

            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
                enrichmentEmbeddingService.embedAndStore(context, queueItemId)
            }

            verify(entityEmbeddingRepository, never()).deleteByEntityId(any())
            verify(entityEmbeddingRepository, never()).save(any())
            verify(executionQueueRepository, never()).save(any())
        }

        /**
         * Test 3: Verifies that the truncated flag from SemanticTextBuilderService is propagated
         * into the saved EntityEmbeddingEntity. This prevents truncation metadata from being silently
         * dropped when text had to be reduced to fit the embedding budget.
         */
        @Test
        fun `embedAndStore propagates truncated flag from buildText result into EntityEmbeddingEntity`() {
            val truncatedResult = EnrichedTextResult(text = enrichedText, truncated = true, estimatedTokens = 12)
            val queueItem = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = context.entityId
            )
            whenever(enrichmentProperties.vectorDimensions).thenReturn(1536)
            whenever(semanticTextBuilderService.buildText(context)).thenReturn(truncatedResult)
            whenever(embeddingProvider.generateEmbedding(enrichedText)).thenReturn(embedding)
            whenever(embeddingProvider.getModelName()).thenReturn(modelName)
            whenever(entityEmbeddingRepository.save(any())).thenAnswer { it.arguments[0] }
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(queueItem))
            whenever(executionQueueRepository.save(any())).thenAnswer { it.arguments[0] }

            enrichmentEmbeddingService.embedAndStore(context, queueItemId)

            val captor = ArgumentCaptor.forClass(EntityEmbeddingEntity::class.java)
            verify(entityEmbeddingRepository).save(captor.capture())
            assertTrue(captor.value.truncated, "EntityEmbeddingEntity.truncated must reflect buildText result")
        }

        @Test
        fun `embedAndStore saves non-truncated flag when text was not truncated`() {
            val queueItem = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = context.entityId
            )
            setupHappyPath(queueItem)

            enrichmentEmbeddingService.embedAndStore(context, queueItemId)

            val captor = ArgumentCaptor.forClass(EntityEmbeddingEntity::class.java)
            verify(entityEmbeddingRepository).save(captor.capture())
            assertFalse(captor.value.truncated, "EntityEmbeddingEntity.truncated must be false when text was not truncated")
        }

        @Test
        fun `embedAndStore saves entity embedding with correct context metadata`() {
            val queueItem = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = context.entityId
            )
            setupHappyPath(queueItem)

            enrichmentEmbeddingService.embedAndStore(context, queueItemId)

            val captor = ArgumentCaptor.forClass(EntityEmbeddingEntity::class.java)
            verify(entityEmbeddingRepository).save(captor.capture())
            val saved = captor.value
            assertEquals(context.entityId, saved.entityId)
            assertEquals(context.workspaceId, saved.workspaceId)
            assertEquals(context.entityTypeId, saved.entityTypeId)
            assertEquals(context.schemaVersion, saved.schemaVersion)
            assertEquals(modelName, saved.embeddingModel)
        }

        @Test
        fun `embedAndStore marks queue item COMPLETED`() {
            val queueItem = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = context.entityId,
                status = ExecutionQueueStatus.CLAIMED
            )
            setupHappyPath(queueItem)

            enrichmentEmbeddingService.embedAndStore(context, queueItemId)

            val captor = ArgumentCaptor.forClass(ExecutionQueueEntity::class.java)
            verify(executionQueueRepository).save(captor.capture())
            assertEquals(ExecutionQueueStatus.COMPLETED, captor.value.status)
        }
    }

    // ------ markQueueItemFailed (r3180290311) ------

    /**
     * Regression tests for r3180290311. The workflow's primary-consumer catch arm calls
     * `EnrichmentActivities.markQueueItemFailed` which delegates here. The contract: a CLAIMED
     * (or PENDING) row transitions to FAILED with the reason recorded; rows already in a
     * terminal state (COMPLETED or FAILED) are left untouched (idempotent).
     *
     * Without this method the workflow swallowed terminal embedding failures and left the
     * queue stuck in CLAIMED with no FAILED state and no retry signal.
     */
    @Nested
    inner class MarkQueueItemFailedTests {

        private val queueItemId: UUID = UUID.fromString("ccccccc1-cccc-cccc-cccc-cccccccccccc")

        @Test
        fun `markQueueItemFailed transitions CLAIMED row to FAILED with reason`() {
            val claimed = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = UUID.randomUUID(),
                status = ExecutionQueueStatus.CLAIMED,
            )
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(claimed))
            whenever(executionQueueRepository.save(any())).thenAnswer { it.arguments[0] }

            enrichmentEmbeddingService.markQueueItemFailed(queueItemId, "embedding provider outage")

            val captor = ArgumentCaptor.forClass(ExecutionQueueEntity::class.java)
            verify(executionQueueRepository).save(captor.capture())
            assertEquals(ExecutionQueueStatus.FAILED, captor.value.status)
            assertEquals("embedding provider outage", captor.value.lastError)
        }

        @Test
        fun `markQueueItemFailed transitions PENDING row to FAILED`() {
            val pending = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = UUID.randomUUID(),
                status = ExecutionQueueStatus.PENDING,
            )
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(pending))
            whenever(executionQueueRepository.save(any())).thenAnswer { it.arguments[0] }

            enrichmentEmbeddingService.markQueueItemFailed(queueItemId, "preflight failure")

            val captor = ArgumentCaptor.forClass(ExecutionQueueEntity::class.java)
            verify(executionQueueRepository).save(captor.capture())
            assertEquals(ExecutionQueueStatus.FAILED, captor.value.status)
        }

        @Test
        fun `markQueueItemFailed is a no-op when row is already COMPLETED`() {
            val completed = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = UUID.randomUUID(),
                status = ExecutionQueueStatus.COMPLETED,
            )
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(completed))

            enrichmentEmbeddingService.markQueueItemFailed(queueItemId, "stale failure signal")

            // Idempotency: do not regress a successful completion.
            verify(executionQueueRepository, never()).save(any())
        }

        @Test
        fun `markQueueItemFailed is a no-op when row is already FAILED`() {
            val alreadyFailed = ExecutionQueueFactory.createEnrichmentJob(
                id = queueItemId, workspaceId = workspaceId, entityId = UUID.randomUUID(),
                status = ExecutionQueueStatus.FAILED,
            )
            whenever(executionQueueRepository.findById(queueItemId)).thenReturn(Optional.of(alreadyFailed))

            enrichmentEmbeddingService.markQueueItemFailed(queueItemId, "second failure attempt")

            // Preserves the original failure reason — no overwrite.
            verify(executionQueueRepository, never()).save(any())
        }
    }

    // ------ Constructor-count assertion ------

    /**
     * Test 4: Constructor-count assertion gate (ENRICH-02 sibling).
     *
     * EnrichmentEmbeddingService targets 6 constructor deps, ceiling ≤ 8.
     * This test prevents uncontrolled dep accumulation: if a new dep is added, this test
     * forces an explicit decision about whether the ceiling should be raised.
     */
    @Test
    fun `EnrichmentEmbeddingService has at most 8 constructor parameters`() {
        val paramCount = EnrichmentEmbeddingService::class.primaryConstructor?.parameters?.size
            ?: error("EnrichmentEmbeddingService must have a primary constructor")
        assertTrue(
            paramCount <= 8,
            "EnrichmentEmbeddingService constructor has $paramCount params but ceiling is ≤ 8 (target 6). " +
                "Raise the ceiling only with explicit architectural justification."
        )
    }
}
