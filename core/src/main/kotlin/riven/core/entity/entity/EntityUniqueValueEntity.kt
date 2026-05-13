package cranium.core.entity.entity

import jakarta.persistence.*
import cranium.core.models.common.SoftDeletable
import java.time.ZonedDateTime
import java.util.*

@Entity
@Table(
    name = "entities_unique_values",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_unique_attribute_per_type",
            columnNames = ["type_id", "field_id", "field_value", "deleted"]
        )
    ]
)
data class EntityUniqueValueEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "type_id", nullable = false)
    val typeId: UUID,

    @Column(name = "field_id", nullable = false)
    val fieldId: UUID,

    @Column(name = "field_value", nullable = false)
    val fieldValue: String,

    @Column(name = "entity_id", nullable = false)
    val entityId: UUID,

    @Column(name = "deleted", nullable = false)
    override var deleted: Boolean = false,

    @Column(name = "deleted_at")
    override var deletedAt: ZonedDateTime? = null
) : SoftDeletable
