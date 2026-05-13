package cranium.core.models.knowledge

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * Reference to an attribute defined on an entity type. Carries both the attribute
 * UUID and its owning entity type UUID so downstream relationship rows
 * (`entity_relationships` with `target_kind = 'ATTRIBUTE'`) can populate the
 * `target_parent_id` column required by the schema CHECK constraint.
 */
data class AttributeRef @JsonCreator constructor(
    @param:JsonProperty("attributeId") val attributeId: UUID,
    @param:JsonProperty("ownerEntityTypeId") val ownerEntityTypeId: UUID,
)
