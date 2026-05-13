package cranium.core.models.block.metadata

import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.enums.block.structure.BlockReferenceFetchPolicy
import java.util.*


@Schema(hidden = true)
sealed interface ReferenceMetadata : Metadata {
    val fetchPolicy: BlockReferenceFetchPolicy
    val path: String
}

data class ReferenceItem(
    val id: UUID,
    val labelOverride: String? = null,
    val badge: String? = null,
//    val actions: List<ActionRef>? = null
)

enum class Presentation { SUMMARY, ENTITY, TABLE, GRID, INLINE }

data class Projection(
    val fields: List<String> = emptyList(), // e.g., ["name","domain","contact.email"]
    val templateId: UUID? = null            // optional reusable template
)

data class SortSpec(val by: String, val dir: SortDir = SortDir.ASC)
enum class SortDir { ASC, DESC }
data class FilterSpec(
    @get:Schema(type = "object", implementation = Any::class)
    val expr: Map<String, Any?> = emptyMap()
)

data class PagingSpec(val pageSize: Int = 20)
