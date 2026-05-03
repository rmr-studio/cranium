package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.identity.IdentityClusterEntity
import riven.core.entity.identity.MatchSuggestionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.identity.MatchSuggestionStatus
import riven.core.enums.integration.SourceType
import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.enums.notification.ReviewPriority
import riven.core.enums.util.OperationType
import riven.core.exceptions.ConflictException
import riven.core.exceptions.NotFoundException
import riven.core.models.identity.MatchSuggestion
import riven.core.models.notification.NotificationContent
import riven.core.models.request.entity.AddRelationshipRequest
import riven.core.models.request.notification.CreateNotificationRequest
import riven.core.repository.identity.MatchSuggestionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityRelationshipService
import riven.core.service.notification.NotificationService
import riven.core.util.ServiceUtil.findOrThrow
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Manages the human decision path for identity match suggestions.
 *
 * Handles confirming and rejecting match suggestions. Confirmation triggers:
 * - A SYSTEM_CONNECTION relationship between the two entities
 * - 5-case cluster resolution (create, expand, or merge identity clusters)
 * - Activity logging and workspace-wide notification
 *
 * Rejection transitions the suggestion to REJECTED with a signal snapshot for re-suggestion context.
 */
@Service
class IdentityConfirmationService(
    private val matchSuggestionRepository: MatchSuggestionRepository,
    private val identityClusterService: IdentityClusterService,
    private val entityRelationshipService: EntityRelationshipService,
    private val notificationService: NotificationService,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val logger: KLogger,
) {

    // ------ Public mutations ------

    /**
     * Confirms a PENDING match suggestion, creating a SYSTEM_CONNECTION relationship
     * and managing the 5-case identity cluster resolution.
     *
     * @param workspaceId The workspace owning the suggestion.
     * @param suggestionId The UUID of the suggestion to confirm.
     * @return The updated [MatchSuggestion] with CONFIRMED status.
     * @throws riven.core.exceptions.NotFoundException if the suggestion does not exist.
     * @throws ConflictException if the suggestion is not in PENDING status.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun confirmSuggestion(workspaceId: UUID, suggestionId: UUID): MatchSuggestion {
        val userId = authTokenService.getUserId()
        val entity = findOrThrow { matchSuggestionRepository.findById(suggestionId) }

        if (entity.workspaceId != workspaceId) {
            throw NotFoundException("Suggestion not found")
        }

        validatePending(entity)
        createRelationship(entity)

        val cluster = identityClusterService.resolveClusterMembership(
            workspaceId = entity.workspaceId,
            sourceEntityId = entity.sourceEntityId,
            targetEntityId = entity.targetEntityId,
            clusterName = resolveClusterName(entity),
            userId = userId,
        )

        entity.status = MatchSuggestionStatus.CONFIRMED
        entity.resolvedBy = userId
        entity.resolvedAt = ZonedDateTime.now()
        val saved = matchSuggestionRepository.save(entity)

        logConfirmationActivity(saved, userId, cluster)
        publishConfirmationNotification(saved, cluster)

        logger.info { "Confirmed suggestion ${saved.id}: ${saved.sourceEntityId} <-> ${saved.targetEntityId}, cluster=${cluster.id}" }

        return saved.toModel()
    }

    /**
     * Rejects a PENDING match suggestion, writing a signal snapshot and soft-deleting the row.
     *
     * The rejectionSignals snapshot preserves the signal state at rejection time, enabling
     * re-suggestion checks when a higher-scored candidate emerges later.
     *
     * @param workspaceId The workspace owning the suggestion.
     * @param suggestionId The UUID of the suggestion to reject.
     * @return The updated [MatchSuggestion] with REJECTED status.
     * @throws riven.core.exceptions.NotFoundException if the suggestion does not exist.
     * @throws ConflictException if the suggestion is not in PENDING status.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun rejectSuggestion(workspaceId: UUID, suggestionId: UUID): MatchSuggestion {
        val userId = authTokenService.getUserId()
        val entity = findOrThrow { matchSuggestionRepository.findById(suggestionId) }

        if (entity.workspaceId != workspaceId) {
            throw NotFoundException("Suggestion not found")
        }

        validatePending(entity)
        applyRejection(entity, userId)
        val saved = matchSuggestionRepository.save(entity)

        logRejectionActivity(saved, userId)

        logger.info { "Rejected suggestion ${saved.id}: ${saved.sourceEntityId} <-> ${saved.targetEntityId}" }

        return saved.toModel()
    }

    // ------ State validation ------

    private fun validatePending(entity: MatchSuggestionEntity) {
        if (entity.status != MatchSuggestionStatus.PENDING) {
            throw ConflictException("Suggestion is already ${entity.status.name.lowercase()} and cannot be modified")
        }
    }

    // ------ Relationship creation ------

    private fun createRelationship(entity: MatchSuggestionEntity) {
        try {
            entityRelationshipService.addRelationship(
                workspaceId = entity.workspaceId,
                sourceEntityId = entity.sourceEntityId,
                request = AddRelationshipRequest(
                    targetEntityId = entity.targetEntityId,
                    definitionId = null,
                    linkSource = SourceType.IDENTITY_MATCH,
                ),
            )
        } catch (e: ConflictException) {
            logger.debug { "Relationship already exists for ${entity.sourceEntityId} <-> ${entity.targetEntityId}, continuing confirmation" }
        }
    }

    // ------ Cluster naming ------

    /**
     * Resolves a display name for a new cluster from the NAME signal in the suggestion signals list.
     * Returns null if no NAME signal is present (IdentityClusterEntity.name is nullable).
     */
    private fun resolveClusterName(entity: MatchSuggestionEntity): String? =
        entity.signals
            .firstOrNull { it["type"] == "NAME" }
            ?.get("sourceValue") as? String

    // ------ Activity logging ------

    private fun logConfirmationActivity(entity: MatchSuggestionEntity, userId: UUID, cluster: IdentityClusterEntity) {
        activityService.logActivity(
            activity = Activity.MATCH_SUGGESTION,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = entity.workspaceId,
            entityType = ApplicationEntityType.MATCH_SUGGESTION,
            entityId = requireNotNull(entity.id) { "Suggestion ID must not be null when logging confirmation" },
            details = mapOf(
                "action" to "confirmed",
                "sourceEntityId" to entity.sourceEntityId.toString(),
                "targetEntityId" to entity.targetEntityId.toString(),
                "clusterId" to cluster.id?.toString(),
                "clusterMemberCount" to cluster.memberCount,
            ),
        )
    }

    private fun logRejectionActivity(entity: MatchSuggestionEntity, userId: UUID) {
        activityService.logActivity(
            activity = Activity.MATCH_SUGGESTION,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = entity.workspaceId,
            entityType = ApplicationEntityType.MATCH_SUGGESTION,
            entityId = requireNotNull(entity.id) { "Suggestion ID must not be null when logging rejection" },
            details = mapOf(
                "action" to "rejected",
                "sourceEntityId" to entity.sourceEntityId.toString(),
                "targetEntityId" to entity.targetEntityId.toString(),
                "confidenceScore" to entity.confidenceScore.toDouble(),
            ),
        )
    }

    private fun logClusterActivity(
        cluster: IdentityClusterEntity,
        operation: OperationType,
        userId: UUID,
        workspaceId: UUID,
        details: Map<String, Any?>,
    ) {
        activityService.logActivity(
            activity = Activity.IDENTITY_CLUSTER,
            operation = operation,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.IDENTITY_CLUSTER,
            entityId = cluster.id,
            details = details,
        )
    }

    // ------ Notification ------

    /**
     * Publishes a REVIEW_REQUEST broadcast notification to all workspace members on confirm.
     *
     * userId=null targets all workspace members (broadcast).
     * referenceId is the suggestion ID so the frontend can navigate to the entity resolution view.
     */
    private fun publishConfirmationNotification(entity: MatchSuggestionEntity, cluster: IdentityClusterEntity) {
        val suggestionId = requireNotNull(entity.id) { "Suggestion ID must not be null when publishing notification" }
        val sourceName = resolveEntityDisplayName(entity.sourceEntityId, entity.signals, "source")
        val targetName = resolveEntityDisplayName(entity.targetEntityId, entity.signals, "target")

        notificationService.createInternalNotification(
            CreateNotificationRequest(
                workspaceId = entity.workspaceId,
                userId = null,
                type = NotificationType.REVIEW_REQUEST,
                content = NotificationContent.ReviewRequest(
                    title = "Identity match confirmed",
                    message = "$sourceName and $targetName have been linked. Identity cluster now has ${cluster.memberCount} members.",
                    priority = ReviewPriority.NORMAL,
                ),
                referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
                referenceId = suggestionId,
            )
        )
    }

    /**
     * Returns a display name for an entity using the NAME signal if available,
     * or falls back to "Entity {id short form}".
     */
    private fun resolveEntityDisplayName(entityId: UUID, signals: List<Map<String, Any?>>, valueKey: String): String {
        val nameSignal = signals.firstOrNull { it["type"] == "NAME" }
        val signalValue = nameSignal?.get("${valueKey}Value") as? String
        return signalValue ?: "Entity ${entityId.toString().take(8)}"
    }

    // ------ Rejection state application ------

    private fun applyRejection(entity: MatchSuggestionEntity, userId: UUID) {
        entity.rejectionSignals = mapOf(
            "signals" to entity.signals,
            "confidenceScore" to entity.confidenceScore.toDouble(),
        )
        entity.status = MatchSuggestionStatus.REJECTED
        entity.resolvedBy = userId
        entity.resolvedAt = ZonedDateTime.now()
        entity.deleted = true
        entity.deletedAt = ZonedDateTime.now()
    }
}
