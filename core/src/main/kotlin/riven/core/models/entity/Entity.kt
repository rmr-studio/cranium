package riven.core.models.entity

import riven.core.entity.util.AuditableModel
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.integration.SourceType
import riven.core.models.common.Icon
import riven.core.models.entity.payload.EntityAttribute
import java.time.ZonedDateTime
import java.util.*

/**
 * Domain model for an entity instance.
 */
data class Entity(
    val id: UUID,
    val workspaceId: UUID,
    val typeId: UUID,

    val payload: Map<UUID, EntityAttribute>,
    val icon: Icon = Icon(
        type = IconType.FILE,
        colour = IconColour.NEUTRAL
    ),
    val validationErrors: List<String>? = null,
    // A unique identifier key for the entity within its type, taken and synced from EntityType for easier lookup
    val identifierKey: UUID,
    override var createdAt: ZonedDateTime? = null,
    override var updatedAt: ZonedDateTime? = null,
    override var createdBy: UUID? = null,
    override var updatedBy: UUID? = null,
    val sourceType: SourceType = SourceType.USER_CREATED,
    val sourceIntegrationId: UUID? = null,
    val sourceExternalId: String? = null,
    val sourceUrl: String? = null,
    val firstSyncedAt: ZonedDateTime? = null,
    val lastSyncedAt: ZonedDateTime? = null,
    val syncVersion: Long = 0,
    val noteCount: Int = 0,
) : AuditableModel {
    val identifier: String
        get() = this.payload[identifierKey].toString()
}