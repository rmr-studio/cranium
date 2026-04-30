package riven.core.workflow.migration

import io.github.oshai.kotlinlogging.KLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionSource
import riven.core.models.knowledge.AttributeRef
import riven.core.repository.knowledge.WorkspaceBusinessDefinitionRepository
import riven.core.service.knowledge.GlossaryEntityIngestionService
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.knowledge.BusinessDefinitionFactory
import java.util.Optional
import java.util.UUID

/**
 * Unit-level coverage of the glossary backfill activity. Verifies the idempotency
 * contract documented on [GlossaryBackfillActivitiesImpl] without spinning up a
 * Temporal test environment:
 *
 *   - migrateBatch upserts via [GlossaryEntityIngestionService] for each input id;
 *   - the source's entityTypeRefs and attributeRefs flow through into the
 *     ingestion-service input verbatim;
 *   - duplicate-key violations from the ingestion path are reported as `skipped`,
 *     not `failed`;
 *   - a second migrateBatch over the same ids reports zero `migrated` and N
 *     `skipped` when the ingestion service raises duplicate-key violations on every
 *     retry.
 */
class GlossaryBackfillActivitiesImplTest {

    private val workspaceId: UUID = UUID.randomUUID()
    private val definitionRepository: WorkspaceBusinessDefinitionRepository = mock()
    private val glossaryEntityIngestionService: GlossaryEntityIngestionService = mock()
    private val transactionTemplate: TransactionTemplate = mock<TransactionTemplate>().also {
        whenever(it.execute<Any?>(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
    }
    private val logger: KLogger = mock()

    private val activities = GlossaryBackfillActivitiesImpl(
        definitionRepository, glossaryEntityIngestionService, transactionTemplate, logger,
    )

    @Test
    fun `migrateBatch upserts a GlossaryIngestionInput for each definition`() {
        val definitionId = UUID.randomUUID()
        val typeRef = UUID.randomUUID()
        val attrRef = AttributeRef(attributeId = UUID.randomUUID(), ownerEntityTypeId = typeRef)
        val legacy = BusinessDefinitionFactory.createDefinition(
            id = definitionId,
            workspaceId = workspaceId,
            term = "Retention Rate",
            normalizedTerm = "retention rate",
            definition = "A retained customer has an active subscription 90 days post-purchase.",
            category = DefinitionCategory.METRIC,
            source = DefinitionSource.MANUAL,
            entityTypeRefs = listOf(typeRef),
            attributeRefs = listOf(attrRef),
            isCustomized = true,
        )
        whenever(definitionRepository.findById(definitionId)).thenReturn(Optional.of(legacy))
        whenever(glossaryEntityIngestionService.upsert(any())).thenReturn(
            EntityFactory.createEntityEntity(id = UUID.randomUUID(), workspaceId = workspaceId, typeKey = "glossary"),
        )

        val result = activities.migrateBatch(workspaceId, listOf(definitionId))

        assertThat(result.migrated).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.failed).isEqualTo(0)

        val captor = argumentCaptor<GlossaryEntityIngestionService.GlossaryIngestionInput>()
        verify(glossaryEntityIngestionService).upsert(captor.capture())
        val input = captor.firstValue
        assertThat(input.workspaceId).isEqualTo(workspaceId)
        assertThat(input.term).isEqualTo("Retention Rate")
        assertThat(input.normalizedTerm).isEqualTo("retention rate")
        assertThat(input.definition).contains("subscription 90 days")
        assertThat(input.category).isEqualTo("METRIC")
        assertThat(input.source).isEqualTo("MANUAL")
        assertThat(input.isCustomised).isTrue()
        assertThat(input.sourceExternalId).isEqualTo("legacy:$definitionId")
        assertThat(input.entityTypeRefs).containsExactly(typeRef)
        assertThat(input.attributeRefs).containsExactly(attrRef)
    }

    @Test
    fun `migrateBatch is idempotent — duplicate-key violations report skipped, not failed`() {
        val definitionId = UUID.randomUUID()
        val legacy = BusinessDefinitionFactory.createDefinition(id = definitionId, workspaceId = workspaceId)
        whenever(definitionRepository.findById(definitionId)).thenReturn(Optional.of(legacy))
        whenever(glossaryEntityIngestionService.upsert(any()))
            .thenThrow(DataIntegrityViolationException("duplicate key"))

        val result = activities.migrateBatch(workspaceId, listOf(definitionId))

        assertThat(result.migrated).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.failed).isEqualTo(0)
    }

    @Test
    fun `migrateBatch — second call over already-migrated ids reports skipped count matching input`() {
        val ids = (1..3).map { UUID.randomUUID() }
        for (id in ids) {
            whenever(definitionRepository.findById(id))
                .thenReturn(Optional.of(BusinessDefinitionFactory.createDefinition(id = id, workspaceId = workspaceId)))
        }
        whenever(glossaryEntityIngestionService.upsert(any()))
            .thenThrow(DataIntegrityViolationException("duplicate"))

        val result = activities.migrateBatch(workspaceId, ids)

        assertThat(result.skipped).isEqualTo(ids.size)
        assertThat(result.migrated).isEqualTo(0)
        assertThat(result.failed).isEqualTo(0)
    }

    @Test
    fun `migrateBatch — missing legacy row is skipped, ingestion service not called`() {
        val definitionId = UUID.randomUUID()
        whenever(definitionRepository.findById(definitionId)).thenReturn(Optional.empty())

        val result = activities.migrateBatch(workspaceId, listOf(definitionId))

        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.migrated).isEqualTo(0)
        verify(glossaryEntityIngestionService, never()).upsert(any())
    }

    @Test
    fun `migrateBatch — unexpected exception increments failed`() {
        val definitionId = UUID.randomUUID()
        val legacy = BusinessDefinitionFactory.createDefinition(id = definitionId, workspaceId = workspaceId)
        whenever(definitionRepository.findById(definitionId)).thenReturn(Optional.of(legacy))
        whenever(glossaryEntityIngestionService.upsert(any())).thenThrow(IllegalStateException("boom"))

        val result = activities.migrateBatch(workspaceId, listOf(definitionId))

        assertThat(result.failed).isEqualTo(1)
        assertThat(result.migrated).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(0)
    }
}
