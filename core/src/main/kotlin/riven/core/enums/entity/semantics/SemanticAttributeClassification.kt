package riven.core.enums.entity.semantics

/**
 * Classification of a semantic attribute, describing the business role it plays.
 *
 * - identifier: uniquely identifies an entity (e.g. email, employee ID)
 * - categorical: groups entities into discrete categories (e.g. industry, status)
 * - quantitative: numeric measurement (e.g. revenue, salary, age)
 * - temporal: date or time value (e.g. created_at, founded_year)
 * - freetext: unstructured text (e.g. description, notes)
 * - relational_reference: a foreign-key-like reference to another entity
 * - connotation_source: a source attribute the DETERMINISTIC tier reads to populate the SENTIMENT metadata
 */
enum class SemanticAttributeClassification {
    IDENTIFIER,
    CATEGORICAL,
    QUANTITATIVE,
    TEMPORAL,
    FREETEXT,
    RELATIONAL_REFERENCE,
    CONNOTATION_SOURCE,
}
