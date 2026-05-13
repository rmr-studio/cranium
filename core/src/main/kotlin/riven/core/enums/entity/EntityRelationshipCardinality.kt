package cranium.core.enums.entity

/**
 * Defines the cardinality of relationships between entities.
 */
enum class EntityRelationshipCardinality {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY
}