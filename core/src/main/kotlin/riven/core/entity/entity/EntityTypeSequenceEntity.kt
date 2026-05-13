package cranium.core.entity.entity

import jakarta.persistence.*
import java.io.Serializable
import java.util.*

/**
 * Composite primary key for entity type sequences.
 */
data class EntityTypeSequenceId(
    val entityTypeId: UUID = UUID.randomUUID(),
    val attributeId: UUID = UUID.randomUUID(),
) : Serializable

/**
 * Tracks the current sequence counter for an ID-type attribute on an entity type.
 * Counter only increments (never decremented on delete) so generated IDs are never reused.
 */
@Entity
@Table(name = "entity_type_sequences")
@IdClass(EntityTypeSequenceId::class)
data class EntityTypeSequenceEntity(
    @Id
    @Column(name = "entity_type_id", nullable = false)
    val entityTypeId: UUID,

    @Id
    @Column(name = "attribute_id", nullable = false)
    val attributeId: UUID,

    @Column(name = "current_value", nullable = false)
    var currentValue: Long = 0,
)
