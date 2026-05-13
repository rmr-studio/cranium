package cranium.core.service.knowledge

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import cranium.core.entity.entity.EntityEntity
import cranium.core.entity.entity.EntityTypeEntity
import cranium.core.enums.entity.RelationshipTargetKind
import cranium.core.enums.entity.SystemRelationshipType
import cranium.core.enums.knowledge.DefinitionCategory
import cranium.core.enums.knowledge.DefinitionSource
import cranium.core.enums.knowledge.DefinitionStatus
import cranium.core.enums.knowledge.KnowledgeEntityTypeKey
import cranium.core.exceptions.NotFoundException
import cranium.core.exceptions.SchemaValidationException
import cranium.core.models.entity.payload.EntityAttributePrimitivePayload
import cranium.core.models.knowledge.AttributeRef
import cranium.core.models.knowledge.GlossaryTerm
import cranium.core.repository.entity.EntityRelationshipRepository
import cranium.core.repository.entity.EntityRepository
import cranium.core.repository.entity.EntityTypeRepository
import cranium.core.service.entity.EntityAttributeService
import java.util.UUID

/**
 * Reshapes entity-backed `glossary` rows back into the existing [GlossaryTerm]
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
 * controller — it preserves the JSON shape exposed by [GlossaryTerm] so
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

    /** Project a single glossary entity into a [GlossaryTerm]. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun project(workspaceId: UUID, entity: EntityEntity): GlossaryTerm {
        val type = resolveGlossaryType(workspaceId)
        return buildDefinition(entity, type)
    }

    /** Project every glossary entity in the workspace. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun listAll(workspaceId: UUID): List<GlossaryTerm> {
        val rows = entityRepository.findByWorkspaceIdAndTypeKey(workspaceId, KnowledgeEntityTypeKey.GLOSSARY.key)
        if (rows.isEmpty()) return emptyList()
        val type = resolveGlossaryType(workspaceId)
        return rows.map { buildDefinition(it, type) }
    }

    /**
     * Find a single glossary entity by its normalized term within a workspace. Returns
     * the underlying [EntityEntity] so the service layer can decide between conflict /
     * update branches without re-reading.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun findByNormalizedTerm(workspaceId: UUID, normalizedTerm: String): EntityEntity? {
        val type = resolveGlossaryType(workspaceId)
        val mapping = type.attributeKeyMapping
            ?: throw SchemaValidationException(listOf("glossary entity type missing attributeKeyMapping"))
        val normalizedAttrId = mapping["normalized_term"]?.let(UUID::fromString)
            ?: throw SchemaValidationException(listOf("glossary entity type missing 'normalized_term' attribute mapping"))

        val rows = entityRepository.findByWorkspaceIdAndTypeKey(workspaceId, KnowledgeEntityTypeKey.GLOSSARY.key)
        return rows.firstOrNull { entity ->
            val id = entity.id ?: return@firstOrNull false
            val attrs = entityAttributeService.getAttributes(id)
            val value = (attrs[normalizedAttrId]?.value as? String)?.takeIf { it.isNotBlank() }
            value == normalizedTerm
        }
    }

    // ------ Private helpers ------

    private fun resolveGlossaryType(workspaceId: UUID): EntityTypeEntity =
        entityTypeRepository.findByworkspaceIdAndKey(workspaceId, KnowledgeEntityTypeKey.GLOSSARY.key).orElseThrow {
            NotFoundException(
                "glossary entity type missing for workspace $workspaceId — onboarding incomplete"
            )
        }

    private fun buildDefinition(entity: EntityEntity, type: EntityTypeEntity): GlossaryTerm {
        val entityId = requireNotNull(entity.id) { "entity.id" }
        val attrs = entityAttributeService.getAttributes(entityId)
        val mapping = type.attributeKeyMapping
            ?: throw SchemaValidationException(listOf("glossary entity type missing attributeKeyMapping"))
        val (term, normalizedTerm, definition, category, source, isCustomised) =
            unwrapGlossaryAttributes(entityId, mapping, attrs)

        val (entityTypeRefs, attributeRefs) = readDefinesEdges(entityId)

        return GlossaryTerm(
            id = entityId,
            workspaceId = entity.workspaceId,
            term = term,
            normalizedTerm = normalizedTerm,
            definition = definition,
            category = parseCategory(category),
            source = parseSource(source),
            entityTypeRefs = entityTypeRefs,
            attributeRefs = attributeRefs,
            isCustomized = isCustomised,
            createdBy = entity.createdBy,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    /**
     * Read the source's DEFINES relationship rows once, then split them by
     * `target_kind` to populate the legacy [GlossaryTerm.entityTypeRefs]
     * and [GlossaryTerm.attributeRefs] lists.
     */
    private fun readDefinesEdges(sourceId: UUID): Pair<List<UUID>, List<AttributeRef>> {
        val rows = entityRelationshipRepository
            .findBySourceIdAndDefinitionSystemType(sourceId, SystemRelationshipType.DEFINES)
        val typeRefs = rows.filter { it.targetKind == RelationshipTargetKind.ENTITY_TYPE }.map { it.targetId }
        val attrRefs = rows
            .filter { it.targetKind == RelationshipTargetKind.ATTRIBUTE }
            .map { row ->
                AttributeRef(
                    attributeId = row.targetId,
                    ownerEntityTypeId = requireNotNull(row.targetParentId) {
                        "ATTRIBUTE-kind relationship row ${row.id} missing target_parent_id"
                    },
                )
            }
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
        entityId: UUID,
        mapping: Map<String, String>,
        attrs: Map<UUID, EntityAttributePrimitivePayload>,
    ): GlossaryAttributes {
        fun id(key: String): UUID = mapping[key]?.let(UUID::fromString)
            ?: throw SchemaValidationException(listOf("glossary entity type missing '$key' attribute mapping"))

        fun requiredString(key: String): String {
            val raw = attrs[id(key)]?.value as? String
            if (raw.isNullOrEmpty()) {
                throw SchemaValidationException(
                    listOf("glossary entity $entityId: required attribute '$key' missing or non-string"),
                )
            }
            return raw
        }

        return GlossaryAttributes(
            term = requiredString("term"),
            normalizedTerm = requiredString("normalized_term"),
            definition = requiredString("definition"),
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
