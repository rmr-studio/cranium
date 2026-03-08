package riven.core.service.entity.query

import org.springframework.stereotype.Component
import riven.core.enums.entity.query.FilterOperator
import java.util.*

/**
 * Generates parameterized SQL fragments for filtering entities by attribute values
 * stored in the `entity_attributes` table.
 *
 * All operators produce EXISTS or NOT EXISTS subqueries that correlate on
 * `entity_id = {entityAlias}.id`, enabling indexed lookups via the composite
 * `(attribute_id, type_id, value)` index.
 *
 * ## Value Extraction
 *
 * The `value` column is JSONB. To extract the raw text representation for comparisons,
 * we use `(a.value #>> '{}')` which extracts the top-level JSON value as text
 * (unwrapping quotes from JSON strings, producing raw numbers for JSON numbers, etc.).
 *
 * ## Entity Alias Parameterization
 *
 * The [entityAlias] parameter controls which table alias is used for correlation
 * (e.g., `e.id` vs `t_0.id`). This supports nested relationship filters where the
 * target entity uses a different alias than the root entity.
 */
@Component
class AttributeSqlGenerator {

    /**
     * Generates a SQL fragment for filtering by an attribute value.
     *
     * @param attributeId UUID key of the attribute in the entity_attributes table
     * @param operator Comparison operator to apply
     * @param value Value to compare against (may be null for IS_NULL/IS_NOT_NULL)
     * @param paramGen Generator for unique parameter names
     * @param entityAlias Table alias for the entity being filtered. Defaults to `"e"` (root entity).
     * @return SqlFragment with parameterized SQL and bound values
     */
    fun generate(
        attributeId: UUID,
        operator: FilterOperator,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String = "e"
    ): SqlFragment = when (operator) {
        FilterOperator.EQUALS -> generateEquals(attributeId, value, paramGen, entityAlias)
        FilterOperator.NOT_EQUALS -> generateNotEquals(attributeId, value, paramGen, entityAlias)
        FilterOperator.GREATER_THAN -> generateNumericComparison(attributeId, ">", value, paramGen, entityAlias)
        FilterOperator.GREATER_THAN_OR_EQUALS -> generateNumericComparison(attributeId, ">=", value, paramGen, entityAlias)
        FilterOperator.LESS_THAN -> generateNumericComparison(attributeId, "<", value, paramGen, entityAlias)
        FilterOperator.LESS_THAN_OR_EQUALS -> generateNumericComparison(attributeId, "<=", value, paramGen, entityAlias)
        FilterOperator.IN -> generateIn(attributeId, value, paramGen, entityAlias)
        FilterOperator.NOT_IN -> generateNotIn(attributeId, value, paramGen, entityAlias)
        FilterOperator.CONTAINS -> generateContains(attributeId, value, paramGen, entityAlias)
        FilterOperator.NOT_CONTAINS -> generateNotContains(attributeId, value, paramGen, entityAlias)
        FilterOperator.IS_NULL -> generateIsNull(attributeId, paramGen, entityAlias)
        FilterOperator.IS_NOT_NULL -> generateIsNotNull(attributeId, paramGen, entityAlias)
        FilterOperator.STARTS_WITH -> generateStartsWith(attributeId, value, paramGen, entityAlias)
        FilterOperator.ENDS_WITH -> generateEndsWith(attributeId, value, paramGen, entityAlias)
    }

    // ------ Operator Implementations ------

    /**
     * EXISTS subquery with JSONB value equality.
     * If value is null, delegates to IS_NULL semantics.
     */
    private fun generateEquals(
        attributeId: UUID,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        if (value == null) return generateIsNull(attributeId, paramGen, entityAlias)

        val alias = paramGen.next("a")
        val attrParam = paramGen.next("eq_attr")
        val valParam = paramGen.next("eq_val")

        return SqlFragment(
            sql = "EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND $alias.value = :$valParam::jsonb AND $alias.deleted = false)",
            parameters = mapOf(
                attrParam to attributeId,
                valParam to serializeJsonbValue(value),
            )
        )
    }

    /**
     * EXISTS with value inequality. Requires the attribute to exist (otherwise NOT EXISTS
     * would incorrectly match entities missing the attribute).
     */
    private fun generateNotEquals(
        attributeId: UUID,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        if (value == null) return generateIsNotNull(attributeId, paramGen, entityAlias)

        val alias = paramGen.next("a")
        val attrParam = paramGen.next("neq_attr")
        val valParam = paramGen.next("neq_val")

        return SqlFragment(
            sql = "EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND $alias.value != :$valParam::jsonb AND $alias.deleted = false)",
            parameters = mapOf(
                attrParam to attributeId,
                valParam to serializeJsonbValue(value),
            )
        )
    }

    /**
     * Numeric comparison with regex-guarded cast.
     * Non-numeric values silently fail to match.
     */
    private fun generateNumericComparison(
        attributeId: UUID,
        sqlOperator: String,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        val alias = paramGen.next("a")
        val attrParam = paramGen.next("num_attr")
        val valParam = paramGen.next("num_val")

        val numericValue = when (value) {
            is Number -> value
            is String -> value.toBigDecimalOrNull() ?: 0
            null -> 0
            else -> value.toString().toBigDecimalOrNull() ?: 0
        }

        return SqlFragment(
            sql = """EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND $alias.deleted = false AND CASE WHEN ($alias.value #>> '{}') ~ '^-?[0-9]+(\.[0-9]+)?$' THEN ($alias.value #>> '{}')::numeric $sqlOperator :$valParam ELSE false END)""",
            parameters = mapOf(
                attrParam to attributeId,
                valParam to numericValue,
            )
        )
    }

    /**
     * Case-insensitive substring containment via ILIKE.
     */
    private fun generateContains(
        attributeId: UUID,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        val alias = paramGen.next("a")
        val attrParam = paramGen.next("contains_attr")
        val valParam = paramGen.next("contains_val")

        return SqlFragment(
            sql = "EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND ($alias.value #>> '{}') ILIKE :$valParam ESCAPE '\\' AND $alias.deleted = false)",
            parameters = mapOf(
                attrParam to attributeId,
                valParam to "%${escapeLikePattern(value?.toString() ?: "")}%",
            )
        )
    }

    /**
     * Case-insensitive substring non-containment.
     */
    private fun generateNotContains(
        attributeId: UUID,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        val alias = paramGen.next("a")
        val attrParam = paramGen.next("nc_attr")
        val valParam = paramGen.next("nc_val")

        return SqlFragment(
            sql = "NOT EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND ($alias.value #>> '{}') ILIKE :$valParam ESCAPE '\\' AND $alias.deleted = false)",
            parameters = mapOf(
                attrParam to attributeId,
                valParam to "%${escapeLikePattern(value?.toString() ?: "")}%",
            )
        )
    }

    /**
     * Case-insensitive prefix match.
     */
    private fun generateStartsWith(
        attributeId: UUID,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        val alias = paramGen.next("a")
        val attrParam = paramGen.next("sw_attr")
        val valParam = paramGen.next("sw_val")

        return SqlFragment(
            sql = "EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND ($alias.value #>> '{}') ILIKE :$valParam ESCAPE '\\' AND $alias.deleted = false)",
            parameters = mapOf(
                attrParam to attributeId,
                valParam to "${escapeLikePattern(value?.toString() ?: "")}%",
            )
        )
    }

    /**
     * Case-insensitive suffix match.
     */
    private fun generateEndsWith(
        attributeId: UUID,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        val alias = paramGen.next("a")
        val attrParam = paramGen.next("ew_attr")
        val valParam = paramGen.next("ew_val")

        return SqlFragment(
            sql = "EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND ($alias.value #>> '{}') ILIKE :$valParam ESCAPE '\\' AND $alias.deleted = false)",
            parameters = mapOf(
                attrParam to attributeId,
                valParam to "%${escapeLikePattern(value?.toString() ?: "")}",
            )
        )
    }

    /**
     * IN list membership check.
     * Returns ALWAYS_FALSE for empty lists.
     */
    private fun generateIn(
        attributeId: UUID,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        val values = extractListValues(value)
        if (values.isEmpty()) return SqlFragment.ALWAYS_FALSE

        val alias = paramGen.next("a")
        val attrParam = paramGen.next("in_attr")
        val valsParam = paramGen.next("in_vals")

        return SqlFragment(
            sql = "EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND ($alias.value #>> '{}') IN (:$valsParam) AND $alias.deleted = false)",
            parameters = mapOf(
                attrParam to attributeId,
                valsParam to values.map { it?.toString() ?: "" },
            )
        )
    }

    /**
     * NOT IN list membership check.
     * Returns ALWAYS_TRUE for empty lists.
     * Uses NOT EXISTS to match entities that either lack the attribute OR have a value not in the list.
     */
    private fun generateNotIn(
        attributeId: UUID,
        value: Any?,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        val values = extractListValues(value)
        if (values.isEmpty()) return SqlFragment.ALWAYS_TRUE

        val alias = paramGen.next("a")
        val attrParam = paramGen.next("nin_attr")
        val valsParam = paramGen.next("nin_vals")

        return SqlFragment(
            sql = "EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND ($alias.value #>> '{}') NOT IN (:$valsParam) AND $alias.deleted = false)",
            parameters = mapOf(
                attrParam to attributeId,
                valsParam to values.map { it?.toString() ?: "" },
            )
        )
    }

    /**
     * No matching attribute row exists for this entity (attribute is absent or soft-deleted).
     */
    private fun generateIsNull(
        attributeId: UUID,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        val alias = paramGen.next("a")
        val attrParam = paramGen.next("isnull_attr")

        return SqlFragment(
            sql = "NOT EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND $alias.deleted = false)",
            parameters = mapOf(attrParam to attributeId)
        )
    }

    /**
     * A matching attribute row exists for this entity.
     */
    private fun generateIsNotNull(
        attributeId: UUID,
        paramGen: ParameterNameGenerator,
        entityAlias: String,
    ): SqlFragment {
        val alias = paramGen.next("a")
        val attrParam = paramGen.next("notnull_attr")

        return SqlFragment(
            sql = "EXISTS (SELECT 1 FROM entity_attributes $alias WHERE $alias.entity_id = $entityAlias.id AND $alias.attribute_id = :$attrParam AND $alias.deleted = false)",
            parameters = mapOf(attrParam to attributeId)
        )
    }

    // ------ Private Helpers ------

    /**
     * Serializes a value as a JSON literal string for JSONB comparison.
     * Strings are quoted, numbers/booleans are raw.
     */
    private fun serializeJsonbValue(value: Any): String = when (value) {
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    private fun escapeLikePattern(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun extractListValues(value: Any?): List<Any?> = when (value) {
        is List<*> -> value
        null -> emptyList()
        else -> listOf(value)
    }
}
