package cranium.core.enums.entity.query

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Comparison operators for attribute and count filtering.
 */
@Schema(description = "Comparison operators for filtering.")
enum class FilterOperator {
    /** Exact equality comparison */
    EQUALS,

    /** Inequality comparison */
    NOT_EQUALS,

    /** Greater than (numeric/date) */
    GREATER_THAN,

    /** Greater than or equal (numeric/date) */
    GREATER_THAN_OR_EQUALS,

    /** Less than (numeric/date) */
    LESS_THAN,

    /** Less than or equal (numeric/date) */
    LESS_THAN_OR_EQUALS,

    /** Value is in list */
    IN,

    /** Value is not in list */
    NOT_IN,

    /** String/array contains value */
    CONTAINS,

    /** String/array does not contain value */
    NOT_CONTAINS,

    /** Value is null */
    IS_NULL,

    /** Value is not null */
    IS_NOT_NULL,

    /** String starts with value */
    STARTS_WITH,

    /** String ends with value */
    ENDS_WITH
}

