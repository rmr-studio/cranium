package riven.core.entity.entity

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import java.time.ZonedDateTime
import java.util.*
import jakarta.persistence.Entity as JPAEntity

/**
 * JPA entity for normalized entity attribute values.
 *
 * Each row stores a single attribute value for an entity instance,
 * replacing the JSONB payload column on the entities table.
 */
@JPAEntity
@Table(
    name = "entity_attributes",
    indexes = [
        Index(name = "idx_entity_attributes_entity_id", columnList = "entity_id, attribute_id"),
        Index(name = "idx_entity_attributes_workspace", columnList = "workspace_id"),
    ]
)
data class EntityAttributeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "entity_id", nullable = false)
    val entityId: UUID,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "type_id", nullable = false)
    val typeId: UUID,

    @Column(name = "attribute_id", nullable = false)
    val attributeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "schema_type", nullable = false, length = 50)
    val schemaType: SchemaType,

    @Type(JsonBinaryType::class)
    @Column(name = "value", columnDefinition = "jsonb", nullable = false)
    val value: Any,

    @Column(name = "deleted", nullable = false)
    override var deleted: Boolean = false,

    @Column(name = "deleted_at", nullable = true)
    override var deletedAt: ZonedDateTime? = null,
) : AuditableSoftDeletableEntity() {

    /**
     * Convert to a domain payload.
     */
    fun toPrimitivePayload(): EntityAttributePrimitivePayload {
        return EntityAttributePrimitivePayload(
            value = value,
            schemaType = schemaType,
        )
    }
}
