package cranium.core.service.knowledge

import io.github.oshai.kotlinlogging.KLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.enums.entity.RelationshipTargetKind
import cranium.core.enums.entity.SystemRelationshipType
import cranium.core.enums.integration.SourceType
import cranium.core.enums.knowledge.DefinitionCategory
import cranium.core.enums.knowledge.DefinitionSource
import cranium.core.models.knowledge.AttributeRef
import cranium.core.repository.entity.EntityRelationshipRepository
import cranium.core.repository.entity.EntityRepository
import cranium.core.repository.entity.EntityTypeRepository
import cranium.core.service.entity.EntityIngestionService
import cranium.core.service.entity.type.EntityTypeRelationshipService
import cranium.core.service.util.factory.entity.EntityFactory
import java.util.Optional
import java.util.UUID

/**
 * Verifies the glossary subclass wires the abstract base correctly:
 *
 *   - six attribute keys (term, normalized_term, definition, category, source, is_customised)
 *     flow through into the attribute payload;
 *   - relationshipBatches emits three edges per upsert: DEFINES/ENTITY_TYPE,
 *     DEFINES/ATTRIBUTE, MENTION/ENTITY — and replaceRelationshipsInternal is invoked
 *     once per batch with the matching targetKind.
 */
class GlossaryEntityIngestionServiceTest {

    private val workspaceId: UUID = UUID.randomUUID()
    private val entityTypeId: UUID = UUID.randomUUID()
    private val termAttrId: UUID = UUID.randomUUID()
    private val normalizedTermAttrId: UUID = UUID.randomUUID()
    private val definitionAttrId: UUID = UUID.randomUUID()
    private val categoryAttrId: UUID = UUID.randomUUID()
    private val sourceAttrId: UUID = UUID.randomUUID()
    private val isCustomisedAttrId: UUID = UUID.randomUUID()

    private val definesDefinitionId: UUID = UUID.randomUUID()
    private val mentionDefinitionId: UUID = UUID.randomUUID()

    private val entityIngestionService: EntityIngestionService = mock()
    private val entityTypeRepository: EntityTypeRepository = mock()
    private val entityRepository: EntityRepository = mock()
    private val entityRelationshipRepository: EntityRelationshipRepository = mock()
    private val entityTypeRelationshipService: EntityTypeRelationshipService = mock()
    private val logger: KLogger = mock()

    private val service = GlossaryEntityIngestionService(
        entityIngestionService, entityTypeRepository, entityRepository, entityRelationshipRepository, entityTypeRelationshipService, logger,
    )

    private fun glossaryType() = EntityFactory.createEntityType(
        id = entityTypeId,
        key = "glossary",
        workspaceId = workspaceId,
        attributeKeyMapping = mapOf(
            "term" to termAttrId.toString(),
            "normalized_term" to normalizedTermAttrId.toString(),
            "definition" to definitionAttrId.toString(),
            "category" to categoryAttrId.toString(),
            "source" to sourceAttrId.toString(),
            "is_customised" to isCustomisedAttrId.toString(),
        ),
    )

    @Test
    fun `upsert maps glossary attributes and emits DEFINES + MENTION batches with correct targetKind`() {
        whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "glossary"))
            .thenReturn(Optional.of(glossaryType()))

        val definesDef = EntityFactory.createRelationshipDefinitionEntity(
            id = definesDefinitionId,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Defines",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true,
            systemType = SystemRelationshipType.DEFINES,
        )
        val mentionDef = EntityFactory.createRelationshipDefinitionEntity(
            id = mentionDefinitionId,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Mentions",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true,
            systemType = SystemRelationshipType.MENTION,
        )
        whenever(
            entityTypeRelationshipService.getOrCreateSystemDefinition(
                workspaceId, entityTypeId, SystemRelationshipType.DEFINES,
            )
        ).thenReturn(definesDef)
        whenever(
            entityTypeRelationshipService.getOrCreateSystemDefinition(
                workspaceId, entityTypeId, SystemRelationshipType.MENTION,
            )
        ).thenReturn(mentionDef)

        val savedEntity = EntityFactory.createEntityEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId, typeId = entityTypeId, typeKey = "glossary",
        )
        whenever(
            entityIngestionService.saveEntityInternal(
                workspaceId = any(), entityTypeId = any(), existingId = anyOrNull(),
                attributePayload = any(), sourceType = any(), sourceIntegrationId = anyOrNull(),
                sourceExternalId = anyOrNull(),
            )
        ).thenReturn(savedEntity)

        val typeRefA = UUID.randomUUID()
        val typeRefB = UUID.randomUUID()
        val attrRef = UUID.randomUUID()
        val mentionTarget = UUID.randomUUID()

        val input = GlossaryEntityIngestionService.GlossaryIngestionInput(
            workspaceId = workspaceId,
            term = "MQL",
            normalizedTerm = "mql",
            definition = "Marketing-qualified lead",
            category = DefinitionCategory.CUSTOM,
            source = DefinitionSource.MANUAL,
            isCustomised = false,
            sourceExternalId = "legacy:abc",
            entityTypeRefs = setOf(typeRefA, typeRefB),
            attributeRefs = listOf(AttributeRef(attributeId = attrRef, ownerEntityTypeId = typeRefA)),
            mentionedEntityIds = setOf(mentionTarget),
        )

        service.upsert(input)

        // Verify attribute payload carries all six glossary fields.
        val payloadCaptor = argumentCaptor<Map<UUID, Any?>>()
        verify(entityIngestionService).saveEntityInternal(
            workspaceId = eq(workspaceId),
            entityTypeId = eq(entityTypeId),
            existingId = anyOrNull(),
            attributePayload = payloadCaptor.capture(),
            sourceType = eq(SourceType.USER_CREATED),
            sourceIntegrationId = anyOrNull(),
            sourceExternalId = eq("legacy:abc"),
        )
        assertThat(payloadCaptor.firstValue.keys).containsExactlyInAnyOrder(
            termAttrId, normalizedTermAttrId, definitionAttrId,
            categoryAttrId, sourceAttrId, isCustomisedAttrId,
        )
        assertThat(payloadCaptor.firstValue[termAttrId]).isEqualTo("MQL")
        assertThat(payloadCaptor.firstValue[normalizedTermAttrId]).isEqualTo("mql")
        assertThat(payloadCaptor.firstValue[definitionAttrId]).isEqualTo("Marketing-qualified lead")
        assertThat(payloadCaptor.firstValue[categoryAttrId]).isEqualTo(DefinitionCategory.CUSTOM.name)
        assertThat(payloadCaptor.firstValue[sourceAttrId]).isEqualTo(DefinitionSource.MANUAL.name)
        assertThat(payloadCaptor.firstValue[isCustomisedAttrId]).isEqualTo(false)

        // Verify three relationship batches: DEFINES/ENTITY_TYPE, DEFINES/ATTRIBUTE, MENTION/ENTITY.
        verify(entityIngestionService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = eq(requireNotNull(savedEntity.id)),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetIds = eq(setOf(typeRefA, typeRefB)),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ENTITY_TYPE),
            targetParentId = eq(null),
        )
        verify(entityIngestionService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = eq(requireNotNull(savedEntity.id)),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetIds = eq(setOf(attrRef)),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ATTRIBUTE),
            targetParentId = eq(typeRefA),
        )
        verify(entityIngestionService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = eq(requireNotNull(savedEntity.id)),
            relationshipDefinitionId = eq(mentionDefinitionId),
            targetIds = eq(setOf(mentionTarget)),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ENTITY),
            targetParentId = eq(null),
        )
    }

    @Test
    fun `upsert with empty refs still emits all three batches with empty target sets`() {
        whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "glossary"))
            .thenReturn(Optional.of(glossaryType()))

        val definesDef = EntityFactory.createRelationshipDefinitionEntity(
            id = definesDefinitionId, workspaceId = workspaceId, sourceEntityTypeId = entityTypeId,
            name = "Defines", cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true, systemType = SystemRelationshipType.DEFINES,
        )
        val mentionDef = EntityFactory.createRelationshipDefinitionEntity(
            id = mentionDefinitionId, workspaceId = workspaceId, sourceEntityTypeId = entityTypeId,
            name = "Mentions", cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true, systemType = SystemRelationshipType.MENTION,
        )
        whenever(
            entityTypeRelationshipService.getOrCreateSystemDefinition(workspaceId, entityTypeId, SystemRelationshipType.DEFINES)
        ).thenReturn(definesDef)
        whenever(
            entityTypeRelationshipService.getOrCreateSystemDefinition(workspaceId, entityTypeId, SystemRelationshipType.MENTION)
        ).thenReturn(mentionDef)

        whenever(
            entityIngestionService.saveEntityInternal(
                workspaceId = any(), entityTypeId = any(), existingId = anyOrNull(),
                attributePayload = any(), sourceType = any(), sourceIntegrationId = anyOrNull(),
                sourceExternalId = anyOrNull(),
            )
        ).thenReturn(
            EntityFactory.createEntityEntity(
                id = UUID.randomUUID(), workspaceId = workspaceId, typeId = entityTypeId, typeKey = "glossary",
            )
        )

        val input = GlossaryEntityIngestionService.GlossaryIngestionInput(
            workspaceId = workspaceId,
            term = "Bare term",
            normalizedTerm = "bare term",
            definition = "definition",
            category = DefinitionCategory.CUSTOM,
            source = DefinitionSource.MANUAL,
            isCustomised = false,
            sourceExternalId = "legacy:bare",
        )

        service.upsert(input)

        // ENTITY_TYPE + MENTION batches still emit empty replaceRelationshipsInternal calls
        // (null targetParentId is allowed for ENTITY_TYPE / ENTITY kinds). The ATTRIBUTE kind
        // is parent-scoped, so when input carries no attribute refs the abstract base routes
        // through clearRelationshipsByKindInternal to drop existing rows regardless of parent.
        verify(entityIngestionService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId), sourceEntityId = any(),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetIds = eq(emptySet()),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ENTITY_TYPE),
            targetParentId = eq(null),
        )
        verify(entityIngestionService).clearRelationshipsByKindInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = any(),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetKind = eq(RelationshipTargetKind.ATTRIBUTE),
        )
        verify(entityIngestionService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId), sourceEntityId = any(),
            relationshipDefinitionId = eq(mentionDefinitionId),
            targetIds = eq(emptySet()),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ENTITY),
            targetParentId = eq(null),
        )
    }

    /**
     * Regression for r3176253155: when an existing glossary entity has ATTRIBUTE rows under
     * two owner entity-types (X, Y) and the input shrinks to refs only under owner X,
     * cleanupOrphanedParentBatches must emit a clear-batch for owner Y so its stale rows are
     * swept. Without this the partial-removal case leaks rows under removed owners.
     */
    @Test
    fun `upsert with shrunk attribute owners - clear-batch emitted for orphan owner`() {
        whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "glossary"))
            .thenReturn(Optional.of(glossaryType()))

        val definesDef = EntityFactory.createRelationshipDefinitionEntity(
            id = definesDefinitionId, workspaceId = workspaceId, sourceEntityTypeId = entityTypeId,
            name = "Defines", cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true, systemType = SystemRelationshipType.DEFINES,
        )
        val mentionDef = EntityFactory.createRelationshipDefinitionEntity(
            id = mentionDefinitionId, workspaceId = workspaceId, sourceEntityTypeId = entityTypeId,
            name = "Mentions", cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true, systemType = SystemRelationshipType.MENTION,
        )
        whenever(
            entityTypeRelationshipService.getOrCreateSystemDefinition(workspaceId, entityTypeId, SystemRelationshipType.DEFINES)
        ).thenReturn(definesDef)
        whenever(
            entityTypeRelationshipService.getOrCreateSystemDefinition(workspaceId, entityTypeId, SystemRelationshipType.MENTION)
        ).thenReturn(mentionDef)

        val savedEntity = EntityFactory.createEntityEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId, typeId = entityTypeId, typeKey = "glossary",
        )
        whenever(
            entityIngestionService.saveEntityInternal(
                workspaceId = any(), entityTypeId = any(), existingId = anyOrNull(),
                attributePayload = any(), sourceType = any(), sourceIntegrationId = anyOrNull(),
                sourceExternalId = anyOrNull(),
            )
        ).thenReturn(savedEntity)

        val ownerX = UUID.randomUUID()
        val ownerY = UUID.randomUUID()
        val attrUnderX = UUID.randomUUID()
        val savedId = requireNotNull(savedEntity.id)
        // Existing rows: one under owner X (still-covered), one under owner Y (orphaned by shrunk input).
        whenever(
            entityRelationshipRepository.findAllBySourceIdAndDefinitionIdAndTargetKind(
                savedId, definesDefinitionId, RelationshipTargetKind.ATTRIBUTE,
            )
        ).thenReturn(
            listOf(
                EntityFactory.createRelationshipEntity(
                    workspaceId = workspaceId, sourceId = savedId, targetId = attrUnderX,
                    definitionId = definesDefinitionId,
                    targetKind = RelationshipTargetKind.ATTRIBUTE, targetParentId = ownerX,
                ),
                EntityFactory.createRelationshipEntity(
                    workspaceId = workspaceId, sourceId = savedId, targetId = UUID.randomUUID(),
                    definitionId = definesDefinitionId,
                    targetKind = RelationshipTargetKind.ATTRIBUTE, targetParentId = ownerY,
                ),
            )
        )

        val input = GlossaryEntityIngestionService.GlossaryIngestionInput(
            workspaceId = workspaceId,
            term = "MQL", normalizedTerm = "mql", definition = "def",
            category = DefinitionCategory.CUSTOM, source = DefinitionSource.MANUAL, isCustomised = false,
            sourceExternalId = "legacy:abc",
            attributeRefs = listOf(AttributeRef(attributeId = attrUnderX, ownerEntityTypeId = ownerX)),
        )

        service.upsert(input)

        // Owner X still covered: regular replace fires with the surviving target.
        verify(entityIngestionService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = eq(savedId),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetIds = eq(setOf(attrUnderX)),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ATTRIBUTE),
            targetParentId = eq(ownerX),
        )
        // Owner Y orphaned: clear-batch fires with empty targetIds + ownerY.
        verify(entityIngestionService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = eq(savedId),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetIds = eq(emptySet()),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ATTRIBUTE),
            targetParentId = eq(ownerY),
        )
    }
}
