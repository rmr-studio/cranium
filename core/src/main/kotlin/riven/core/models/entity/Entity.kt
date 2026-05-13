package cranium.core.models.entity

import cranium.core.entity.util.AuditableModel
import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.integration.SourceType
import cranium.core.models.common.Icon
import cranium.core.models.entity.payload.EntityAttribute
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
    /**
     * Inbound knowledge references grouped by source entity `typeKey`
     * (e.g. `note`, `glossary_term`, `sop`). Each row preserves its
     * `systemType` (`ATTACHMENT` / `MENTION` / `DEFINES`) so the frontend
     * can render badges, panels, and per-kind splits without a follow-up
     * request. Replaces the dropped `entities.note_count` aggregate.
     */
    val knowledgeRefs: Map<String, List<EntityLink>> = emptyMap(),
) : AuditableModel {
    val identifier: String
        get() = this.payload[identifierKey].toString()
}