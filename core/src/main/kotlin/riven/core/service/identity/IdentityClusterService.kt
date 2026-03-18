package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
import java.util.UUID

/**
 * Handles manual cluster mutation operations for the identity domain.
 *
 * Provides two mutation entry points:
 * - Manually adding an entity to an existing cluster
 * - Renaming an existing cluster
 *
 * Confirmation-driven cluster creation and merge logic lives in [IdentityConfirmationService].
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

    // ------ Private helpers ------

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
