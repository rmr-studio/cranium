package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.identity.IdentityClusterEntity
import riven.core.entity.identity.IdentityClusterMemberEntity
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
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.identity.IdentityClusterRepository
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
 * - A CONNECTED_ENTITIES relationship between the two entities
 * - 5-case cluster resolution (create, expand, or merge identity clusters)
 * - Activity logging and workspace-wide notification
 *
 * Rejection transitions the suggestion to REJECTED with a signal snapshot for re-suggestion context.
 */
@Service
class IdentityConfirmationService(
    private val matchSuggestionRepository: MatchSuggestionRepository,
    private val clusterRepository: IdentityClusterRepository,
    private val memberRepository: IdentityClusterMemberRepository,
    private val entityRelationshipService: EntityRelationshipService,
    private val notificationService: NotificationService,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val logger: KLogger,
) {

    // ------ Public mutations ------

    /**
     * Confirms a PENDING match suggestion, creating a CONNECTED_ENTITIES relationship
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

        validateConfirmable(entity)
        createRelationship(entity)

        val cluster = resolveCluster(entity, userId)

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

        validateRejectable(entity)
        applyRejection(entity, userId)
        val saved = matchSuggestionRepository.save(entity)

        logRejectionActivity(saved, userId)

        logger.info { "Rejected suggestion ${saved.id}: ${saved.sourceEntityId} <-> ${saved.targetEntityId}" }

        return saved.toModel()
    }

    // ------ State validation ------

    private fun validateConfirmable(entity: MatchSuggestionEntity) {
        if (entity.status != MatchSuggestionStatus.PENDING) {
            throw ConflictException("Suggestion is already ${entity.status}")
        }
    }

    private fun validateRejectable(entity: MatchSuggestionEntity) {
        if (entity.status != MatchSuggestionStatus.PENDING) {
            throw ConflictException("Suggestion is already ${entity.status}")
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

    // ------ Cluster management ------

    /**
     * Resolves the identity cluster for the confirmed suggestion using the 5-case logic:
     *
     * - Case 5: both in same cluster — no mutations, return existing cluster
     * - Case 4: both in different clusters — merge smaller into larger, return surviving
     * - Case 2: source clustered only — add target to source cluster, return cluster
     * - Case 3: target clustered only — add source to target cluster, return cluster
     * - Case 1: neither clustered — create new cluster with both members, return new cluster
     */
    private fun resolveCluster(entity: MatchSuggestionEntity, userId: UUID): IdentityClusterEntity {
        val sourceMember = memberRepository.findByEntityId(entity.sourceEntityId)
        val targetMember = memberRepository.findByEntityId(entity.targetEntityId)

        return when {
            // Case 5: both already in the same cluster — no-op
            sourceMember != null && targetMember != null && sourceMember.clusterId == targetMember.clusterId -> {
                logger.debug { "Cluster case 5: both entities in same cluster ${sourceMember.clusterId}" }
                findOrThrow { clusterRepository.findById(sourceMember.clusterId) }
            }

            // Case 4: both in different clusters — merge
            sourceMember != null && targetMember != null -> {
                logger.debug { "Cluster case 4: merging clusters ${sourceMember.clusterId} and ${targetMember.clusterId}" }
                val (surviving, dissolving) = mergeClusters(sourceMember.clusterId, targetMember.clusterId, userId)
                logClusterActivity(surviving, OperationType.UPDATE, userId, entity.workspaceId, mapOf(
                    "action" to "merge_surviving",
                    "dissolvedClusterId" to dissolving.id.toString(),
                    "newMemberCount" to surviving.memberCount,
                ))
                logClusterActivity(dissolving, OperationType.DELETE, userId, entity.workspaceId, mapOf(
                    "action" to "merge_dissolved",
                    "survivingClusterId" to surviving.id.toString(),
                ))
                surviving
            }

            // Case 2: source clustered, target not — add target to source cluster
            sourceMember != null -> {
                logger.debug { "Cluster case 2: adding target ${entity.targetEntityId} to cluster ${sourceMember.clusterId}" }
                val cluster = findOrThrow { clusterRepository.findById(sourceMember.clusterId) }
                val updated = addMemberToCluster(cluster, entity.targetEntityId, userId)
                logClusterActivity(updated, OperationType.UPDATE, userId, entity.workspaceId, mapOf(
                    "action" to "member_added",
                    "entityId" to entity.targetEntityId.toString(),
                    "newMemberCount" to updated.memberCount,
                ))
                updated
            }

            // Case 3: target clustered, source not — add source to target cluster
            targetMember != null -> {
                logger.debug { "Cluster case 3: adding source ${entity.sourceEntityId} to cluster ${targetMember.clusterId}" }
                val cluster = findOrThrow { clusterRepository.findById(targetMember.clusterId) }
                val updated = addMemberToCluster(cluster, entity.sourceEntityId, userId)
                logClusterActivity(updated, OperationType.UPDATE, userId, entity.workspaceId, mapOf(
                    "action" to "member_added",
                    "entityId" to entity.sourceEntityId.toString(),
                    "newMemberCount" to updated.memberCount,
                ))
                updated
            }

            // Case 1: neither clustered — create new cluster
            else -> {
                logger.debug { "Cluster case 1: creating new cluster for ${entity.sourceEntityId} <-> ${entity.targetEntityId}" }
                val cluster = createClusterWithMembers(entity.workspaceId, entity.sourceEntityId, entity.targetEntityId, entity, userId)
                logClusterActivity(cluster, OperationType.CREATE, userId, entity.workspaceId, mapOf(
                    "action" to "created",
                    "memberCount" to 2,
                ))
                cluster
            }
        }
    }

    private fun createClusterWithMembers(
        workspaceId: UUID,
        sourceEntityId: UUID,
        targetEntityId: UUID,
        entity: MatchSuggestionEntity,
        userId: UUID,
    ): IdentityClusterEntity {
        val cluster = IdentityClusterEntity(
            workspaceId = workspaceId,
            name = resolveClusterName(entity),
            memberCount = 2,
        )
        val savedCluster = clusterRepository.save(cluster)

        memberRepository.save(
            IdentityClusterMemberEntity(
                clusterId = requireNotNull(savedCluster.id) { "Saved cluster ID must not be null" },
                entityId = sourceEntityId,
                joinedBy = userId,
            )
        )
        memberRepository.save(
            IdentityClusterMemberEntity(
                clusterId = requireNotNull(savedCluster.id) { "Saved cluster ID must not be null" },
                entityId = targetEntityId,
                joinedBy = userId,
            )
        )

        return savedCluster
    }

    private fun addMemberToCluster(cluster: IdentityClusterEntity, entityId: UUID, userId: UUID): IdentityClusterEntity {
        val clusterId = requireNotNull(cluster.id) { "Cluster ID must not be null when adding member" }
        memberRepository.save(
            IdentityClusterMemberEntity(
                clusterId = clusterId,
                entityId = entityId,
                joinedBy = userId,
            )
        )
        cluster.memberCount += 1
        return clusterRepository.save(cluster)
    }

    /**
     * Merges the dissolving cluster into the surviving cluster.
     *
     * Surviving cluster = higher memberCount. On tie, source entity's cluster survives.
     * Dissolving cluster members are hard-deleted and re-inserted into the surviving cluster,
     * preserving original joinedAt and joinedBy. Dissolving cluster is soft-deleted.
     *
     * @return Pair of (surviving cluster, dissolving cluster) for activity logging.
     */
    private fun mergeClusters(sourceClusterId: UUID, targetClusterId: UUID, userId: UUID): Pair<IdentityClusterEntity, IdentityClusterEntity> {
        val sourceCluster = findOrThrow { clusterRepository.findById(sourceClusterId) }
        val targetCluster = findOrThrow { clusterRepository.findById(targetClusterId) }

        val (survivingCluster, dissolvingCluster) = if (targetCluster.memberCount > sourceCluster.memberCount) {
            targetCluster to sourceCluster
        } else {
            // Source cluster survives on tie (or when source has more members)
            sourceCluster to targetCluster
        }

        val dissolvingClusterId = requireNotNull(dissolvingCluster.id) { "Dissolving cluster ID must not be null" }
        val survivingClusterId = requireNotNull(survivingCluster.id) { "Surviving cluster ID must not be null" }

        val dissolvingMembers = memberRepository.findByClusterId(dissolvingClusterId)

        // Hard-delete the dissolving cluster's members
        memberRepository.deleteByClusterId(dissolvingClusterId)

        // Re-insert them into the surviving cluster preserving original join metadata
        dissolvingMembers.forEach { member ->
            memberRepository.save(
                IdentityClusterMemberEntity(
                    clusterId = survivingClusterId,
                    entityId = member.entityId,
                    joinedAt = member.joinedAt,
                    joinedBy = member.joinedBy,
                )
            )
        }

        // Update member count on surviving cluster
        survivingCluster.memberCount += dissolvingMembers.size
        clusterRepository.save(survivingCluster)

        // Soft-delete the dissolving cluster
        dissolvingCluster.deleted = true
        dissolvingCluster.deletedAt = ZonedDateTime.now()
        clusterRepository.save(dissolvingCluster)

        logger.info { "Merged cluster $dissolvingClusterId into $survivingClusterId (${dissolvingMembers.size} members moved)" }

        return survivingCluster to dissolvingCluster
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
