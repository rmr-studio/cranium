package riven.core.service.knowledge

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
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.RelationshipTargetKind
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.integration.SourceType
import riven.core.models.knowledge.AttributeRef
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.entity.EntityService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.util.factory.entity.EntityFactory
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

    private val entityService: EntityService = mock()
    private val entityTypeRepository: EntityTypeRepository = mock()
    private val entityRepository: EntityRepository = mock()
    private val entityTypeRelationshipService: EntityTypeRelationshipService = mock()
    private val logger: KLogger = mock()

    private val service = GlossaryEntityIngestionService(
        entityService, entityTypeRepository, entityRepository, entityTypeRelationshipService, logger,
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

        val definesDef = RelationshipDefinitionEntity(
            id = definesDefinitionId,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Defines",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true,
            systemType = SystemRelationshipType.DEFINES,
        )
        val mentionDef = RelationshipDefinitionEntity(
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
            entityService.saveEntityInternal(
                workspaceId = any(), entityTypeId = any(), existingId = anyOrNull(),
                attributePayload = any(), sourceType = any(), sourceIntegrationId = anyOrNull(),
                sourceExternalId = anyOrNull(), readonly = any(),
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
            category = "BUSINESS",
            source = "MANUAL",
            isCustomised = false,
            sourceExternalId = "legacy:abc",
            entityTypeRefs = setOf(typeRefA, typeRefB),
            attributeRefs = listOf(AttributeRef(attributeId = attrRef, ownerEntityTypeId = typeRefA)),
            mentionedEntityIds = setOf(mentionTarget),
        )

        service.upsert(input)

        // Verify attribute payload carries all six glossary fields.
        val payloadCaptor = argumentCaptor<Map<UUID, Any?>>()
        verify(entityService).saveEntityInternal(
            workspaceId = eq(workspaceId),
            entityTypeId = eq(entityTypeId),
            existingId = anyOrNull(),
            attributePayload = payloadCaptor.capture(),
            sourceType = eq(SourceType.USER_CREATED),
            sourceIntegrationId = anyOrNull(),
            sourceExternalId = eq("legacy:abc"),
            readonly = eq(false),
        )
        assertThat(payloadCaptor.firstValue.keys).containsExactlyInAnyOrder(
            termAttrId, normalizedTermAttrId, definitionAttrId,
            categoryAttrId, sourceAttrId, isCustomisedAttrId,
        )
        assertThat(payloadCaptor.firstValue[termAttrId]).isEqualTo("MQL")
        assertThat(payloadCaptor.firstValue[normalizedTermAttrId]).isEqualTo("mql")
        assertThat(payloadCaptor.firstValue[definitionAttrId]).isEqualTo("Marketing-qualified lead")
        assertThat(payloadCaptor.firstValue[categoryAttrId]).isEqualTo("BUSINESS")
        assertThat(payloadCaptor.firstValue[sourceAttrId]).isEqualTo("MANUAL")
        assertThat(payloadCaptor.firstValue[isCustomisedAttrId]).isEqualTo(false)

        // Verify three relationship batches: DEFINES/ENTITY_TYPE, DEFINES/ATTRIBUTE, MENTION/ENTITY.
        verify(entityService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = eq(requireNotNull(savedEntity.id)),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetIds = eq(setOf(typeRefA, typeRefB)),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ENTITY_TYPE),
            targetParentId = eq(null),
        )
        verify(entityService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = eq(requireNotNull(savedEntity.id)),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetIds = eq(setOf(attrRef)),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ATTRIBUTE),
            targetParentId = eq(typeRefA),
        )
        verify(entityService).replaceRelationshipsInternal(
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

        val definesDef = RelationshipDefinitionEntity(
            id = definesDefinitionId, workspaceId = workspaceId, sourceEntityTypeId = entityTypeId,
            name = "Defines", cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true, systemType = SystemRelationshipType.DEFINES,
        )
        val mentionDef = RelationshipDefinitionEntity(
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
            entityService.saveEntityInternal(
                workspaceId = any(), entityTypeId = any(), existingId = anyOrNull(),
                attributePayload = any(), sourceType = any(), sourceIntegrationId = anyOrNull(),
                sourceExternalId = anyOrNull(), readonly = any(),
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
            category = "OTHER",
            source = "MANUAL",
            isCustomised = false,
            sourceExternalId = "legacy:bare",
        )

        service.upsert(input)

        // ENTITY_TYPE + ATTRIBUTE batches still get invoked (with empty sets) so that prior
        // refs are reconciled away on update.
        verify(entityService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId), sourceEntityId = any(),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetIds = eq(emptySet()),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ENTITY_TYPE),
            targetParentId = eq(null),
        )
        verify(entityService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId), sourceEntityId = any(),
            relationshipDefinitionId = eq(definesDefinitionId),
            targetIds = eq(emptySet()),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ATTRIBUTE),
            targetParentId = eq(null),
        )
        verify(entityService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId), sourceEntityId = any(),
            relationshipDefinitionId = eq(mentionDefinitionId),
            targetIds = eq(emptySet()),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ENTITY),
            targetParentId = eq(null),
        )
    }
}
