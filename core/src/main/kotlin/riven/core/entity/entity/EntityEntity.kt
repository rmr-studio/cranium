package riven.core.entity.entity

import jakarta.persistence.*
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.integration.SourceType
import riven.core.models.common.Icon
import riven.core.models.entity.Entity
import riven.core.models.entity.EntityLink
import riven.core.models.entity.payload.EntityAttribute
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRelationPayload
import java.time.ZonedDateTime
import java.util.*
import jakarta.persistence.Entity as JPAEntity

/**
 * JPA entity for entity instances.
 */
@JPAEntity
@Table(
    name = "entities",
    indexes = [
        Index(name = "idx_entities_type_id", columnList = "type_id"),
        Index(name = "idx_entities_workspace_id", columnList = "workspace_id"),
    ]
)
data class EntityEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "type_id", nullable = false)
    val typeId: UUID,

    @Column(name = "type_key", nullable = false)
    val typeKey: String,

    @Column("identifier_key", nullable = false, columnDefinition = "uuid")
    val identifierKey: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_colour", nullable = false)
    var iconColour: IconColour = IconColour.NEUTRAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    var iconType: IconType = IconType.FILE,

    @Column("deleted", nullable = false)
    override var deleted: Boolean = false,

    @Column("deleted_at", nullable = true)
    override var deletedAt: ZonedDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    val sourceType: SourceType = SourceType.USER_CREATED,

    @Column(name = "source_integration_id")
    val sourceIntegrationId: UUID? = null,

    @Column(name = "source_external_id")
    val sourceExternalId: String? = null,

    @Column(name = "source_url")
    val sourceUrl: String? = null,

    @Column(name = "first_synced_at")
    val firstSyncedAt: ZonedDateTime? = null,

    @Column(name = "last_synced_at")
    var lastSyncedAt: ZonedDateTime? = null,

    @Column(name = "sync_version", nullable = false)
    var syncVersion: Long = 0,
) : AuditableSoftDeletableEntity() {
    
    /**
     * Convert this entity to a domain model.
     */
    fun toModel(
        audit: Boolean = false,
        relationships: Map<UUID, List<EntityLink>> = emptyMap(),
        attributes: Map<UUID, EntityAttributePrimitivePayload> = emptyMap(),
    ): Entity {
        val id = requireNotNull(this.id) { "EntityEntity ID cannot be null" }

        val primitiveAttributes: Map<UUID, EntityAttribute> =
            attributes.map { (key, value) -> key to EntityAttribute(payload = value) }.toMap()

        val payload: Map<UUID, EntityAttribute> =
            primitiveAttributes + relationships.mapValues { entry ->
                EntityAttribute(
                    payload =
                        EntityAttributeRelationPayload(
                            relations = entry.value
                        )
                )
            }


        return Entity(
            id = id,
            workspaceId = this.workspaceId,
            typeId = this.typeId,
            payload = payload,
            identifierKey = this.identifierKey,
            icon = Icon(
                type = this.iconType,
                colour = this.iconColour
            ),
            createdAt = if (audit) this.createdAt else null,
            updatedAt = if (audit) this.updatedAt else null,
            createdBy = if (audit) this.createdBy else null,
            updatedBy = if (audit) this.updatedBy else null,
            sourceType = this.sourceType,
            sourceIntegrationId = this.sourceIntegrationId,
            sourceExternalId = this.sourceExternalId,
            sourceUrl = this.sourceUrl,
            firstSyncedAt = this.firstSyncedAt,
            lastSyncedAt = this.lastSyncedAt,
            syncVersion = this.syncVersion
        )
    }
}
