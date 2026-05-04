package riven.core.projection.entity

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityTypeRole
import riven.core.enums.entity.RelationshipDirection
import riven.core.enums.entity.SystemRelationshipType
import riven.core.models.common.Icon
import riven.core.models.entity.EntityLink
import java.util.*

/**
 * Projection interface for EntityLink native query results.
 * Spring Data JPA will automatically map the query columns to these methods.
 *
 * Note: This interface must only contain simple property accessors (getters).
 * Use the toEntityLink() extension function to convert to the domain model.
 *
 * `direction` is set as a SQL literal in each query (`'FORWARD'` from forward
 * lookups, `'INVERSE'` from inverse lookups). `systemType` is the joined
 * `relationship_definitions.system_type` and may be null for legacy rows that
 * predate the system relationship taxonomy.
 *
 * `sourceSurfaceRole` carries the surface_role of the entity that owns the
 * projection's `sourceEntityId` — joined from entity_types via the source entity's
 * type_id. Drives knowledge-view bucketing in EnrichmentContextAssembler (VIEW-03).
 */
interface EntityLinkProjection {
    fun getId(): UUID
    fun getworkspaceId(): UUID
    fun getTypeKey(): String
    fun getDefinitionId(): UUID
    fun getSourceEntityId(): UUID
    fun getIconType(): String
    fun getIconColour(): String
    fun getLabel(): String
    fun getDirection(): String
    fun getSystemType(): String?
    fun getSourceSurfaceRole(): String
}

/**
 * Convert this projection to an EntityLink domain model.
 */
fun EntityLinkProjection.toEntityLink(): EntityLink = EntityLink(
    id = getId(),
    workspaceId = getworkspaceId(),
    definitionId = getDefinitionId(),
    sourceEntityId = getSourceEntityId(),
    icon = Icon(
        type = IconType.valueOf(getIconType()),
        colour = IconColour.valueOf(getIconColour())
    ),
    key = getTypeKey(),
    label = getLabel(),
    direction = RelationshipDirection.valueOf(getDirection()),
    systemType = getSystemType()?.let { SystemRelationshipType.valueOf(it) },
    sourceSurfaceRole = EntityTypeRole.valueOf(getSourceSurfaceRole()),
)
