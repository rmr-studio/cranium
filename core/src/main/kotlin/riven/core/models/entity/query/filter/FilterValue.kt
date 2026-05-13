package cranium.core.models.entity.query.filter

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.annotation.JsonTypeIdResolver
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.configuration.util.CaseInsensitiveTypeIdResolver

/**
 * Filter value supporting both literal values and template expressions.
 *
 * Templates enable dynamic value resolution from workflow context.
 */
@Schema(
    description = "Value for filter comparison - literal or template expression.",
    oneOf = [FilterValue.Literal::class, FilterValue.Template::class]
)
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonTypeIdResolver(CaseInsensitiveTypeIdResolver::class)
@JsonSubTypes(
    JsonSubTypes.Type(FilterValue.Literal::class, name = "LITERAL"),
    JsonSubTypes.Type(FilterValue.Template::class, name = "TEMPLATE")
)
sealed interface FilterValue {

    /**
     * Literal value for direct comparison.
     *
     * @property value The literal value (string, number, boolean, null, or list)
     */
    @Schema(description = "Literal value for comparison.")
    @JsonTypeName("LITERAL")
    data class Literal(
        @param:Schema(description = "The literal value.", example = "\"Active\"")
        val value: Any?
    ) : FilterValue

    /**
     * Template expression resolved at execution time.
     *
     * @property expression Template string using workflow context syntax
     */
    @Schema(description = "Template expression resolved at execution time.")
    @JsonTypeName("TEMPLATE")
    data class Template(
        @param:Schema(
            description = "Template expression using workflow context.",
            example = "{{ steps.lookup.output.status }}"
        )
        val expression: String
    ) : FilterValue
}
