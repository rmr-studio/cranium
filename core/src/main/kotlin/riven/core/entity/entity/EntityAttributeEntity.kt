package riven.core.entity.entity

import tools.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
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
@SQLRestriction("deleted = false")
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
    val value: JsonNode,

    @Column(name = "deleted", nullable = false)
    override var deleted: Boolean = false,

    @Column(name = "deleted_at", nullable = true)
    override var deletedAt: ZonedDateTime? = null,
) : AuditableSoftDeletableEntity() {

    /**
     * Convert to a domain payload, unwrapping JsonNode to a plain value.
     */
    fun toPrimitivePayload(): EntityAttributePrimitivePayload {
        return EntityAttributePrimitivePayload(
            value = unwrapJsonNode(value),
            schemaType = schemaType,
        )
    }

    companion object {
        /**
         * Recursively unwrap a JsonNode to plain Kotlin/Java types
         * so the domain layer works with native types (String, Number, List, Map).
         */
        fun unwrapJsonNode(node: JsonNode): Any? = when {
            node.isNull -> null
            node.isString -> node.stringValue()
            node.isBoolean -> node.booleanValue()
            node.isNumber -> node.numberValue()
            node.isArray -> node.map { unwrapJsonNode(it) }
            node.isObject -> node.properties().associate { (k, v) -> k to unwrapJsonNode(v) }
            else -> node.toString()
        }
    }
}
