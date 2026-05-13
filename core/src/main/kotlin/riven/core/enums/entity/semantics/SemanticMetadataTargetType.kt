package cranium.core.enums.entity.semantics

/**
 * Discriminator enum identifying which domain object a semantic metadata record describes.
 *
 * - ENTITY_TYPE: metadata targets the entity type itself
 * - ATTRIBUTE: metadata targets a specific attribute (target_id = attribute UUID key)
 * - RELATIONSHIP: metadata targets a relationship definition (target_id = relationship UUID)
 */
enum class SemanticMetadataTargetType {
    ENTITY_TYPE,
    ATTRIBUTE,
    RELATIONSHIP
}
