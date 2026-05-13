package cranium.core.models.entity

import cranium.core.enums.entity.RelationshipDirection
import cranium.core.enums.entity.SystemRelationshipType
import cranium.core.models.common.Icon
import java.util.*

/**
 * EntityLink represents an entity that is being referenced by another entity,
 * This should provide enough information to display the referenced entity in a UI context,
 * and to navigate to that entity upon user interaction.
 *
 * [direction] disambiguates outbound edges (the viewed entity is the source) from inbound
 * edges (the viewed entity is the target). [systemType] carries the relationship
 * definition's `system_type` so callers can route inverse knowledge edges
 * (`ATTACHMENT` / `MENTION` / `DEFINES`) into a separate projection from the
 * regular relationships map without re-fetching the definition row.
 */
data class EntityLink(
    val id: UUID,
    val workspaceId: UUID,
    val definitionId: UUID,
    val sourceEntityId: UUID,
    val icon: Icon,
    // This should be the value taken from the field marked as the `key` of that entity type for navigation purposes
    val key: String,
    // This should be the value taken from the field marked as the `identifier` of that entity type
    val label: String,
    val direction: RelationshipDirection,
    val systemType: SystemRelationshipType? = null,
)

/**
 * Edge kinds that represent inbound knowledge references on an entity. Used to split
 * the flat link list returned by `EntityRelationshipService.findRelatedEntities` into
 * the regular `relationships` map and the `knowledgeRefs` projection on `Entity`.
 */
private val KNOWLEDGE_INVERSE_KINDS: Set<SystemRelationshipType> = setOf(
    SystemRelationshipType.ATTACHMENT,
    SystemRelationshipType.MENTION,
    SystemRelationshipType.DEFINES,
)

private fun EntityLink.isInverseKnowledgeEdge(): Boolean =
    direction == RelationshipDirection.INVERSE && systemType in KNOWLEDGE_INVERSE_KINDS

/**
 * Holder for the two-way partition of a flat link list:
 *  - [relationships] keyed by `definitionId` — outbound edges of any kind plus inbound
 *    `SYSTEM_CONNECTION` edges. This is what the relationship picker / connections UI consumes.
 *  - [knowledgeRefs] keyed by source entity `typeKey` — inbound `ATTACHMENT` / `MENTION` /
 *    `DEFINES` edges from `surface_role = KNOWLEDGE` source types. Drives the knowledge
 *    badge count and the attached-knowledge panel.
 */
data class PartitionedEntityLinks(
    val relationships: Map<UUID, List<EntityLink>>,
    val knowledgeRefs: Map<String, List<EntityLink>>,
)

fun List<EntityLink>.partitionForEntityProjection(): PartitionedEntityLinks {
    val (knowledge, defined) = this.partition { it.isInverseKnowledgeEdge() }
    return PartitionedEntityLinks(
        relationships = defined.groupBy { it.definitionId },
        knowledgeRefs = knowledge.groupBy { it.key },
    )
}
