package riven.core.service.knowledge

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.entity.RelationshipTargetKind
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.integration.SourceType
import riven.core.models.knowledge.AttributeRef
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.entity.EntityService
import riven.core.service.entity.type.EntityTypeRelationshipService
import java.util.UUID

/**
 * Concrete subclass of [AbstractKnowledgeEntityIngestionService] for glossary terms.
 * Owns the glossary input shape (term/definition/category/source/etc.) and the mapping
 * into the abstract relationship-batch contract: glossary terms can DEFINE structural
 * objects (entity types, attributes) and MENTION other entities. Single emission point
 * for both the cutover service ([WorkspaceBusinessDefinitionService]) and the legacy
 * backfill workflow.
 */
@Service
class GlossaryEntityIngestionService(
    entityService: EntityService,
    entityTypeRepository: EntityTypeRepository,
    entityRepository: EntityRepository,
    entityTypeRelationshipService: EntityTypeRelationshipService,
    logger: KLogger,
) : AbstractKnowledgeEntityIngestionService<GlossaryEntityIngestionService.GlossaryIngestionInput>(
    entityService, entityTypeRepository, entityRepository, entityTypeRelationshipService, logger,
) {

    override val entityTypeKey: String = "glossary"

    /**
     * Glossary term ingestion input. `entityTypeRefs` and `attributeRefs` both produce
     * `DEFINES` rows on the same relationship definition; the `target_kind` column on
     * `entity_relationships` is what distinguishes them. `mentionedEntityIds` produces
     * `MENTION` rows pointing at other entity instances.
     */
    data class GlossaryIngestionInput(
        override val workspaceId: UUID,
        val term: String,
        val normalizedTerm: String,
        val definition: String,
        val category: String,
        val source: String,
        val isCustomised: Boolean,
        override val sourceExternalId: String,
        val entityTypeRefs: Set<UUID> = emptySet(),
        val attributeRefs: List<AttributeRef> = emptyList(),
        val mentionedEntityIds: Set<UUID> = emptySet(),
        override val sourceType: SourceType = SourceType.USER_CREATED,
        override val sourceIntegrationId: UUID? = null,
        override val readonly: Boolean = false,
        override val linkSource: SourceType = SourceType.USER_CREATED,
    ) : KnowledgeIngestionInput

    override fun buildAttributePayload(
        entityType: EntityTypeEntity,
        input: GlossaryIngestionInput,
    ): Map<UUID, Any?> = mapOf(
        attributeId(entityType, "term") to input.term,
        attributeId(entityType, "normalized_term") to input.normalizedTerm,
        attributeId(entityType, "definition") to input.definition,
        attributeId(entityType, "category") to input.category,
        attributeId(entityType, "source") to input.source,
        attributeId(entityType, "is_customised") to input.isCustomised,
    )

    override fun relationshipBatches(input: GlossaryIngestionInput): List<KnowledgeRelationshipBatch> {
        // DEFINES edges fan out across multiple target_kinds; all reuse the same definition row.
        // ATTRIBUTE rows are split per owning entity_type so each batch can carry the correct
        // `targetParentId` required by the entity_relationships CHECK constraint. When the
        // input carries no attribute refs we still emit a single empty ATTRIBUTE batch so
        // existing rows get reconciled away.
        val attributeBatches = if (input.attributeRefs.isEmpty()) {
            listOf(
                KnowledgeRelationshipBatch(
                    systemType = SystemRelationshipType.DEFINES,
                    targetIds = emptySet(),
                    targetKind = RelationshipTargetKind.ATTRIBUTE,
                ),
            )
        } else {
            input.attributeRefs
                .groupBy { it.ownerEntityTypeId }
                .map { (ownerEntityTypeId, refs) ->
                    KnowledgeRelationshipBatch(
                        systemType = SystemRelationshipType.DEFINES,
                        targetIds = refs.map { it.attributeId }.toSet(),
                        targetKind = RelationshipTargetKind.ATTRIBUTE,
                        targetParentId = ownerEntityTypeId,
                    )
                }
        }

        return buildList {
            add(
                KnowledgeRelationshipBatch(
                    systemType = SystemRelationshipType.DEFINES,
                    targetIds = input.entityTypeRefs,
                    targetKind = RelationshipTargetKind.ENTITY_TYPE,
                ),
            )
            addAll(attributeBatches)
            add(
                KnowledgeRelationshipBatch(
                    systemType = SystemRelationshipType.MENTION,
                    targetIds = input.mentionedEntityIds,
                    targetKind = RelationshipTargetKind.ENTITY,
                ),
            )
        }
    }
}
