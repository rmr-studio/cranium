package riven.core.enums.entity

/**
 * Kind of target a relationship row points at.
 *
 * Default targets are entity instances ([ENTITY]). Knowledge-domain edges (e.g. glossary
 * `DEFINES`) may point at structural objects rather than data rows: an entity type
 * ([ENTITY_TYPE]), a single attribute of an entity type ([ATTRIBUTE]), or a relationship
 * definition owned by an entity type ([RELATIONSHIP]).
 *
 * For the sub-reference target_kinds ([ATTRIBUTE], [RELATIONSHIP]), the row carries
 * `target_parent_id` populated with the owning entity_type id; for [ENTITY] / [ENTITY_TYPE]
 * `target_parent_id` is null.
 */
enum class RelationshipTargetKind {
    ENTITY,
    ENTITY_TYPE,
    ATTRIBUTE,
    RELATIONSHIP,
}
