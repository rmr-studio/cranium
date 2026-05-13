package cranium.core.enums.entity

/**
 * Direction of a relationship edge relative to the entity being viewed.
 *
 * [FORWARD] — the entity is the source row of the edge (it points at the link's other end).
 * [INVERSE] — the entity is the target row of the edge (the link's other end points at it).
 *
 * Surfaced on [cranium.core.models.entity.EntityLink] so callers can disambiguate inbound
 * knowledge edges (notes / glossary terms / SOPs that reference the entity) from outbound
 * relationships defined on the entity's own type.
 */
enum class RelationshipDirection {
    FORWARD,
    INVERSE,
}
