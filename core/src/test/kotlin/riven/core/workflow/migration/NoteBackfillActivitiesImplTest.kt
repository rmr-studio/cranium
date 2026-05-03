package riven.core.workflow.migration

import io.github.oshai.kotlinlogging.KLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import riven.core.enums.integration.SourceType
import riven.core.enums.note.NoteSourceType
import riven.core.repository.note.NoteEntityAttachmentRepository
import riven.core.repository.note.NoteRepository
import riven.core.service.note.NoteEntityIngestionService
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.note.NoteFactory
import java.util.Optional
import java.util.UUID

/**
 * Unit-level coverage of the note backfill activity. Verifies the idempotency
 * contract documented on [NoteBackfillActivitiesImpl] without spinning up a
 * Temporal test environment:
 *
 *   - migrateBatch upserts via [NoteEntityIngestionService] for each input id;
 *   - duplicate-key violations from the ingestion path are reported as
 *     `skipped`, not `failed`;
 *   - a second migrateBatch over the same ids reports zero `migrated` and
 *     N `skipped` when the ingestion service raises duplicate-key violations
 *     on every retry.
 */
class NoteBackfillActivitiesImplTest {

    private val workspaceId: UUID = UUID.randomUUID()
    private val noteRepository: NoteRepository = mock()
    private val noteAttachmentRepository: NoteEntityAttachmentRepository = mock()
    private val noteEntityIngestionService: NoteEntityIngestionService = mock()
    private val transactionTemplate: TransactionTemplate = mock<TransactionTemplate>().also {
        whenever(it.execute<Any?>(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
    }
    private val logger: KLogger = mock()

    private val activities = NoteBackfillActivitiesImpl(
        noteRepository, noteAttachmentRepository, noteEntityIngestionService, transactionTemplate, logger,
    )

    @Test
    fun `migrateBatch upserts a NoteIngestionInput for each note`() {
        val noteId = UUID.randomUUID()
        val target = UUID.randomUUID()
        val legacy = NoteFactory.createEntity(
            id = noteId,
            workspaceId = workspaceId,
            title = "Hello",
            plaintext = "Hello",
            sourceType = NoteSourceType.USER,
        )
        whenever(noteRepository.findById(noteId)).thenReturn(Optional.of(legacy))
        whenever(noteAttachmentRepository.findEntityIdsByNoteId(noteId)).thenReturn(listOf(target))
        whenever(noteEntityIngestionService.upsert(any())).thenReturn(
            EntityFactory.createEntityEntity(id = UUID.randomUUID(), workspaceId = workspaceId, typeKey = "note"),
        )

        val result = activities.migrateBatch(workspaceId, listOf(noteId))

        assertThat(result.migrated).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.failed).isEqualTo(0)

        val captor = argumentCaptor<NoteEntityIngestionService.NoteIngestionInput>()
        verify(noteEntityIngestionService).upsert(captor.capture())
        val input = captor.firstValue
        assertThat(input.workspaceId).isEqualTo(workspaceId)
        assertThat(input.title).isEqualTo("Hello")
        assertThat(input.plaintext).isEqualTo("Hello")
        assertThat(input.sourceType).isEqualTo(SourceType.USER_CREATED)
        assertThat(input.sourceExternalId).isEqualTo("legacy:$noteId")
        assertThat(input.targetEntityIds).containsExactly(target)
    }

    @Test
    fun `migrateBatch is idempotent — duplicate-key violations report skipped, not failed`() {
        val noteId = UUID.randomUUID()
        val legacy = NoteFactory.createEntity(id = noteId, workspaceId = workspaceId)
        whenever(noteRepository.findById(noteId)).thenReturn(Optional.of(legacy))
        whenever(noteAttachmentRepository.findEntityIdsByNoteId(noteId)).thenReturn(emptyList())
        whenever(noteEntityIngestionService.upsert(any()))
            .thenThrow(DataIntegrityViolationException("duplicate key", java.sql.SQLException("duplicate", "23505")))

        val result = activities.migrateBatch(workspaceId, listOf(noteId))

        assertThat(result.migrated).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.failed).isEqualTo(0)
    }

    /**
     * Regression for r3176253163: pairs with the 23505-skipped test above to lock down the
     * `isUniqueViolation` branching. A non-unique DataIntegrityViolationException (e.g. FK
     * violation 23503) must be classified as `failed`, not silently skipped.
     */
    @Test
    fun `migrateBatch — non-unique DataIntegrityViolation (FK 23503) reports failed, not skipped`() {
        val noteId = UUID.randomUUID()
        val legacy = NoteFactory.createEntity(id = noteId, workspaceId = workspaceId)
        whenever(noteRepository.findById(noteId)).thenReturn(Optional.of(legacy))
        whenever(noteAttachmentRepository.findEntityIdsByNoteId(noteId)).thenReturn(emptyList())
        whenever(noteEntityIngestionService.upsert(any()))
            .thenThrow(DataIntegrityViolationException("fk violation", java.sql.SQLException("fk", "23503")))

        val result = activities.migrateBatch(workspaceId, listOf(noteId))

        assertThat(result.failed).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.migrated).isEqualTo(0)
    }

    @Test
    fun `migrateBatch — second call over already-migrated ids reports skipped count matching input`() {
        val noteIds = (1..3).map { UUID.randomUUID() }
        for (id in noteIds) {
            whenever(noteRepository.findById(id))
                .thenReturn(Optional.of(NoteFactory.createEntity(id = id, workspaceId = workspaceId)))
            whenever(noteAttachmentRepository.findEntityIdsByNoteId(id)).thenReturn(emptyList())
        }
        // First migrateBatch call succeeds for every id; second call simulates the
        // already-migrated-on-restart case where the entity-side unique constraint
        // rejects every upsert with a duplicate-key violation.
        val savedEntity = EntityFactory.createEntityEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId,
        )
        whenever(noteEntityIngestionService.upsert(any()))
            .thenReturn(savedEntity)
            .thenReturn(savedEntity)
            .thenReturn(savedEntity)
            .thenThrow(DataIntegrityViolationException("duplicate", java.sql.SQLException("duplicate", "23505")))

        val first = activities.migrateBatch(workspaceId, noteIds)

        assertThat(first.migrated).isEqualTo(noteIds.size)
        assertThat(first.skipped).isEqualTo(0)
        assertThat(first.failed).isEqualTo(0)

        val second = activities.migrateBatch(workspaceId, noteIds)

        assertThat(second.skipped).isEqualTo(noteIds.size)
        assertThat(second.migrated).isEqualTo(0)
        assertThat(second.failed).isEqualTo(0)
    }

    @Test
    fun `migrateBatch — missing legacy row is skipped, ingestion service not called`() {
        val noteId = UUID.randomUUID()
        whenever(noteRepository.findById(noteId)).thenReturn(Optional.empty())

        val result = activities.migrateBatch(workspaceId, listOf(noteId))

        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.migrated).isEqualTo(0)
        verify(noteEntityIngestionService, never()).upsert(any())
    }

    @Test
    fun `migrateBatch — unexpected exception increments failed`() {
        val noteId = UUID.randomUUID()
        val legacy = NoteFactory.createEntity(id = noteId, workspaceId = workspaceId)
        whenever(noteRepository.findById(noteId)).thenReturn(Optional.of(legacy))
        whenever(noteAttachmentRepository.findEntityIdsByNoteId(noteId)).thenReturn(emptyList())
        whenever(noteEntityIngestionService.upsert(any())).thenThrow(IllegalStateException("boom"))

        val result = activities.migrateBatch(workspaceId, listOf(noteId))

        assertThat(result.failed).isEqualTo(1)
        assertThat(result.migrated).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(0)
    }

    @Test
    fun `migrateBatch — sourceExternalId carried through when present`() {
        val noteId = UUID.randomUUID()
        val externalId = "hubspot-123"
        val integrationId = UUID.randomUUID()
        val legacy = NoteFactory.createEntity(
            id = noteId,
            workspaceId = workspaceId,
            sourceType = NoteSourceType.INTEGRATION,
            sourceIntegrationId = integrationId,
            sourceExternalId = externalId,
        )
        whenever(noteRepository.findById(noteId)).thenReturn(Optional.of(legacy))
        whenever(noteAttachmentRepository.findEntityIdsByNoteId(noteId)).thenReturn(emptyList())
        whenever(noteEntityIngestionService.upsert(any())).thenReturn(
            EntityFactory.createEntityEntity(id = UUID.randomUUID(), workspaceId = workspaceId, typeKey = "note"),
        )

        activities.migrateBatch(workspaceId, listOf(noteId))

        val captor = argumentCaptor<NoteEntityIngestionService.NoteIngestionInput>()
        verify(noteEntityIngestionService, times(1)).upsert(captor.capture())
        assertThat(captor.firstValue.sourceExternalId).isEqualTo(externalId)
        assertThat(captor.firstValue.sourceIntegrationId).isEqualTo(integrationId)
        assertThat(captor.firstValue.sourceType).isEqualTo(SourceType.INTEGRATION)
        assertThat(captor.firstValue.linkSource).isEqualTo(SourceType.INTEGRATION)
    }
}

