package cranium.core.models.block.display

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.databind.annotation.JsonTypeIdResolver
import cranium.core.configuration.util.CaseInsensitiveTypeIdResolver
import java.io.Serializable

/** Map a data source to a component prop */
data class BlockBinding(
    val prop: String,      // e.g. "title.text", "rows" (dot-path into props)
    val source: BindingSource
) : Serializable

/** Data sources: raw block data, references by slot, or computed expr (reserved) */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonTypeIdResolver(CaseInsensitiveTypeIdResolver::class)
@JsonSubTypes(
    JsonSubTypes.Type(BindingSource.DataPath::class, name = "DataPath"),
    JsonSubTypes.Type(BindingSource.Computed::class, name = "Computed")
)
sealed class BindingSource : Serializable {
    // Direct path into the block's data, rendered in that component
    data class DataPath(val path: String) : BindingSource()   // $.data/name, $.data/contacts[0]/email
    data class Computed(val expr: String, val engine: String = "expr-v1") : BindingSource() // reserved for later
}


