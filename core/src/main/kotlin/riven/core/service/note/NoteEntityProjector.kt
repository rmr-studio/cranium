package riven.core.service.note

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.integration.SourceType
import riven.core.enums.knowledge.KnowledgeEntityTypeKey
import riven.core.enums.note.NoteSourceType
import riven.core.exceptions.NotFoundException
import riven.core.exceptions.SchemaValidationException
import riven.core.models.note.Note
import riven.core.models.note.NoteEntityContext
import riven.core.models.note.WorkspaceNote
import riven.core.repository.entity.EntityAttributeRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.entity.EntityAttributeService
import riven.core.util.CursorPage
import riven.core.util.CursorPagination
import java.util.UUID

/**
 * Reshapes entity-backed `note` rows back into the existing [Note] / [WorkspaceNote]
 * DTO contract. Read-only — does not mutate entities or relationships.
 *
 * Inputs:
 *   - `EntityEntity` rows where `typeKey = "note"` (from `EntityRepository`);
 *   - `entity_attributes` rows for the title / content / plaintext attributes;
 *   - `entity_relationships` rows where `definition.systemType = ATTACHMENT` to
 *     resolve the attached entity ids (replacing the legacy `note_entity_attachments`
 *     join).
 *
 * Cross-domain note: this projector is the post-cutover read path for the note
 * controller — it preserves the JSON shape exposed by [Note] and [WorkspaceNote]
 * so the frontend doesn't need to know that notes are now entities.
 */
@Service
class NoteEntityProjector(
    private val entityRepository: EntityRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val entityAttributeService: EntityAttributeService,
    private val entityAttributeRepository: EntityAttributeRepository,
    private val entityRelationshipRepository: EntityRelationshipRepository,
) {

    // ------ Public read operations ------

    /** Project a single note entity into a [Note] (with attached entity ids). */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun projectNote(workspaceId: UUID, noteEntity: EntityEntity): Note {
        val noteType = resolveNoteType(workspaceId)
        val targetIds = attachedEntityIds(noteEntity)
        return buildNote(noteEntity, noteType, targetIds)
    }

    /** Project a single note entity into a [WorkspaceNote] (with entity context). */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun projectWorkspaceNote(workspaceId: UUID, noteEntity: EntityEntity): WorkspaceNote {
        val noteType = resolveNoteType(workspaceId)
        val targetIds = attachedEntityIds(noteEntity)
        val contexts = if (targetIds.isEmpty()) emptyList() else buildEntityContexts(workspaceId, targetIds)
        return buildWorkspaceNote(noteEntity, noteType, targetIds, contexts)
    }

    /**
     * List all notes in the workspace, projecting each into a [WorkspaceNote] with
     * its attached entity contexts. Cursor pagination uses the existing
     * (createdAt, id) keyset to maintain stable ordering.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun listNotes(
        workspaceId: UUID,
        search: String?,
        cursor: String?,
        limit: Int,
    ): CursorPage<WorkspaceNote> {
        require(limit in 1..100) { "limit must be between 1 and 100" }

        val noteType = resolveNoteType(workspaceId)
        val all = entityRepository.findByWorkspaceIdAndTypeKey(workspaceId, KnowledgeEntityTypeKey.NOTE.key)
        val filtered = if (!search.isNullOrBlank()) {
            val term = search.lowercase()
            val plaintextId = resolvePlaintextAttributeId(noteType)
            val titleId = resolveTitleAttributeId(noteType)
            all.filter { matchesPlaintext(it, plaintextId, titleId, term) }
        } else all

        val (cursorCreatedAt, cursorId) = CursorPagination.decodeCursor(cursor)
        // Sort deterministically by (createdAt DESC, id DESC) before applying the cursor
        // predicate and `.take(limit)`. The repository query orders by createdAt DESC only,
        // so notes that share a millisecond would otherwise come back in undefined order and
        // cursor pagination could duplicate or skip rows. The id tiebreaker matches the
        // (createdAt, id) keyset emitted by the cursor encoder.
        val paged = filtered
            .sortedWith(
                compareByDescending<EntityEntity> {
                    requireNotNull(it.createdAt) { "createdAt must not be null for cursor sort" }
                }.thenByDescending {
                    requireNotNull(it.id) { "id must not be null for cursor sort" }
                }
            )
            .filter { entity ->
                val createdAt = entity.createdAt ?: return@filter false
                val id = entity.id ?: return@filter false
                createdAt < cursorCreatedAt || (createdAt == cursorCreatedAt && id < cursorId)
            }
            .take(limit)

        val attachmentsByNoteId = batchAttachmentsBySource(paged.mapNotNull { it.id })
        val allTargetIds = attachmentsByNoteId.values.flatten().toSet()
        val contextMap = if (allTargetIds.isEmpty()) emptyMap() else buildEntityContextMap(workspaceId, allTargetIds)

        val items = paged.map { entity ->
            val targetIds = attachmentsByNoteId[entity.id] ?: emptyList()
            val contexts = targetIds.mapNotNull { contextMap[it] }
            buildWorkspaceNote(entity, noteType, targetIds, contexts)
        }

        val nextCursor = if (paged.size == limit) {
            paged.lastOrNull()?.let { last ->
                CursorPagination.encodeCursor(
                    requireNotNull(last.createdAt) { "createdAt must not be null for cursor" },
                    requireNotNull(last.id) { "id must not be null for cursor" },
                )
            }
        } else null

        return CursorPage(items = items, nextCursor = nextCursor, totalCount = filtered.size.toLong())
    }

    /**
     * List notes attached to a single entity (inverse `ATTACHMENT` lookup).
     * Each result is projected into a [Note] (no `entityContexts`).
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getNotesForEntity(workspaceId: UUID, entityId: UUID, search: String?): List<Note> {
        val attachments = entityRelationshipRepository.findByTargetIdAndDefinitionSystemType(
            entityId, SystemRelationshipType.ATTACHMENT,
        )
        if (attachments.isEmpty()) return emptyList()

        val noteIds = attachments.map { it.sourceId }.toSet()
        val noteEntities = entityRepository.findAllById(noteIds)
            .filter { it.workspaceId == workspaceId && it.typeKey == KnowledgeEntityTypeKey.NOTE.key }

        val noteType = resolveNoteType(workspaceId)
        val attachmentsByNoteId = batchAttachmentsBySource(noteEntities.mapNotNull { it.id })

        val notes = noteEntities.map { entity ->
            val targetIds = attachmentsByNoteId[entity.id] ?: listOf(entityId)
            buildNote(entity, noteType, targetIds)
        }

        return if (!search.isNullOrBlank()) {
            val term = search.lowercase()
            notes.filter { it.title.lowercase().contains(term) }
        } else notes
    }

    // ------ Private helpers ------

    private fun resolveNoteType(workspaceId: UUID): EntityTypeEntity =
        entityTypeRepository.findByworkspaceIdAndKey(workspaceId, KnowledgeEntityTypeKey.NOTE.key).orElseThrow {
            NotFoundException("note entity type missing for workspace $workspaceId — onboarding incomplete")
        }

    private fun attachedEntityIds(noteEntity: EntityEntity): List<UUID> {
        val sourceId = noteEntity.id ?: return emptyList()
        return entityRelationshipRepository
            .findBySourceIdAndDefinitionSystemType(sourceId, SystemRelationshipType.ATTACHMENT)
            .map { it.targetId }
    }

    private fun batchAttachmentsBySource(sourceIds: List<UUID>): Map<UUID, List<UUID>> {
        if (sourceIds.isEmpty()) return emptyMap()
        return entityRelationshipRepository
            .findAllBySourceIdInAndDefinitionSystemType(sourceIds, SystemRelationshipType.ATTACHMENT)
            .groupBy({ it.sourceId }, { it.targetId })
    }

    private fun buildNote(
        entity: EntityEntity,
        noteType: EntityTypeEntity,
        targetIds: List<UUID>,
    ): Note {
        val attrs = entityAttributeService.getAttributes(requireNotNull(entity.id) { "entity.id" })
        val (title, content, _) = unwrapNoteAttributes(noteType, attrs)
        return Note(
            id = requireNotNull(entity.id) { "entity.id" },
            entityIds = targetIds,
            workspaceId = entity.workspaceId,
            title = title,
            content = content,
            sourceType = entity.sourceType.toNoteSourceType(),
            readonly = entity.sourceType == SourceType.INTEGRATION,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            createdBy = entity.createdBy,
            updatedBy = entity.updatedBy,
        )
    }

    private fun buildWorkspaceNote(
        entity: EntityEntity,
        noteType: EntityTypeEntity,
        targetIds: List<UUID>,
        contexts: List<NoteEntityContext>,
    ): WorkspaceNote {
        val attrs = entityAttributeService.getAttributes(requireNotNull(entity.id) { "entity.id" })
        val (title, content, _) = unwrapNoteAttributes(noteType, attrs)
        return WorkspaceNote(
            id = requireNotNull(entity.id) { "entity.id" },
            entityIds = targetIds,
            workspaceId = entity.workspaceId,
            title = title,
            content = content,
            sourceType = entity.sourceType.toNoteSourceType(),
            readonly = entity.sourceType == SourceType.INTEGRATION,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            createdBy = entity.createdBy,
            updatedBy = entity.updatedBy,
            entityContexts = contexts,
        )
    }

    /**
     * Pull `title` / `content` / `plaintext` out of the loaded attribute map using
     * the attribute UUIDs declared on the workspace's note entity type.
     */
    private fun unwrapNoteAttributes(
        noteType: EntityTypeEntity,
        attrs: Map<UUID, riven.core.models.entity.payload.EntityAttributePrimitivePayload>,
    ): Triple<String, List<Map<String, Any>>, String> {
        val mapping = noteType.attributeKeyMapping
            ?: error("note entity type missing attributeKeyMapping")
        val titleId = mapping["title"]?.let(UUID::fromString)
            ?: error("note entity type missing 'title' attribute mapping")
        val contentId = mapping["content"]?.let(UUID::fromString)
            ?: error("note entity type missing 'content' attribute mapping")
        val plaintextId = mapping["plaintext"]?.let(UUID::fromString)
            ?: error("note entity type missing 'plaintext' attribute mapping")

        val title = (attrs[titleId]?.value as? String)
            ?: throw SchemaValidationException(listOf("note attribute 'title' is missing or not a string"))
        @Suppress("UNCHECKED_CAST")
        val content = (attrs[contentId]?.value as? List<Map<String, Any>>)
            ?: throw SchemaValidationException(listOf("note attribute 'content' is missing or not a list"))
        val plaintext = (attrs[plaintextId]?.value as? String)
            ?: throw SchemaValidationException(listOf("note attribute 'plaintext' is missing or not a string"))
        return Triple(title, content, plaintext)
    }

    /**
     * Resolves the plaintext attribute id from the note entity type's attributeKeyMapping.
     * Throws SchemaValidationException when missing — keeps search consistent with project
     * read paths instead of silently filtering out malformed notes.
     */
    private fun resolvePlaintextAttributeId(noteType: EntityTypeEntity): UUID {
        val mapping = noteType.attributeKeyMapping
            ?: throw SchemaValidationException(listOf("note entity type missing attributeKeyMapping"))
        return mapping["plaintext"]?.let(UUID::fromString)
            ?: throw SchemaValidationException(listOf("note entity type missing 'plaintext' attribute mapping"))
    }

    private fun resolveTitleAttributeId(noteType: EntityTypeEntity): UUID? =
        noteType.attributeKeyMapping?.get("title")?.let(UUID::fromString)

    /**
     * Fetch entity context (display name, type metadata) for a set of entity ids.
     * Mirrors the legacy `findEntityContext` query but operates entirely against
     * the entity / entity_type / entity_attributes triple.
     */
    private fun buildEntityContextMap(
        workspaceId: UUID,
        entityIds: Set<UUID>,
    ): Map<UUID, NoteEntityContext> {
        if (entityIds.isEmpty()) return emptyMap()
        val entities = entityRepository.findByIdInAndWorkspaceId(entityIds, workspaceId)
        if (entities.isEmpty()) return emptyMap()

        val typeIds = entities.map { it.typeId }.toSet()
        val typesById = entityTypeRepository.findAllById(typeIds).associateBy { requireNotNull(it.id) }

        // Identifier-attribute values: load only the rows where attribute_id == identifier_key.
        val identifierAttrIds = entities.map { it.identifierKey }.toSet()
        val attrRows = entityAttributeRepository
            .findByEntityIdInAndAttributeIdIn(entityIds, identifierAttrIds)
            .associateBy { Pair(it.entityId, it.attributeId) }

        return entities.associate { entity ->
            val type = typesById[entity.typeId]
            val displayNode = attrRows[Pair(requireNotNull(entity.id) { "entity.id" }, entity.identifierKey)]?.value
            val displayName = displayNode?.let { node ->
                if (node.isString) node.stringValue() else node.toString()
            }
            requireNotNull(entity.id) to NoteEntityContext(
                entityId = requireNotNull(entity.id),
                entityDisplayName = displayName,
                entityTypeKey = type?.key ?: entity.typeKey,
                entityTypeIcon = (type?.iconType ?: entity.iconType).name,
                entityTypeColour = (type?.iconColour ?: entity.iconColour).name,
            )
        }
    }

    private fun buildEntityContexts(workspaceId: UUID, entityIds: List<UUID>): List<NoteEntityContext> {
        val map = buildEntityContextMap(workspaceId, entityIds.toSet())
        return entityIds.mapNotNull { map[it] }
    }

    /**
     * In-memory contains-match against the loaded plaintext attribute. Mirrors the
     * legacy Postgres FTS search well enough for the post-cutover path; the parity
     * test (Task 13) asserts this is acceptable.
     *
     * TODO(Phase F): replace with a persisted `entities.search_vector` or per-attribute FTS index
     * once the legacy `notes` table is dropped and we no longer rely on its `search_vector`.
     */
    private fun matchesPlaintext(
        entity: EntityEntity,
        plaintextId: UUID,
        titleId: UUID?,
        lowercaseTerm: String,
    ): Boolean {
        val id = requireNotNull(entity.id) { "entity.id" }
        val attrs = entityAttributeService.getAttributes(id)
        val plaintext = (attrs[plaintextId]?.value as? String)
            ?: throw SchemaValidationException(
                listOf("note ${id} attribute 'plaintext' is missing or not a string"),
            )
        val title = (titleId?.let { attrs[it]?.value } as? String) ?: ""
        return plaintext.lowercase().contains(lowercaseTerm) || title.lowercase().contains(lowercaseTerm)
    }

    private fun SourceType.toNoteSourceType(): NoteSourceType = when (this) {
        SourceType.INTEGRATION -> NoteSourceType.INTEGRATION
        else -> NoteSourceType.USER
    }
}
