package cranium.core.models.common

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.databind.annotation.JsonTypeIdResolver
import cranium.core.configuration.util.CaseInsensitiveTypeIdResolver
import cranium.core.models.common.json.JsonValue

/** Simple boolean predicate */
data class Condition(
    val op: Op,
    val left: Operand,
    val right: Operand? = null
)

enum class Op { EXISTS, EQUALS, NOT_EQUALS, GT, GTE, LT, LTE, IN, NOT_IN, EMPTY, NOT_EMPTY }

@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonTypeIdResolver(CaseInsensitiveTypeIdResolver::class)
@JsonSubTypes(
    JsonSubTypes.Type(Operand.Path::class, name = "Path"),
    JsonSubTypes.Type(Operand.Value::class, name = "Value")
)
sealed class Operand {
    data class Path(val path: String) : Operand()       // same JSONPath-ish as DataPath
    data class Value(val value: JsonValue) : Operand()
}
