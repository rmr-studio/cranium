package riven.core.enums.core

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Functions that produce default values at entity instance creation time.
 *
 * Each value maps to a deterministic computation evaluated when a new entity
 * instance is saved without an explicit value for the attribute.
 */
enum class DynamicDefaultFunction {
    /** Resolves to the current date (ISO-8601 date, e.g. 2026-04-09) at creation time. */
    @JsonProperty("CURRENT_DATE")
    CURRENT_DATE,

    /** Resolves to the current datetime (ISO-8601 datetime) at creation time. */
    @JsonProperty("CURRENT_DATETIME")
    CURRENT_DATETIME,
}
