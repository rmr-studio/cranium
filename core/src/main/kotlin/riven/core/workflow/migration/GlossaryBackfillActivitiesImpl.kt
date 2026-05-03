package riven.core.workflow.migration

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.activity.Activity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import riven.core.repository.knowledge.WorkspaceBusinessDefinitionRepository
import riven.core.service.knowledge.GlossaryEntityIngestionService
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Backfill activity implementation: migrates legacy `workspace_business_definitions` rows
 * into entity-backed glossary terms via [GlossaryEntityIngestionService.upsert].
 *
 * Idempotency contract:
 *   - each legacy definition carries `sourceExternalId = "legacy:{definitionId}"`;
 *   - the ingestion service's idempotent lookup keys on `(workspaceId, sourceExternalId)`
 *     when no integration is present, so re-runs over the same `definitionId` set update
 *     the same entity row in place;
 *   - `DataIntegrityViolationException` from concurrent races is treated as `skipped`,
 *     not `failed`.
 *
 * Each definition produces one glossary entity plus three reconciled relationship batches
 * (DEFINES/ENTITY_TYPE, DEFINES/ATTRIBUTE, MENTION/ENTITY) — see the ingestion service's
 * `relationshipBatches` for the contract.
 */
@Component
class GlossaryBackfillActivitiesImpl(
    private val definitionRepository: WorkspaceBusinessDefinitionRepository,
    private val glossaryEntityIngestionService: GlossaryEntityIngestionService,
    private val transactionTemplate: TransactionTemplate,
    private val logger: KLogger,
) : GlossaryBackfillActivities {

    companion object {
        const val PAGE_SIZE: Int = 100

        /** First-page sentinel — sorts after every real `(created_at, id)` row. */
        private val FAR_FUTURE: ZonedDateTime = ZonedDateTime.now().plusYears(1000)
        private val MAX_UUID: UUID = UUID(Long.MAX_VALUE, Long.MAX_VALUE)
    }

    override fun fetchPage(workspaceId: UUID, cursor: GlossaryBackfillCursor?): GlossaryBackfillPage {
        val cursorCreatedAt = cursor?.createdAt?.let(ZonedDateTime::parse) ?: FAR_FUTURE
        val cursorId = cursor?.definitionId ?: MAX_UUID

        val rows = definitionRepository.findByWorkspaceIdPaged(
            workspaceId, cursorCreatedAt, cursorId, PageRequest.of(0, PAGE_SIZE),
        )
        val ids = rows.mapNotNull { it.id }

        val nextCursor = if (rows.size == PAGE_SIZE) {
            rows.lastOrNull()?.let { last ->
                GlossaryBackfillCursor(
                    createdAt = requireNotNull(last.createdAt) {
                        "definition.createdAt must not be null for cursor"
                    }.toString(),
                    definitionId = requireNotNull(last.id) { "definition.id must not be null for cursor" },
                )
            }
        } else null

        return GlossaryBackfillPage(definitionIds = ids, nextCursor = nextCursor)
    }

    override fun migrateBatch(workspaceId: UUID, definitionIds: List<UUID>): GlossaryBackfillBatchResult {
        var migrated = 0
        var skipped = 0
        var failed = 0

        for (definitionId in definitionIds) {
            try {
                val outcome = transactionTemplate.execute { migrateOne(workspaceId, definitionId) }
                    ?: MigrationOutcome.SKIPPED
                when (outcome) {
                    MigrationOutcome.MIGRATED -> migrated++
                    MigrationOutcome.SKIPPED -> skipped++
                }
            } catch (e: DataIntegrityViolationException) {
                if (isUniqueViolation(e)) {
                    logger.warn { "Glossary definition $definitionId already migrated — skipping" }
                    skipped++
                } else {
                    logger.error(e) { "Glossary definition $definitionId integrity violation (non-unique) — failed" }
                    failed++
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to migrate glossary definition $definitionId" }
                failed++
            } finally {
                // Heartbeat on every iteration regardless of success/skip/fail. Otherwise an
                // exception-heavy batch never heartbeats and the activity exceeds Temporal's
                // heartbeat timeout long before the batch finishes.
                heartbeatSafe(definitionId)
            }
        }

        return GlossaryBackfillBatchResult(migrated, skipped, failed)
    }

    /**
     * Per-definition migration step. Returns `null` (mapped to SKIPPED) when the source
     * row is absent (race against a hard-delete) so the batch can mark it skipped.
     */
    private fun migrateOne(workspaceId: UUID, definitionId: UUID): MigrationOutcome {
        val definition = definitionRepository.findById(definitionId).orElse(null)
            ?: return MigrationOutcome.SKIPPED
        require(definition.workspaceId == workspaceId) {
            "Definition $definitionId workspaceId=${definition.workspaceId} does not match expected $workspaceId"
        }

        glossaryEntityIngestionService.upsert(
            GlossaryEntityIngestionService.GlossaryIngestionInput(
                workspaceId = workspaceId,
                term = definition.term,
                normalizedTerm = definition.normalizedTerm,
                definition = definition.definition,
                category = definition.category,
                source = definition.source,
                isCustomised = definition.isCustomized,
                sourceExternalId = "legacy:${requireNotNull(definition.id) { "definition.id" }}",
                entityTypeRefs = definition.entityTypeRefs.toSet(),
                attributeRefs = definition.attributeRefs,
            ),
        )
        return MigrationOutcome.MIGRATED
    }

    private fun heartbeatSafe(definitionId: UUID) {
        try {
            Activity.getExecutionContext().heartbeat(definitionId)
        } catch (_: IllegalStateException) {
            // Not running inside a Temporal activity — safe to ignore.
        }
    }

    private enum class MigrationOutcome { MIGRATED, SKIPPED }
}
