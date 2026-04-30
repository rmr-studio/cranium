package riven.core.service.note

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import riven.core.entity.note.NoteEntity
import riven.core.entity.note.NoteEntityAttachment
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.note.NoteSourceType
import riven.core.enums.util.OperationType
import riven.core.models.integration.NangoRecord
import riven.core.models.integration.NangoRecordAction
import riven.core.models.note.NoteContentFormat
import riven.core.models.note.NoteEmbeddingConfig
import riven.core.repository.entity.EntityRepository
import riven.core.repository.note.NoteEntityAttachmentRepository
import riven.core.repository.note.NoteRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.note.converter.HtmlToBlockConverter
import riven.core.service.note.converter.NoteContentConverter
import riven.core.service.note.converter.PlaintextToBlockConverter
import java.time.ZonedDateTime
import java.util.*

/**
 * Processes integration note records into NoteEntity records with BlockNote content.
 *
 * Bypasses the entity creation pipeline entirely — integration notes are persisted directly
 * as NoteEntity rows with sourceType=INTEGRATION and readonly=true. Associations to target
 * entities are resolved by looking up sourceExternalId in the entities table.
 *
 * This is a plain Spring service with no Temporal coupling. The paginated fetch loop and
 * heartbeat remain in IntegrationSyncActivitiesImpl which delegates per-batch to this service.
 */
@Service
class NoteEmbeddingService(
    private val noteRepository: NoteRepository,
    private val attachmentRepository: NoteEntityAttachmentRepository,
    private val entityRepository: EntityRepository,
    private val htmlToBlockConverter: HtmlToBlockConverter,
    private val plaintextToBlockConverter: PlaintextToBlockConverter,
    private val transactionTemplate: TransactionTemplate,
    private val activityService: ActivityService,
    private val logger: KLogger,
) {

    companion object {
        val SYSTEM_USER_ID: UUID = AuthTokenService.SYSTEM_USER_ID
        const val MAX_BODY_SIZE = 1_048_576 // 1MB
    }

    // ------ Batch Processing ------

    /**
     * Processes a batch of Nango records as notes. Each record is processed in its own
     * transaction for error isolation — a single record failure does not abort the batch.
     */
    fun processBatch(
        records: List<NangoRecord>,
        config: NoteEmbeddingConfig,
        workspaceId: UUID,
        integrationId: UUID,
    ): NoteEmbeddingBatchResult {
        if (records.isEmpty()) return NoteEmbeddingBatchResult(0, 0, null)

        var synced = 0
        var failed = 0
        var lastError: String? = null

        for (record in records) {
            try {
                transactionTemplate.execute {
                    processRecord(record, config, workspaceId, integrationId)
                }
                synced++
            } catch (e: DataIntegrityViolationException) {
                // Dedup race condition: two concurrent syncs for the same note
                logger.warn { "Duplicate note record — skipping: ${e.message}" }
                synced++ // Not a failure — just a race
            } catch (e: Exception) {
                logger.error(e) { "Error processing note record — skipping" }
                failed++
                lastError = e.message
            }
        }

        return NoteEmbeddingBatchResult(synced, failed, lastError)
    }

    // ------ Per-Record Processing ------

    private fun processRecord(
        record: NangoRecord,
        config: NoteEmbeddingConfig,
        workspaceId: UUID,
        integrationId: UUID,
    ) {
        val externalId = record.payload["id"] as? String
            ?: throw IllegalArgumentException("Note record missing 'id' field")

        when (record.nangoMetadata.lastAction) {
            NangoRecordAction.DELETED -> handleDelete(externalId, workspaceId, integrationId)
            NangoRecordAction.ADDED, NangoRecordAction.UPDATED ->
                handleUpsert(record, externalId, config, workspaceId, integrationId)
        }
    }

    private fun handleDelete(externalId: String, workspaceId: UUID, integrationId: UUID) {
        val existing = noteRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalId(
            workspaceId, integrationId, externalId
        )
        if (existing == null) {
            logger.debug { "Note with sourceExternalId=$externalId not found for delete — no-op" }
            return
        }
        val noteId = requireNotNull(existing.id) { "NoteEntity.id must not be null" }
        attachmentRepository.deleteByNoteId(noteId)
        noteRepository.delete(existing)
        activityService.log(
            activity = Activity.NOTE,
            operation = OperationType.DELETE,
            userId = SYSTEM_USER_ID,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.NOTE,
            entityId = noteId,
            "sourceExternalId" to externalId,
            "integrationId" to integrationId,
        )
        logger.info { "Hard-deleted note $noteId (sourceExternalId=$externalId)" }
    }

    private fun handleUpsert(
        record: NangoRecord,
        externalId: String,
        config: NoteEmbeddingConfig,
        workspaceId: UUID,
        integrationId: UUID,
    ) {
        val body = extractBody(record.payload, config)
        val conversionResult = convertBody(body, config.contentFormat)

        val existing = noteRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalId(
            workspaceId, integrationId, externalId
        )
        val isNew = existing == null
        val note = upsertNote(existing, externalId, conversionResult, workspaceId, integrationId)
        val noteId = requireNotNull(note.id) { "NoteEntity.id must not be null after save" }

        activityService.log(
            activity = Activity.NOTE,
            operation = if (isNew) OperationType.CREATE else OperationType.UPDATE,
            userId = SYSTEM_USER_ID,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.NOTE,
            entityId = noteId,
            "sourceExternalId" to externalId,
            "integrationId" to integrationId,
        )

        val associations = extractAssociations(record.payload)
        val targetEntityIds = resolveTargets(associations, config, workspaceId, integrationId)

        if (targetEntityIds.isNotEmpty()) {
            syncAttachments(noteId, targetEntityIds)
            // Clear pending associations — targets were resolved
            if (note.pendingAssociations != null) {
                note.pendingAssociations = null
                noteRepository.save(note)
            }
        } else if (associations.isNotEmpty()) {
            // Store for later reconciliation
            note.pendingAssociations = associations
            noteRepository.save(note)
        }
    }

    // ------ Content Conversion ------

    private fun extractBody(payload: Map<String, Any?>, config: NoteEmbeddingConfig): String? {
        return payload[config.bodyField] as? String
    }

    private fun convertBody(body: String?, format: NoteContentFormat): ConversionResult {
        if (body.isNullOrBlank()) {
            return ConversionResult(
                blocks = listOf(emptyParagraphBlock()),
                plaintext = "",
                title = "",
            )
        }

        val converter: NoteContentConverter = when (format) {
            NoteContentFormat.HTML -> {
                if (body.length > MAX_BODY_SIZE) {
                    logger.warn { "Note body exceeds ${MAX_BODY_SIZE} bytes — truncating to plaintext" }
                    plaintextToBlockConverter
                } else {
                    htmlToBlockConverter
                }
            }
            NoteContentFormat.PLAINTEXT -> plaintextToBlockConverter
        }

        val effectiveBody = if (body.length > MAX_BODY_SIZE) body.take(MAX_BODY_SIZE) else body
        val result = converter.convert(effectiveBody)

        return ConversionResult(
            blocks = result.blocks,
            plaintext = result.plaintext,
            title = result.title,
        )
    }

    private fun emptyParagraphBlock(): Map<String, Any> = mapOf(
        "id" to UUID.randomUUID().toString().take(10),
        "type" to "paragraph",
        "props" to mapOf("textColor" to "default", "backgroundColor" to "default", "textAlignment" to "left"),
        "content" to emptyList<Any>(),
        "children" to emptyList<Any>(),
    )

    // ------ Note Persistence ------

    private fun upsertNote(
        existing: NoteEntity?,
        externalId: String,
        conversion: ConversionResult,
        workspaceId: UUID,
        integrationId: UUID,
    ): NoteEntity {
        val now = ZonedDateTime.now()

        if (existing != null) {
            existing.content = conversion.blocks
            existing.plaintext = conversion.plaintext
            existing.title = conversion.title
            existing.updatedBy = SYSTEM_USER_ID
            return noteRepository.save(existing)
        }

        val note = NoteEntity(
            workspaceId = workspaceId,
            title = conversion.title,
            content = conversion.blocks,
            plaintext = conversion.plaintext,
            sourceType = NoteSourceType.INTEGRATION,
            sourceIntegrationId = integrationId,
            sourceExternalId = externalId,
            readonly = true,
        )
        note.createdBy = SYSTEM_USER_ID
        note.updatedBy = SYSTEM_USER_ID
        note.createdAt = now
        note.updatedAt = now

        return noteRepository.save(note)
    }

    // ------ Association Resolution ------

    /**
     * Extracts association data from the Nango record payload.
     * Returns a map of association type → list of external IDs.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractAssociations(payload: Map<String, Any?>): Map<String, List<String>> {
        val associations = payload["associations"] as? Map<String, Any?> ?: return emptyMap()
        return associations.mapNotNull { (key, value) ->
            val ids = when (value) {
                is List<*> -> value.filterIsInstance<String>()
                else -> null
            }
            if (ids != null && ids.isNotEmpty()) key to ids else null
        }.toMap()
    }

    /**
     * Resolves association external IDs to internal entity UUIDs.
     *
     * Uses the noteEmbedding config's associations map to determine which entity type key
     * each association type maps to, then looks up entities by sourceExternalId.
     */
    private fun resolveTargets(
        associations: Map<String, List<String>>,
        config: NoteEmbeddingConfig,
        workspaceId: UUID,
        integrationId: UUID,
    ): Set<UUID> {
        if (associations.isEmpty()) return emptySet()

        val resolvedIds = mutableSetOf<UUID>()

        for ((associationType, externalIds) in associations) {
            // config.associations maps association type name → entity type key (e.g. "contact" → "hubspot-contact")
            // We don't need the entity type key for lookup — we look up by sourceExternalId + integrationId
            if (!config.associations.containsKey(associationType)) continue

            val entities = entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                workspaceId, integrationId, externalIds
            )
            entities.mapNotNull { it.id }.forEach { resolvedIds.add(it) }
        }

        return resolvedIds
    }

    // ------ Attachment Sync ------

    /**
     * Syncs note_entity_attachments for a note. Adds missing attachments and removes stale ones.
     * Catches FK violations from stale entity references gracefully.
     */
    private fun syncAttachments(noteId: UUID, targetEntityIds: Set<UUID>) {
        val existing = attachmentRepository.findByNoteId(noteId).map { it.entityId }.toSet()
        val toAdd = targetEntityIds - existing
        val toRemove = existing - targetEntityIds

        for (entityId in toAdd) {
            try {
                attachmentRepository.save(NoteEntityAttachment(noteId = noteId, entityId = entityId))
            } catch (e: DataIntegrityViolationException) {
                // Entity deleted between resolution and attachment creation
                logger.warn { "FK violation attaching note $noteId to entity $entityId — entity may have been deleted" }
            }
        }

        for (entityId in toRemove) {
            attachmentRepository.deleteById(riven.core.entity.note.NoteEntityAttachmentId(noteId, entityId))
        }
    }

    // ------ Reconciliation ------

    /**
     * Finds unattached integration notes and attempts to resolve their pending associations.
     *
     * Called after any model sync to handle the case where notes sync before their target
     * contacts/deals/tickets. Scoped to workspace + integration.
     */
    fun reconcileUnattachedNotes(workspaceId: UUID, integrationId: UUID, config: NoteEmbeddingConfig) {
        val unattachedNotes = noteRepository.findUnattachedIntegrationNotes(workspaceId, integrationId)
        if (unattachedNotes.isEmpty()) return

        logger.info { "Reconciling ${unattachedNotes.size} unattached integration notes for workspace=$workspaceId" }

        var resolved = 0
        for (note in unattachedNotes) {
            val pending = note.pendingAssociations ?: continue
            val noteId = requireNotNull(note.id) { "NoteEntity.id must not be null" }

            val targets = resolveTargets(pending, config, workspaceId, integrationId)
            if (targets.isNotEmpty()) {
                syncAttachments(noteId, targets)
                note.pendingAssociations = null
                noteRepository.save(note)
                resolved++
            }
        }

        logger.info { "Reconciled $resolved of ${unattachedNotes.size} unattached notes" }
    }

    // ------ Data Classes ------

    private data class ConversionResult(
        val blocks: List<Map<String, Any>>,
        val plaintext: String,
        val title: String,
    )
}

data class NoteEmbeddingBatchResult(
    val synced: Int,
    val failed: Int,
    val lastError: String?,
)
