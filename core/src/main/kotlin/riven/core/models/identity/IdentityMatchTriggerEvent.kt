package riven.core.models.identity

import java.util.UUID

/**
 * Domain event published after an entity is saved or updated.
 *
 * Carries the minimum information needed for [riven.core.service.entity.IdentityMatchTriggerListener]
 * to decide whether to enqueue an IDENTITY_MATCH job:
 * - Which entity was saved and in which workspace
 * - Whether this is a create or an update
 * - Old and new values for IDENTIFIER-classified attributes (enables change detection on update)
 *
 * Published by EntityService after commit via [org.springframework.context.ApplicationEventPublisher].
 * Consumed by [riven.core.service.entity.IdentityMatchTriggerListener] via
 * [@TransactionalEventListener(AFTER_COMMIT)][org.springframework.transaction.event.TransactionalEventListener].
 */
data class IdentityMatchTriggerEvent(
    /** The entity that was saved or updated. */
    val entityId: UUID,

    /** Workspace the entity belongs to. */
    val workspaceId: UUID,

    /** Entity type ID — used to look up IDENTIFIER-classified attribute definitions. */
    val entityTypeId: UUID,

    /** True when this event represents an update to an existing entity; false for a create. */
    val isUpdate: Boolean,

    /**
     * Attribute values keyed by attributeId before the save.
     * Only contains IDENTIFIER-classified attributes.
     * Empty map for create events.
     */
    val previousIdentifierAttributes: Map<UUID, Any?>,

    /**
     * Attribute values keyed by attributeId after the save.
     * Only contains IDENTIFIER-classified attributes.
     */
    val newIdentifierAttributes: Map<UUID, Any?>,
)
