package riven.core.service.knowledge

import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.entity.RelationshipTargetKind
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionSource
import riven.core.enums.knowledge.DefinitionStatus
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.knowledge.WorkspaceBusinessDefinition
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.entity.EntityAttributeService
import java.util.UUID

/**
 * Reshapes entity-backed `glossary` rows back into the existing [WorkspaceBusinessDefinition]
 * DTO contract. Read-only — does not mutate entities or relationships.
 *
 * Inputs:
 *   - `EntityEntity` rows where `typeKey = "glossary"` (from `EntityRepository`);
 *   - `entity_attributes` rows for the six glossary attributes (term / normalized_term /
 *     definition / category / source / is_customised);
 *   - `entity_relationships` rows where `definition.systemType = DEFINES` to populate
 *     `entityTypeRefs` (target_kind=ENTITY_TYPE) and `attributeRefs` (target_kind=ATTRIBUTE).
 *
 * Cross-domain note: this projector is the post-cutover read path for the knowledge
 * controller — it preserves the JSON shape exposed by [WorkspaceBusinessDefinition] so
 * the frontend doesn't need to know that glossary terms are now entities. Fields with no
 * direct entity-layer storage (`compiledParams`, `status`, `version`) project to fixed
 * defaults: compiledParams=null, status=ACTIVE, version=0.
 */
@Service
class GlossaryEntityProjector(
    private val entityRepository: EntityRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val entityAttributeService: EntityAttributeService,
    private val entityRelationshipRepository: EntityRelationshipRepository,
) {

    // ------ Public read operations ------

    /** Project a single glossary entity into a [WorkspaceBusinessDefinition]. */
    fun project(workspaceId: UUID, entity: EntityEntity): WorkspaceBusinessDefinition {
        val type = resolveGlossaryType(workspaceId)
        return buildDefinition(entity, type)
    }

    /** Project every glossary entity in the workspace. */
    fun listAll(workspaceId: UUID): List<WorkspaceBusinessDefinition> {
        val rows = entityRepository.findByWorkspaceIdAndTypeKey(workspaceId, "glossary")
        if (rows.isEmpty()) return emptyList()
        val type = resolveGlossaryType(workspaceId)
        return rows.map { buildDefinition(it, type) }
    }

    /**
     * Find a single glossary entity by its normalized term within a workspace. Returns
     * the underlying [EntityEntity] so the service layer can decide between conflict /
     * update branches without re-reading.
     */
    fun findByNormalizedTerm(workspaceId: UUID, normalizedTerm: String): EntityEntity? {
        val type = resolveGlossaryType(workspaceId)
        val mapping = type.attributeKeyMapping
            ?: error("glossary entity type missing attributeKeyMapping")
        val normalizedAttrId = mapping["normalized_term"]?.let(UUID::fromString)
            ?: error("glossary entity type missing 'normalized_term' attribute mapping")

        val rows = entityRepository.findByWorkspaceIdAndTypeKey(workspaceId, "glossary")
        return rows.firstOrNull { entity ->
            val id = entity.id ?: return@firstOrNull false
            val attrs = entityAttributeService.getAttributes(id)
            val value = (attrs[normalizedAttrId]?.value as? String)?.takeIf { it.isNotBlank() }
            value == normalizedTerm
        }
    }

    // ------ Private helpers ------

    private fun resolveGlossaryType(workspaceId: UUID): EntityTypeEntity =
        entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "glossary").orElseThrow {
            IllegalStateException(
                "glossary entity type missing for workspace $workspaceId — onboarding incomplete"
            )
        }

    private fun buildDefinition(entity: EntityEntity, type: EntityTypeEntity): WorkspaceBusinessDefinition {
        val entityId = requireNotNull(entity.id) { "entity.id" }
        val attrs = entityAttributeService.getAttributes(entityId)
        val mapping = type.attributeKeyMapping
            ?: error("glossary entity type missing attributeKeyMapping")
        val (term, normalizedTerm, definition, category, source, isCustomised) =
            unwrapGlossaryAttributes(mapping, attrs)

        val (entityTypeRefs, attributeRefs) = readDefinesEdges(entityId)

        return WorkspaceBusinessDefinition(
            id = entityId,
            workspaceId = entity.workspaceId,
            term = term,
            normalizedTerm = normalizedTerm,
            definition = definition,
            category = parseCategory(category),
            compiledParams = null,
            status = DefinitionStatus.ACTIVE,
            source = parseSource(source),
            entityTypeRefs = entityTypeRefs,
            attributeRefs = attributeRefs,
            isCustomized = isCustomised,
            version = 0,
            createdBy = entity.createdBy,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    /**
     * Read the source's DEFINES relationship rows once, then split them by
     * `target_kind` to populate the legacy [WorkspaceBusinessDefinition.entityTypeRefs]
     * and [WorkspaceBusinessDefinition.attributeRefs] lists.
     */
    private fun readDefinesEdges(sourceId: UUID): Pair<List<UUID>, List<UUID>> {
        val rows = entityRelationshipRepository
            .findBySourceIdAndDefinitionSystemType(sourceId, SystemRelationshipType.DEFINES)
        val typeRefs = rows.filter { it.targetKind == RelationshipTargetKind.ENTITY_TYPE }.map { it.targetId }
        val attrRefs = rows.filter { it.targetKind == RelationshipTargetKind.ATTRIBUTE }.map { it.targetId }
        return typeRefs to attrRefs
    }

    private data class GlossaryAttributes(
        val term: String,
        val normalizedTerm: String,
        val definition: String,
        val category: String,
        val source: String,
        val isCustomised: Boolean,
    )

    private fun unwrapGlossaryAttributes(
        mapping: Map<String, String>,
        attrs: Map<UUID, EntityAttributePrimitivePayload>,
    ): GlossaryAttributes {
        fun id(key: String): UUID = mapping[key]?.let(UUID::fromString)
            ?: error("glossary entity type missing '$key' attribute mapping")
        return GlossaryAttributes(
            term = (attrs[id("term")]?.value as? String) ?: "",
            normalizedTerm = (attrs[id("normalized_term")]?.value as? String) ?: "",
            definition = (attrs[id("definition")]?.value as? String) ?: "",
            category = (attrs[id("category")]?.value as? String) ?: DefinitionCategory.CUSTOM.name,
            source = (attrs[id("source")]?.value as? String) ?: DefinitionSource.MANUAL.name,
            isCustomised = (attrs[id("is_customised")]?.value as? Boolean) ?: false,
        )
    }

    private fun parseCategory(raw: String): DefinitionCategory =
        runCatching { DefinitionCategory.valueOf(raw) }.getOrDefault(DefinitionCategory.CUSTOM)

    private fun parseSource(raw: String): DefinitionSource =
        runCatching { DefinitionSource.valueOf(raw) }.getOrDefault(DefinitionSource.MANUAL)
}
