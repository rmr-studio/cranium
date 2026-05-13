package cranium.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import cranium.core.entity.entity.EntityEntity
import cranium.core.entity.identity.IdentityClusterMemberEntity
import cranium.core.exceptions.NotFoundException
import cranium.core.models.response.identity.ClusterDetailResponse
import cranium.core.models.response.identity.ClusterMemberContext
import cranium.core.models.response.identity.ClusterSummaryResponse
import cranium.core.models.response.identity.PendingMatchCountResponse
import cranium.core.models.response.identity.SuggestionResponse
import cranium.core.repository.identity.IdentityClusterMemberRepository
import cranium.core.repository.identity.IdentityClusterRepository
import cranium.core.repository.identity.MatchSuggestionRepository
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.entity.EntityService
import cranium.core.util.ServiceUtil.findOrThrow
import java.util.UUID

/**
 * Read-only query service for the identity domain.
 *
 * Exposes suggestion and cluster data for the public REST API. All mutations live
 * in [IdentityConfirmationService]. No activity logging is required for reads.
 */
@Service
class IdentityReadService(
    private val matchSuggestionRepository: MatchSuggestionRepository,
    private val clusterRepository: IdentityClusterRepository,
    private val memberRepository: IdentityClusterMemberRepository,
    private val entityService: EntityService,
    private val authTokenService: AuthTokenService,
    private val logger: KLogger,
) {

    // ------ Suggestion read operations ------

    /**
     * Returns all non-deleted suggestions (PENDING + CONFIRMED) for the given workspace.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun listSuggestions(workspaceId: UUID): List<SuggestionResponse> {
        val userId = authTokenService.getUserId()
        logger.debug { "listSuggestions: workspaceId=$workspaceId userId=$userId" }

        return matchSuggestionRepository.findByWorkspaceId(workspaceId)
            .map { it.toModel() }
            .map { SuggestionResponse.from(it) }
    }

    /**
     * Returns a single suggestion by ID, scoped to the given workspace.
     *
     * @throws NotFoundException if the suggestion does not exist or belongs to a different workspace.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getSuggestion(workspaceId: UUID, suggestionId: UUID): SuggestionResponse {
        val userId = authTokenService.getUserId()
        logger.debug { "getSuggestion: workspaceId=$workspaceId suggestionId=$suggestionId userId=$userId" }

        val entity = findOrThrow { matchSuggestionRepository.findById(suggestionId) }
        if (entity.workspaceId != workspaceId) {
            throw NotFoundException("Match suggestion not found: $suggestionId")
        }
        return SuggestionResponse.from(entity.toModel())
    }

    // ------ Cluster read operations ------

    /**
     * Returns all non-deleted clusters for the given workspace as summary items.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun listClusters(workspaceId: UUID): List<ClusterSummaryResponse> {
        val userId = authTokenService.getUserId()
        logger.debug { "listClusters: workspaceId=$workspaceId userId=$userId" }

        return clusterRepository.findByWorkspaceId(workspaceId)
            .map { entity ->
                val model = entity.toModel()
                ClusterSummaryResponse(
                    id = model.id,
                    workspaceId = model.workspaceId,
                    name = model.name,
                    memberCount = model.memberCount,
                    createdAt = model.createdAt,
                )
            }
    }

    /**
     * Returns cluster metadata plus an enriched member list for the given cluster.
     *
     * Member entity fields (typeKey, sourceType, identifierKey) are nullable — if a member
     * entity has been soft-deleted since joining the cluster, those fields will be null.
     *
     * @throws NotFoundException if the cluster does not exist in the given workspace.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getClusterDetail(workspaceId: UUID, clusterId: UUID): ClusterDetailResponse {
        val userId = authTokenService.getUserId()
        logger.debug { "getClusterDetail: workspaceId=$workspaceId clusterId=$clusterId userId=$userId" }

        val clusterEntity = findOrThrow { clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId) }
        val members = memberRepository.findByClusterId(clusterId)
        val enrichedMembers = enrichMembers(members)

        val model = clusterEntity.toModel()
        return ClusterDetailResponse(
            id = model.id,
            workspaceId = model.workspaceId,
            name = model.name,
            memberCount = model.memberCount,
            members = enrichedMembers,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt,
        )
    }

    // ------ Pending match count ------

    /**
     * Returns the count of PENDING suggestions where the given entity is the source or target.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getPendingMatchCount(workspaceId: UUID, entityId: UUID): PendingMatchCountResponse {
        val userId = authTokenService.getUserId()
        logger.debug { "getPendingMatchCount: workspaceId=$workspaceId entityId=$entityId userId=$userId" }

        val count = matchSuggestionRepository.countPendingForEntity(workspaceId, entityId)
        return PendingMatchCountResponse(entityId = entityId, pendingCount = count)
    }

    // ------ Private helpers ------

    /**
     * Batch-loads entity data for a list of cluster members and maps to enriched [ClusterMemberContext].
     *
     * Missing entities (soft-deleted after joining cluster) result in null fields on the context.
     */
    private fun enrichMembers(members: List<IdentityClusterMemberEntity>): List<ClusterMemberContext> {
        val entityIds = members.map { it.entityId }.toSet()
        val entityMap: Map<UUID, EntityEntity> = entityService
            .getEntitiesByIds(entityIds)
            .associateBy { requireNotNull(it.id) { "EntityEntity ID must not be null" } }

        return members.map { member ->
            val entity = entityMap[member.entityId]
            ClusterMemberContext(
                entityId = member.entityId,
                typeKey = entity?.typeKey,
                sourceType = entity?.sourceType,
                identifierKey = entity?.identifierKey,
                joinedAt = member.joinedAt,
            )
        }
    }
}
