package cranium.core.enums.entity

/**
 * System-managed relationship kinds. Generic on purpose — the same enum value
 * is reused across all entity types. Concrete semantics emerge from the
 * (sourceEntityType, systemType) pair on `relationship_definitions`.
 */
enum class SystemRelationshipType {
    /** Default system-managed connection between any two entities — used when no domain-specific definition applies (e.g. picker-style ad-hoc links). */
    SYSTEM_CONNECTION,

    /** Knowledge entity (note, memo, sop, ...) attached to one or more entities. */
    ATTACHMENT,

    /** Free-text mention of an entity from inside another entity's content (notes, glossary definitions, memos, ...). */
    MENTION,

    /** Knowledge entity overrides or scopes the definition of another entity-type or attribute (glossary, policy, ...). */
    DEFINES,

    /** Entity is included as part of an event, meeting, incident, or other complex/temporal entity. */
    INCLUDES,
}
