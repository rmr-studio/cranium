package cranium.core.service.entity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import cranium.core.models.identity.IdentityMatchTriggerEvent
import cranium.core.service.identity.EntityTypeClassificationService
import cranium.core.service.identity.IdentityMatchQueueService

/**
 * Listens for [IdentityMatchTriggerEvent] after entity saves commit and decides
 * whether to enqueue an IDENTITY_MATCH job.
 *
 * Decision logic:
 * 1. Skip if the entity type has no IDENTIFIER-classified attributes — prevents queue noise
 *    from entity types with no matchable signals.
 * 2. On update, skip if no IDENTIFIER-classified attribute values changed — updating a
 *    'notes' field should not re-trigger identity matching.
 * 3. Otherwise enqueue via [IdentityMatchQueueService.enqueueIfNotPending].
 *
 * On create, always enqueue if the entity type has IDENTIFIER attributes, even if the
 * new attribute values map is empty (per CONTEXT.md locked decision).
 *
 * The listener is placed in `service.entity` because it consumes entity domain events,
 * following the same pattern as [cranium.core.service.analytics.WorkspaceAnalyticsListener]
 * which lives in `service.analytics`.
 *
 * `@Transactional(REQUIRES_NEW)` is mandatory: after AFTER_COMMIT there is no active
 * transaction; without REQUIRES_NEW the repository.save() in [IdentityMatchQueueService]
 * would throw or silently no-op.
 */
@Component
class IdentityMatchTriggerListener(
    private val entityTypeClassificationService: EntityTypeClassificationService,
    private val identityMatchQueueService: IdentityMatchQueueService,
    private val logger: KLogger,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onEntitySaved(event: IdentityMatchTriggerEvent) {
        if (!entityTypeClassificationService.hasIdentifierAttributes(event.entityTypeId)) {
            logger.debug { "Skipping identity match for entity ${event.entityId} — entity type ${event.entityTypeId} has no IDENTIFIER attributes" }
            return
        }

        if (event.isUpdate && !identifierAttributesChanged(event)) {
            logger.debug { "Skipping identity match for entity ${event.entityId} — no IDENTIFIER attribute values changed on update" }
            return
        }

        logger.debug { "Enqueueing IDENTITY_MATCH for entity ${event.entityId} (isUpdate=${event.isUpdate})" }
        identityMatchQueueService.enqueueIfNotPending(event.entityId, event.workspaceId)
    }

    // ------ Private helpers ------

    private fun identifierAttributesChanged(event: IdentityMatchTriggerEvent): Boolean =
        event.newIdentifierAttributes != event.previousIdentifierAttributes
}
