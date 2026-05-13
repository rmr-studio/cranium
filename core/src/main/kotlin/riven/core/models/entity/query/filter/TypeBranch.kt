package cranium.core.models.entity.query.filter

import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/**
 * Branch for type-aware filtering in polymorphic relationships.
 *
 * @property entityTypeId UUID of the entity type this branch matches
 * @property filter Optional filter to apply to entities of this type (null = match any)
 */
@Schema(description = "Type branch for polymorphic relationship filtering.")
data class TypeBranch(
    @param:Schema(description = "UUID of the entity type this branch matches.")
    val entityTypeId: UUID,

    @param:Schema(description = "Optional filter to apply to entities of this type.", nullable = true)
    val filter: QueryFilter? = null
)