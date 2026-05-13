package cranium.core.models.entity

import cranium.core.entity.util.AuditableModel
import cranium.core.enums.entity.RelationshipTargetKind
import cranium.core.enums.integration.SourceType
import java.time.ZonedDateTime
import java.util.*

/**
 * Domain model for a relationship between entities.
 */
data class EntityRelationship(
    val id: UUID,
    val definitionId: UUID,
    val workspaceId: UUID,
    val sourceEntityId: UUID,
    val sourceEntity: Entity? = null, // Can be hydrated if needed
    val targetId: UUID,
    val targetParentId: UUID? = null,
    val targetKind: RelationshipTargetKind = RelationshipTargetKind.ENTITY,
    val targetEntity: Entity? = null,
    val semanticContext: String? = null,
    val linkSource: SourceType = SourceType.USER_CREATED,
    override var createdAt: ZonedDateTime? = null,
    override var updatedAt: ZonedDateTime? = null,
    override var createdBy: UUID? = null,
    override var updatedBy: UUID? = null
) : AuditableModel
