package riven.core.models.integration.sync

import java.util.UUID

/**
 * Pending relationship data collected during Pass 1 of the sync upsert process.
 *
 * During record processing (Pass 1), relationship fields are collected rather than
 * immediately resolved. Pass 2 then resolves all pending relationships in batch,
 * ensuring targets within the same sync batch are visible regardless of processing order.
 *
 * @property sourceEntityId UUID of the entity that has the relationship
 * @property relationshipDefinitionKey Key identifying the relationship definition on the entity type
 * @property targetExternalIds List of external IDs that the source entity should be related to
 */
data class RelationshipPending(
    val sourceEntityId: UUID,
    val relationshipDefinitionKey: String,
    val targetExternalIds: List<String>
)
