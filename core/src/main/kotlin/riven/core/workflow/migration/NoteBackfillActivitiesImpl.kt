package riven.core.workflow.migration

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.activity.Activity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import riven.core.enums.integration.SourceType
import riven.core.enums.note.NoteSourceType
import riven.core.repository.note.NoteEntityAttachmentRepository
import riven.core.repository.note.NoteRepository
import riven.core.service.note.NoteEntityIngestionService
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Backfill activity implementation: migrates legacy `notes` rows into entity-backed
 * notes via [NoteEntityIngestionService.upsert].
 *
 * Idempotency contract:
 *   - each legacy note carries `sourceExternalId = "legacy:{noteId}"` (or its existing
 *     integration external id for integration-sourced notes);
 *   - the ingestion service's idempotent lookup keys on
 *     `(workspaceId, sourceIntegrationId, sourceExternalId)`, so re-runs over the same
 *     `noteId` set update the same entity row in place;
 *   - `DataIntegrityViolationException` from concurrent races is treated as
 *     `skipped`, not `failed`.
 */
@Component
class NoteBackfillActivitiesImpl(
    private val noteRepository: NoteRepository,
    private val noteAttachmentRepository: NoteEntityAttachmentRepository,
    private val noteEntityIngestionService: NoteEntityIngestionService,
    private val transactionTemplate: TransactionTemplate,
    private val logger: KLogger,
) : NoteBackfillActivities {

    companion object {
        const val PAGE_SIZE: Int = 100

        /** First-page sentinel — sorts after every real `notes(created_at, id)` row. */
        private val FAR_FUTURE: ZonedDateTime = ZonedDateTime.now().plusYears(1000)
        private val MAX_UUID: UUID = UUID(Long.MAX_VALUE, Long.MAX_VALUE)
    }

    override fun fetchPage(workspaceId: UUID, cursor: NoteBackfillCursor?): NoteBackfillPage {
        val cursorCreatedAt = cursor?.createdAt?.let(ZonedDateTime::parse) ?: FAR_FUTURE
        val cursorId = cursor?.noteId ?: MAX_UUID

        val notes = noteRepository.findByWorkspaceId(
            workspaceId, cursorCreatedAt, cursorId, PageRequest.of(0, PAGE_SIZE),
        )
        val ids = notes.mapNotNull { it.id }

        val nextCursor = if (notes.size == PAGE_SIZE) {
            notes.lastOrNull()?.let { last ->
                NoteBackfillCursor(
                    createdAt = requireNotNull(last.createdAt) { "note.createdAt must not be null for cursor" }.toString(),
                    noteId = requireNotNull(last.id) { "note.id must not be null for cursor" },
                )
            }
        } else null

        return NoteBackfillPage(noteIds = ids, nextCursor = nextCursor)
    }

    override fun migrateBatch(workspaceId: UUID, noteIds: List<UUID>): NoteBackfillBatchResult {
        var migrated = 0
        var skipped = 0
        var failed = 0

        for (noteId in noteIds) {
            try {
                val outcome = transactionTemplate.execute { migrateOne(workspaceId, noteId) }
                    ?: MigrationOutcome.SKIPPED
                when (outcome) {
                    MigrationOutcome.MIGRATED -> migrated++
                    MigrationOutcome.SKIPPED -> skipped++
                }
            } catch (e: DataIntegrityViolationException) {
                if (isUniqueViolation(e)) {
                    logger.warn { "Note $noteId already migrated — skipping" }
                    skipped++
                } else {
                    logger.error(e) { "Note $noteId integrity violation (non-unique) — failed" }
                    failed++
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to migrate note $noteId" }
                failed++
            } finally {
                // Heartbeat on every iteration regardless of success/skip/fail. Otherwise an
                // exception-heavy batch never heartbeats and the activity exceeds Temporal's
                // heartbeat timeout long before the batch finishes.
                heartbeatSafe(noteId)
            }
        }

        return NoteBackfillBatchResult(migrated, skipped, failed)
    }

    /**
     * Per-note migration step. Returns `null` when the source row is absent
     * (race against a hard-delete) so the batch can mark it skipped.
     */
    private fun migrateOne(workspaceId: UUID, noteId: UUID): MigrationOutcome {
        val note = noteRepository.findById(noteId).orElse(null) ?: return MigrationOutcome.SKIPPED
        require(note.workspaceId == workspaceId) {
            "Note $noteId workspaceId=${note.workspaceId} does not match expected $workspaceId"
        }

        val targets = noteAttachmentRepository.findEntityIdsByNoteId(noteId).toSet()

        noteEntityIngestionService.upsert(
            NoteEntityIngestionService.NoteIngestionInput(
                workspaceId = workspaceId,
                title = note.title,
                content = note.content,
                plaintext = note.plaintext,
                targetEntityIds = targets,
                sourceType = note.sourceType.toEntitySourceType(),
                sourceIntegrationId = note.sourceIntegrationId,
                sourceExternalId = note.sourceExternalId ?: "legacy:${requireNotNull(note.id)}",
                linkSource = note.sourceType.toEntitySourceType(),
            ),
        )
        return MigrationOutcome.MIGRATED
    }

    private fun heartbeatSafe(noteId: UUID) {
        // Activity context is only present when invoked by Temporal; in tests we may run
        // the impl directly. Swallow the IllegalStateException raised by getExecutionContext()
        // when no context is available.
        try {
            Activity.getExecutionContext().heartbeat(noteId)
        } catch (_: IllegalStateException) {
            // Not running inside a Temporal activity — safe to ignore.
        }
    }

    private enum class MigrationOutcome { MIGRATED, SKIPPED }
}

private fun NoteSourceType.toEntitySourceType(): SourceType = when (this) {
    NoteSourceType.USER -> SourceType.USER_CREATED
    NoteSourceType.INTEGRATION -> SourceType.INTEGRATION
}
