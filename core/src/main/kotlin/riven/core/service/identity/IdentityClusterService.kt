package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.identity.IdentityClusterEntity
import riven.core.entity.identity.IdentityClusterMemberEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.integration.SourceType
import riven.core.enums.util.OperationType
import riven.core.exceptions.ConflictException
import riven.core.models.identity.IdentityCluster
import riven.core.models.request.entity.AddRelationshipRequest
import riven.core.models.request.identity.AddClusterMemberRequest
import riven.core.models.request.identity.RenameClusterRequest
import riven.core.models.response.identity.ClusterDetailResponse
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.identity.IdentityClusterRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityRelationshipService
import riven.core.service.entity.EntityService
import riven.core.util.ServiceUtil.findOrThrow
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Handles cluster lifecycle operations for the identity domain.
 *
 * Provides three categories of operations:
 * - Cluster resolution: 5-case cluster membership logic called by [IdentityConfirmationService] on confirm
 * - Manual add: adding an entity to an existing cluster
 * - Rename: updating an existing cluster's name
 */
@Service
class IdentityClusterService(
    private val clusterRepository: IdentityClusterRepository,
    private val memberRepository: IdentityClusterMemberRepository,
    private val entityRelationshipService: EntityRelationshipService,
    private val entityService: EntityService,
    private val identityReadService: IdentityReadService,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val logger: KLogger,
) {

    // ------ Public mutations ------

    /**
     * Manually adds an entity to an existing identity cluster.
     *
     * Creates a CONNECTED_ENTITIES relationship between the new entity and the specified
     * target member, saves the new cluster membership, increments memberCount, and logs activity.
     *
     * @param workspaceId The workspace owning the cluster.
     * @param clusterId The cluster to add the entity to.
     * @param request The entity to add and the target member to relate it to.
     * @return The updated [ClusterDetailResponse] after the entity is added.
     * @throws riven.core.exceptions.NotFoundException if the cluster, entity, or target member is not found.
     * @throws ConflictException if the entity is already a member of any cluster.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun addEntityToCluster(workspaceId: UUID, clusterId: UUID, request: AddClusterMemberRequest): ClusterDetailResponse {
        val userId = authTokenService.getUserId()
        val cluster = findOrThrow { clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId) }

        verifyEntityInWorkspace(workspaceId, request.entityId)
        checkNotAlreadyInCluster(request.entityId)
        verifyTargetMemberInCluster(clusterId, request.targetMemberId)

        entityRelationshipService.addRelationship(
            workspaceId = workspaceId,
            sourceEntityId = request.entityId,
            request = AddRelationshipRequest(
                targetEntityId = request.targetMemberId,
                definitionId = null,
                linkSource = SourceType.IDENTITY_MATCH,
            ),
        )

        memberRepository.save(
            IdentityClusterMemberEntity(
                clusterId = clusterId,
                entityId = request.entityId,
                joinedBy = userId,
            )
        )

        cluster.memberCount += 1
        clusterRepository.save(cluster)

        activityService.logActivity(
            activity = Activity.IDENTITY_CLUSTER,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.IDENTITY_CLUSTER,
            entityId = clusterId,
            details = mapOf(
                "action" to "member_added",
                "entityId" to request.entityId.toString(),
                "targetMemberId" to request.targetMemberId.toString(),
            ),
        )

        logger.info { "Manually added entity ${request.entityId} to cluster $clusterId" }

        return identityReadService.getClusterDetail(workspaceId, clusterId)
    }

    /**
     * Renames an identity cluster.
     *
     * @param workspaceId The workspace owning the cluster.
     * @param clusterId The cluster to rename.
     * @param request The new name for the cluster.
     * @return The updated [IdentityCluster] domain model.
     * @throws riven.core.exceptions.NotFoundException if the cluster is not found in the given workspace.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun renameCluster(workspaceId: UUID, clusterId: UUID, request: RenameClusterRequest): IdentityCluster {
        val userId = authTokenService.getUserId()
        val cluster = findOrThrow { clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId) }

        val oldName = cluster.name
        cluster.name = request.name
        val saved = clusterRepository.save(cluster)

        activityService.logActivity(
            activity = Activity.IDENTITY_CLUSTER,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.IDENTITY_CLUSTER,
            entityId = clusterId,
            details = mapOf(
                "oldName" to oldName,
                "newName" to request.name,
            ),
        )

        logger.info { "Renamed cluster $clusterId from '$oldName' to '${request.name}'" }

        return saved.toModel()
    }

    // ------ Cluster resolution ------

    /**
     * Resolves the identity cluster membership for a confirmed suggestion using the 5-case logic:
     *
     * - Case 5: both in same cluster — no mutations, return existing cluster
     * - Case 4: both in different clusters — merge smaller into larger, return surviving
     * - Case 2: source clustered only — add target to source cluster, return cluster
     * - Case 3: target clustered only — add source to target cluster, return cluster
     * - Case 1: neither clustered — create new cluster with both members, return new cluster
     *
     * Activity logging for each case is handled internally.
     *
     * @param workspaceId The workspace owning the entities.
     * @param sourceEntityId The source entity from the confirmed suggestion.
     * @param targetEntityId The target entity from the confirmed suggestion.
     * @param clusterName Optional name for a newly created cluster (derived from NAME signal).
     * @param userId The user who confirmed the suggestion.
     * @return The resulting [IdentityClusterEntity] after resolution.
     */
    @Transactional
    fun resolveClusterMembership(
        workspaceId: UUID,
        sourceEntityId: UUID,
        targetEntityId: UUID,
        clusterName: String?,
        userId: UUID,
    ): IdentityClusterEntity {
        val sourceMember = memberRepository.findByEntityId(sourceEntityId)
        val targetMember = memberRepository.findByEntityId(targetEntityId)

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
                logClusterActivity(surviving, OperationType.UPDATE, userId, workspaceId, mapOf(
                    "action" to "merge_surviving",
                    "dissolvedClusterId" to dissolving.id.toString(),
                    "newMemberCount" to surviving.memberCount,
                ))
                logClusterActivity(dissolving, OperationType.DELETE, userId, workspaceId, mapOf(
                    "action" to "merge_dissolved",
                    "survivingClusterId" to surviving.id.toString(),
                ))
                surviving
            }

            // Case 2: source clustered, target not — add target to source cluster
            sourceMember != null -> {
                logger.debug { "Cluster case 2: adding target $targetEntityId to cluster ${sourceMember.clusterId}" }
                val cluster = findOrThrow { clusterRepository.findById(sourceMember.clusterId) }
                val updated = addMemberToExistingCluster(cluster, targetEntityId, userId)
                logClusterActivity(updated, OperationType.UPDATE, userId, workspaceId, mapOf(
                    "action" to "member_added",
                    "entityId" to targetEntityId.toString(),
                    "newMemberCount" to updated.memberCount,
                ))
                updated
            }

            // Case 3: target clustered, source not — add source to target cluster
            targetMember != null -> {
                logger.debug { "Cluster case 3: adding source $sourceEntityId to cluster ${targetMember.clusterId}" }
                val cluster = findOrThrow { clusterRepository.findById(targetMember.clusterId) }
                val updated = addMemberToExistingCluster(cluster, sourceEntityId, userId)
                logClusterActivity(updated, OperationType.UPDATE, userId, workspaceId, mapOf(
                    "action" to "member_added",
                    "entityId" to sourceEntityId.toString(),
                    "newMemberCount" to updated.memberCount,
                ))
                updated
            }

            // Case 1: neither clustered — create new cluster
            else -> {
                logger.debug { "Cluster case 1: creating new cluster for $sourceEntityId <-> $targetEntityId" }
                val cluster = createClusterWithMembers(workspaceId, sourceEntityId, targetEntityId, clusterName, userId)
                logClusterActivity(cluster, OperationType.CREATE, userId, workspaceId, mapOf(
                    "action" to "created",
                    "memberCount" to 2,
                ))
                cluster
            }
        }
    }

    // ------ Cluster resolution helpers ------

    private fun createClusterWithMembers(
        workspaceId: UUID,
        sourceEntityId: UUID,
        targetEntityId: UUID,
        clusterName: String?,
        userId: UUID,
    ): IdentityClusterEntity {
        val cluster = IdentityClusterEntity(
            workspaceId = workspaceId,
            name = clusterName,
            memberCount = 2,
        )
        val savedCluster = clusterRepository.save(cluster)
        val savedClusterId = requireNotNull(savedCluster.id) { "Saved cluster ID must not be null" }

        memberRepository.save(
            IdentityClusterMemberEntity(
                clusterId = savedClusterId,
                entityId = sourceEntityId,
                joinedBy = userId,
            )
        )
        memberRepository.save(
            IdentityClusterMemberEntity(
                clusterId = savedClusterId,
                entityId = targetEntityId,
                joinedBy = userId,
            )
        )

        return savedCluster
    }

    private fun addMemberToExistingCluster(cluster: IdentityClusterEntity, entityId: UUID, userId: UUID): IdentityClusterEntity {
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

    // ------ Manual add helpers ------

    /**
     * Verifies that the entity exists and belongs to the given workspace.
     * Returns 404 for both missing and wrong-workspace entities to prevent information leakage.
     */
    private fun verifyEntityInWorkspace(workspaceId: UUID, entityId: UUID) {
        val entity = entityService.getEntitiesByIds(setOf(entityId)).firstOrNull()
        if (entity == null || entity.workspaceId != workspaceId) {
            throw riven.core.exceptions.NotFoundException("Entity $entityId not found")
        }
    }

    /**
     * Throws [ConflictException] if the entity is already a member of any cluster.
     */
    private fun checkNotAlreadyInCluster(entityId: UUID) {
        val existing = memberRepository.findByEntityId(entityId)
        if (existing != null) {
            throw ConflictException("Entity is already a member of cluster ${existing.clusterId}")
        }
    }

    /**
     * Verifies that the target member entity is a member of the specified cluster.
     */
    private fun verifyTargetMemberInCluster(clusterId: UUID, targetMemberId: UUID) {
        memberRepository.findByClusterIdAndEntityId(clusterId, targetMemberId)
            ?: throw riven.core.exceptions.NotFoundException("Target member $targetMemberId is not in cluster $clusterId")
    }
}
