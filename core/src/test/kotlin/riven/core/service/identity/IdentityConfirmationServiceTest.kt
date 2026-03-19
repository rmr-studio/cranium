package riven.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.identity.IdentityClusterEntity
import riven.core.entity.identity.IdentityClusterMemberEntity
import riven.core.entity.identity.MatchSuggestionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.identity.MatchSuggestionStatus
import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.enums.util.OperationType
import riven.core.exceptions.ConflictException
import riven.core.models.common.json.JsonObject
import riven.core.models.identity.MatchSuggestion
import riven.core.models.notification.Notification
import riven.core.models.request.notification.CreateNotificationRequest
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.identity.IdentityClusterRepository
import riven.core.repository.identity.MatchSuggestionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityRelationshipService
import riven.core.service.notification.NotificationService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.identity.IdentityFactory
import riven.core.enums.workspace.WorkspaceRoles
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [IdentityConfirmationService].
 *
 * Covers CONF-01 through CONF-05:
 * - CONF-01: Confirming creates a CONNECTED_ENTITIES relationship with SourceType.IDENTITY_MATCH
 * - CONF-02: 5-case cluster resolution (neither, source only, target only, different clusters, same cluster)
 * - CONF-03: Cluster merge — smaller dissolved into larger, dissolving cluster soft-deleted
 * - CONF-04: Rejecting transitions PENDING -> REJECTED with resolvedBy/At, rejectionSignals snapshot, soft-delete
 * - CONF-05: Invalid state transitions throw ConflictException (double-confirm, double-reject, cross-transitions)
 *
 * @WithUserPersona is required at class level because this service uses @PreAuthorize and authTokenService.getUserId().
 * JUnit 5 @Nested inner classes do not inherit this annotation — it is re-applied on each Nested class.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        IdentityConfirmationService::class,
        IdentityConfirmationServiceTest.TestConfig::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@example.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.ADMIN
        )
    ]
)
class IdentityConfirmationServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var matchSuggestionRepository: MatchSuggestionRepository

    @MockitoBean
    private lateinit var clusterRepository: IdentityClusterRepository

    @MockitoBean
    private lateinit var memberRepository: IdentityClusterMemberRepository

    @MockitoBean
    private lateinit var entityRelationshipService: EntityRelationshipService

    @MockitoBean
    private lateinit var notificationService: NotificationService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var authTokenService: AuthTokenService

    @Autowired
    private lateinit var service: IdentityConfirmationService

    // ------ ConfirmSuggestion — CONF-01 relationship creation ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class ConfirmSuggestion {

        /**
         * CONF-01: Confirming a PENDING suggestion calls entityRelationshipService.addRelationship
         * with the source entity ID from the suggestion and linkSource=SourceType.IDENTITY_MATCH.
         */
        @Test
        fun `confirmSuggestion calls addRelationship with IDENTITY_MATCH source type`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            stubBasicConfirmFlow(entityWithId)

            service.confirmSuggestion(workspaceId, suggestionId)

            val requestCaptor = argumentCaptor<riven.core.models.request.entity.AddRelationshipRequest>()
            verify(entityRelationshipService).addRelationship(
                eq(workspaceId),
                eq(suggestion.sourceEntityId),
                requestCaptor.capture(),
            )
            val request = requestCaptor.firstValue
            assertEquals(suggestion.targetEntityId, request.targetEntityId)
            assertEquals(riven.core.enums.integration.SourceType.IDENTITY_MATCH, request.linkSource)
        }

        /**
         * CONF-01: Confirming a suggestion sets status=CONFIRMED and resolvedBy=userId.
         * The suggestion is NOT soft-deleted on confirmation (only on rejection).
         */
        @Test
        fun `confirmSuggestion sets CONFIRMED status and resolvedBy without soft-delete`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            stubBasicConfirmFlow(entityWithId)

            val result = service.confirmSuggestion(workspaceId, suggestionId)

            assertEquals(MatchSuggestionStatus.CONFIRMED, result.status)
            assertEquals(userId, result.resolvedBy)
            assertNotNull(result.resolvedAt)
        }

        /**
         * CONF-02, Case 1: When neither entity is in a cluster, a new cluster is created
         * with memberCount=2 and both entities are saved as members.
         */
        @Test
        fun `confirmSuggestion creates new cluster when neither entity is clustered (Case 1)`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(buildRelationshipResponse())
            // Neither entity has a cluster membership
            whenever(memberRepository.findByEntityId(any())).thenReturn(null)
            // Cluster save returns entity with ID
            whenever(clusterRepository.save(any())).thenAnswer { invocation ->
                val cluster = invocation.getArgument<IdentityClusterEntity>(0)
                buildSavedCluster(cluster)
            }
            whenever(memberRepository.save(any<IdentityClusterMemberEntity>())).thenAnswer { invocation ->
                invocation.getArgument<IdentityClusterMemberEntity>(0)
            }
            whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())
            whenever(notificationService.createInternalNotification(any())).thenReturn(buildNotification())

            service.confirmSuggestion(workspaceId, suggestionId)

            // Should save a new cluster
            val clusterCaptor = argumentCaptor<IdentityClusterEntity>()
            verify(clusterRepository).save(clusterCaptor.capture())
            assertEquals(2, clusterCaptor.firstValue.memberCount)

            // Should save 2 member rows
            verify(memberRepository, org.mockito.kotlin.times(2)).save(any<IdentityClusterMemberEntity>())
        }

        /**
         * Activity is logged on confirm with action="confirmed", clusterId, and clusterMemberCount.
         */
        @Test
        fun `confirmSuggestion logs activity with confirmed action`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            stubBasicConfirmFlow(entityWithId)

            service.confirmSuggestion(workspaceId, suggestionId)

            val detailsCaptor = argumentCaptor<JsonObject>()
            verify(activityService).logActivity(
                eq(Activity.MATCH_SUGGESTION),
                eq(OperationType.UPDATE),
                eq(userId),
                eq(workspaceId),
                eq(ApplicationEntityType.MATCH_SUGGESTION),
                any(),
                any(),
                detailsCaptor.capture(),
            )
            val details = detailsCaptor.firstValue
            assertEquals("confirmed", details["action"])
            assertNotNull(details["clusterId"])
            assertNotNull(details["clusterMemberCount"])
        }

        /**
         * Notification is published on confirm with type=REVIEW_REQUEST,
         * referenceType=ENTITY_RESOLUTION, and referenceId=suggestionId.
         */
        @Test
        fun `confirmSuggestion publishes REVIEW_REQUEST notification`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            stubBasicConfirmFlow(entityWithId)

            service.confirmSuggestion(workspaceId, suggestionId)

            val requestCaptor = argumentCaptor<CreateNotificationRequest>()
            verify(notificationService).createInternalNotification(requestCaptor.capture())
            val request = requestCaptor.firstValue
            assertEquals(NotificationType.REVIEW_REQUEST, request.type)
            assertEquals(NotificationReferenceType.ENTITY_RESOLUTION, request.referenceType)
            assertEquals(suggestionId, request.referenceId)
        }
    }

    // ------ ConfirmClusterCases — CONF-02 and CONF-03 ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class ConfirmClusterCases {

        /**
         * CONF-02, Case 2: Source entity is already in a cluster, target is not.
         * Target should be added to the source cluster, and memberCount incremented.
         */
        @Test
        fun `confirmSuggestion adds target to source cluster when source is already clustered (Case 2)`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)
            val existingClusterId = UUID.randomUUID()
            val existingCluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 3),
                existingClusterId,
            )

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(buildRelationshipResponse())
            // Source entity is clustered, target is not
            val sourceMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = existingClusterId,
                entityId = suggestion.sourceEntityId,
            )
            whenever(memberRepository.findByEntityId(suggestion.sourceEntityId)).thenReturn(sourceMember)
            whenever(memberRepository.findByEntityId(suggestion.targetEntityId)).thenReturn(null)
            whenever(clusterRepository.findById(existingClusterId)).thenReturn(Optional.of(existingCluster))
            whenever(memberRepository.save(any<IdentityClusterMemberEntity>())).thenAnswer { invocation ->
                invocation.getArgument<IdentityClusterMemberEntity>(0)
            }
            whenever(clusterRepository.save(any())).thenAnswer { invocation ->
                buildSavedCluster(invocation.getArgument(0))
            }
            whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())
            whenever(notificationService.createInternalNotification(any())).thenReturn(buildNotification())

            service.confirmSuggestion(workspaceId, suggestionId)

            // A new member should be saved for the target entity
            val memberCaptor = argumentCaptor<IdentityClusterMemberEntity>()
            verify(memberRepository).save(memberCaptor.capture())
            assertEquals(existingClusterId, memberCaptor.firstValue.clusterId)
            assertEquals(suggestion.targetEntityId, memberCaptor.firstValue.entityId)
        }

        /**
         * CONF-02, Case 3: Target entity is already in a cluster, source is not.
         * Source should be added to the target cluster.
         */
        @Test
        fun `confirmSuggestion adds source to target cluster when target is already clustered (Case 3)`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)
            val existingClusterId = UUID.randomUUID()
            val existingCluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 2),
                existingClusterId,
            )

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(buildRelationshipResponse())
            // Target entity is clustered, source is not
            whenever(memberRepository.findByEntityId(suggestion.sourceEntityId)).thenReturn(null)
            val targetMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = existingClusterId,
                entityId = suggestion.targetEntityId,
            )
            whenever(memberRepository.findByEntityId(suggestion.targetEntityId)).thenReturn(targetMember)
            whenever(clusterRepository.findById(existingClusterId)).thenReturn(Optional.of(existingCluster))
            whenever(memberRepository.save(any<IdentityClusterMemberEntity>())).thenAnswer { invocation ->
                invocation.getArgument<IdentityClusterMemberEntity>(0)
            }
            whenever(clusterRepository.save(any())).thenAnswer { invocation ->
                buildSavedCluster(invocation.getArgument(0))
            }
            whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())
            whenever(notificationService.createInternalNotification(any())).thenReturn(buildNotification())

            service.confirmSuggestion(workspaceId, suggestionId)

            // A new member should be saved for the source entity
            val memberCaptor = argumentCaptor<IdentityClusterMemberEntity>()
            verify(memberRepository).save(memberCaptor.capture())
            assertEquals(existingClusterId, memberCaptor.firstValue.clusterId)
            assertEquals(suggestion.sourceEntityId, memberCaptor.firstValue.entityId)
        }

        /**
         * CONF-02 + CONF-03, Case 4: Both entities are in different clusters.
         * The smaller cluster is dissolved into the larger cluster (surviving).
         * Dissolving cluster members are hard-deleted and re-inserted, dissolving cluster is soft-deleted.
         */
        @Test
        fun `confirmSuggestion merges smaller cluster into larger when both are in different clusters (Case 4)`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            val largerClusterId = UUID.randomUUID()
            val smallerClusterId = UUID.randomUUID()

            val largerCluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 5),
                largerClusterId,
            )
            val smallerCluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 2),
                smallerClusterId,
            )

            // Source entity is in the larger cluster, target in the smaller
            val sourceMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = largerClusterId,
                entityId = suggestion.sourceEntityId,
            )
            val targetMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = smallerClusterId,
                entityId = suggestion.targetEntityId,
            )
            val dissolvingMembers = listOf(
                IdentityFactory.createIdentityClusterMemberEntity(clusterId = smallerClusterId),
                IdentityFactory.createIdentityClusterMemberEntity(clusterId = smallerClusterId),
            )

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(buildRelationshipResponse())
            whenever(memberRepository.findByEntityId(suggestion.sourceEntityId)).thenReturn(sourceMember)
            whenever(memberRepository.findByEntityId(suggestion.targetEntityId)).thenReturn(targetMember)
            whenever(clusterRepository.findById(largerClusterId)).thenReturn(Optional.of(largerCluster))
            whenever(clusterRepository.findById(smallerClusterId)).thenReturn(Optional.of(smallerCluster))
            whenever(memberRepository.findByClusterId(smallerClusterId)).thenReturn(dissolvingMembers)
            whenever(memberRepository.save(any<IdentityClusterMemberEntity>())).thenAnswer { invocation ->
                invocation.getArgument<IdentityClusterMemberEntity>(0)
            }
            whenever(clusterRepository.save(any())).thenAnswer { invocation ->
                buildSavedCluster(invocation.getArgument(0))
            }
            whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())
            whenever(notificationService.createInternalNotification(any())).thenReturn(buildNotification())

            service.confirmSuggestion(workspaceId, suggestionId)

            // Dissolving members should be hard-deleted
            verify(memberRepository).deleteByClusterId(smallerClusterId)

            // Dissolving members should be re-inserted into the surviving cluster
            verify(memberRepository, org.mockito.kotlin.times(dissolvingMembers.size)).save(any<IdentityClusterMemberEntity>())

            // Dissolving cluster should be soft-deleted
            val clusterCaptor = argumentCaptor<IdentityClusterEntity>()
            verify(clusterRepository, org.mockito.kotlin.times(2)).save(clusterCaptor.capture())
            val savedClusters = clusterCaptor.allValues
            val deletedCluster = savedClusters.find { it.deleted }
            assertNotNull(deletedCluster)
        }

        /**
         * CONF-03: On tie (equal member counts), the source entity's cluster is the surviving cluster.
         */
        @Test
        fun `confirmSuggestion keeps source cluster as survivor on memberCount tie (CONF-03)`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            val sourceClusterId = UUID.randomUUID()
            val targetClusterId = UUID.randomUUID()

            val sourceCluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 3),
                sourceClusterId,
            )
            val targetCluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 3),
                targetClusterId,
            )

            val sourceMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = sourceClusterId,
                entityId = suggestion.sourceEntityId,
            )
            val targetMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = targetClusterId,
                entityId = suggestion.targetEntityId,
            )
            val dissolvingMembers = listOf(
                IdentityFactory.createIdentityClusterMemberEntity(clusterId = targetClusterId),
                IdentityFactory.createIdentityClusterMemberEntity(clusterId = targetClusterId),
                IdentityFactory.createIdentityClusterMemberEntity(clusterId = targetClusterId),
            )

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(buildRelationshipResponse())
            whenever(memberRepository.findByEntityId(suggestion.sourceEntityId)).thenReturn(sourceMember)
            whenever(memberRepository.findByEntityId(suggestion.targetEntityId)).thenReturn(targetMember)
            whenever(clusterRepository.findById(sourceClusterId)).thenReturn(Optional.of(sourceCluster))
            whenever(clusterRepository.findById(targetClusterId)).thenReturn(Optional.of(targetCluster))
            whenever(memberRepository.findByClusterId(targetClusterId)).thenReturn(dissolvingMembers)
            whenever(memberRepository.save(any<IdentityClusterMemberEntity>())).thenAnswer { invocation ->
                invocation.getArgument<IdentityClusterMemberEntity>(0)
            }
            whenever(clusterRepository.save(any())).thenAnswer { invocation ->
                buildSavedCluster(invocation.getArgument(0))
            }
            whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())
            whenever(notificationService.createInternalNotification(any())).thenReturn(buildNotification())

            service.confirmSuggestion(workspaceId, suggestionId)

            // Target cluster (dissolving on tie) should be hard-deleted
            verify(memberRepository).deleteByClusterId(targetClusterId)

            // Source cluster should NOT be deleted
            verify(memberRepository, never()).deleteByClusterId(sourceClusterId)
        }

        /**
         * CONF-02, Case 5: Both entities are already in the same cluster.
         * No cluster mutations should occur — no saves or deletes on cluster repositories.
         */
        @Test
        fun `confirmSuggestion is no-op for clusters when both entities are in the same cluster (Case 5)`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)
            val sharedClusterId = UUID.randomUUID()
            val sharedCluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 2),
                sharedClusterId,
            )

            val sourceMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = sharedClusterId,
                entityId = suggestion.sourceEntityId,
            )
            val targetMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = sharedClusterId,
                entityId = suggestion.targetEntityId,
            )

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(buildRelationshipResponse())
            whenever(memberRepository.findByEntityId(suggestion.sourceEntityId)).thenReturn(sourceMember)
            whenever(memberRepository.findByEntityId(suggestion.targetEntityId)).thenReturn(targetMember)
            // Return shared cluster for the findById call used in resolveCluster
            whenever(clusterRepository.findById(sharedClusterId)).thenReturn(Optional.of(sharedCluster))
            whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())
            whenever(notificationService.createInternalNotification(any())).thenReturn(buildNotification())

            service.confirmSuggestion(workspaceId, suggestionId)

            // No cluster saves or member saves or deletes should happen
            verify(clusterRepository, never()).save(any())
            verify(memberRepository, never()).save(any<IdentityClusterMemberEntity>())
            verify(memberRepository, never()).deleteByClusterId(any())
        }
    }

    // ------ RejectSuggestion — CONF-04 ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class RejectSuggestion {

        /**
         * CONF-04: Rejecting a PENDING suggestion transitions status to REJECTED,
         * sets resolvedBy=userId, resolvedAt=now, and writes a rejectionSignals snapshot.
         */
        @Test
        fun `rejectSuggestion transitions PENDING to REJECTED with resolvedBy and rejectionSignals`() {
            val signals = listOf(IdentityFactory.createMatchSignal().toMap())
            val suggestion = IdentityFactory.createMatchSuggestionEntity(
                workspaceId = workspaceId,
                status = MatchSuggestionStatus.PENDING,
                signals = signals,
            )
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())

            val result = service.rejectSuggestion(workspaceId, suggestionId)

            assertEquals(MatchSuggestionStatus.REJECTED, result.status)
            assertEquals(userId, result.resolvedBy)
            assertNotNull(result.resolvedAt)
            assertNotNull(result.rejectionSignals)
        }

        /**
         * CONF-04: Rejecting a suggestion soft-deletes the row (deleted=true, deletedAt set).
         */
        @Test
        fun `rejectSuggestion soft-deletes the suggestion entity`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())

            val savedCaptor = argumentCaptor<MatchSuggestionEntity>()
            whenever(matchSuggestionRepository.save(savedCaptor.capture())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }

            service.rejectSuggestion(workspaceId, suggestionId)

            val saved = savedCaptor.firstValue
            assertEquals(true, saved.deleted)
            assertNotNull(saved.deletedAt)
        }

        /**
         * CONF-04: Activity is logged for rejection with action="rejected".
         */
        @Test
        fun `rejectSuggestion logs activity with rejected action`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())

            service.rejectSuggestion(workspaceId, suggestionId)

            val detailsCaptor = argumentCaptor<JsonObject>()
            verify(activityService).logActivity(
                eq(Activity.MATCH_SUGGESTION),
                eq(OperationType.UPDATE),
                eq(userId),
                eq(workspaceId),
                eq(ApplicationEntityType.MATCH_SUGGESTION),
                eq(suggestionId),
                any(),
                detailsCaptor.capture(),
            )
            assertEquals("rejected", detailsCaptor.firstValue["action"])
        }

        /**
         * Rejecting does NOT publish a notification (notifications are only for confirmations).
         */
        @Test
        fun `rejectSuggestion does not publish a notification`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
            whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())

            service.rejectSuggestion(workspaceId, suggestionId)

            verify(notificationService, never()).createInternalNotification(any())
        }
    }

    // ------ StateTransitionGuards — CONF-05 ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class StateTransitionGuards {

        /**
         * CONF-05: Confirming an already-CONFIRMED suggestion throws ConflictException.
         */
        @Test
        fun `confirmSuggestion throws ConflictException when suggestion is already CONFIRMED`() {
            val suggestion = IdentityFactory.createMatchSuggestionEntity(
                workspaceId = workspaceId,
                status = MatchSuggestionStatus.CONFIRMED,
            )
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))

            assertThrows<ConflictException> {
                service.confirmSuggestion(workspaceId, suggestionId)
            }
        }

        /**
         * CONF-05: Confirming an already-REJECTED suggestion throws ConflictException.
         */
        @Test
        fun `confirmSuggestion throws ConflictException when suggestion is already REJECTED`() {
            val suggestion = IdentityFactory.createMatchSuggestionEntity(
                workspaceId = workspaceId,
                status = MatchSuggestionStatus.REJECTED,
            )
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))

            assertThrows<ConflictException> {
                service.confirmSuggestion(workspaceId, suggestionId)
            }
        }

        /**
         * CONF-05: Rejecting an already-REJECTED suggestion throws ConflictException.
         */
        @Test
        fun `rejectSuggestion throws ConflictException when suggestion is already REJECTED`() {
            val suggestion = IdentityFactory.createMatchSuggestionEntity(
                workspaceId = workspaceId,
                status = MatchSuggestionStatus.REJECTED,
            )
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))

            assertThrows<ConflictException> {
                service.rejectSuggestion(workspaceId, suggestionId)
            }
        }

        /**
         * CONF-05: Rejecting an already-CONFIRMED suggestion throws ConflictException.
         */
        @Test
        fun `rejectSuggestion throws ConflictException when suggestion is already CONFIRMED`() {
            val suggestion = IdentityFactory.createMatchSuggestionEntity(
                workspaceId = workspaceId,
                status = MatchSuggestionStatus.CONFIRMED,
            )
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))

            assertThrows<ConflictException> {
                service.rejectSuggestion(workspaceId, suggestionId)
            }
        }
    }

    // ------ Foreign Workspace Tests — D1 regression ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class ForeignWorkspaceSuggestionTests {

        /**
         * Bug: require() threw IllegalArgumentException (HTTP 400) when a suggestion belongs to
         * a different workspace, leaking existence info. Fix: replaced with NotFoundException (HTTP 404).
         *
         * Verifies that confirmSuggestion throws NotFoundException when the user has workspace access
         * but the suggestion entity belongs to a different workspace.
         */
        @Test
        fun `confirmSuggestion throws NotFoundException for suggestion in different workspace`() {
            val suggestionId = UUID.randomUUID()
            val foreignWorkspaceId = UUID.randomUUID()
            val entity = IdentityFactory.createMatchSuggestionEntity(
                workspaceId = foreignWorkspaceId,
                status = MatchSuggestionStatus.PENDING,
            )
            val entityWithId = buildEntityWithId(entity, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))

            assertThrows<riven.core.exceptions.NotFoundException> {
                service.confirmSuggestion(workspaceId, suggestionId)
            }
        }

        /**
         * Bug: require() threw IllegalArgumentException (HTTP 400) when a suggestion belongs to
         * a different workspace, leaking existence info. Fix: replaced with NotFoundException (HTTP 404).
         *
         * Verifies that rejectSuggestion throws NotFoundException when the user has workspace access
         * but the suggestion entity belongs to a different workspace.
         */
        @Test
        fun `rejectSuggestion throws NotFoundException for suggestion in different workspace`() {
            val suggestionId = UUID.randomUUID()
            val foreignWorkspaceId = UUID.randomUUID()
            val entity = IdentityFactory.createMatchSuggestionEntity(
                workspaceId = foreignWorkspaceId,
                status = MatchSuggestionStatus.PENDING,
            )
            val entityWithId = buildEntityWithId(entity, suggestionId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))

            assertThrows<riven.core.exceptions.NotFoundException> {
                service.rejectSuggestion(workspaceId, suggestionId)
            }
        }
    }

    // ------ Access Denied Tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "00000000-0000-0000-0000-000000000000",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class UnauthorizedAccessTests {

        /**
         * Verifies that confirmSuggestion rejects requests when the authenticated user
         * does not have access to the target workspace. The @PreAuthorize annotation
         * on the service method should trigger an AccessDeniedException before any
         * business logic executes.
         */
        @Test
        fun `confirmSuggestion throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.confirmSuggestion(workspaceId, UUID.randomUUID())
            }
        }

        /**
         * Verifies that rejectSuggestion rejects requests when the authenticated user
         * does not have access to the target workspace. The @PreAuthorize annotation
         * on the service method should trigger an AccessDeniedException before any
         * business logic executes.
         */
        @Test
        fun `rejectSuggestion throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.rejectSuggestion(workspaceId, UUID.randomUUID())
            }
        }
    }

    // ------ Test helpers ------

    private fun buildPendingSuggestion(): MatchSuggestionEntity =
        IdentityFactory.createMatchSuggestionEntity(
            workspaceId = workspaceId,
            status = MatchSuggestionStatus.PENDING,
            confidenceScore = BigDecimal("0.8500"),
        )

    private fun buildEntityWithId(entity: MatchSuggestionEntity, id: UUID): MatchSuggestionEntity {
        val result = entity.copy(id = id)
        val now = ZonedDateTime.now()
        setAuditField(result, "createdAt", now)
        setAuditField(result, "updatedAt", now)
        return result
    }

    private fun buildSavedEntity(entity: MatchSuggestionEntity): MatchSuggestionEntity {
        val id = entity.id ?: UUID.randomUUID()
        val result = entity.copy(id = id)
        val now = ZonedDateTime.now()
        setAuditField(result, "createdAt", now)
        setAuditField(result, "updatedAt", now)
        return result
    }

    private fun buildSavedCluster(cluster: IdentityClusterEntity): IdentityClusterEntity {
        val id = cluster.id ?: UUID.randomUUID()
        val result = cluster.copy(id = id)
        val now = ZonedDateTime.now()
        setAuditField(result, "createdAt", now)
        setAuditField(result, "updatedAt", now)
        return result
    }

    private fun buildSavedClusterWithId(cluster: IdentityClusterEntity, id: UUID): IdentityClusterEntity {
        val result = cluster.copy(id = id)
        val now = ZonedDateTime.now()
        setAuditField(result, "createdAt", now)
        setAuditField(result, "updatedAt", now)
        return result
    }

    private fun buildRelationshipResponse(): riven.core.models.response.entity.RelationshipResponse =
        riven.core.models.response.entity.RelationshipResponse(
            id = UUID.randomUUID(),
            sourceEntityId = UUID.randomUUID(),
            targetEntityId = UUID.randomUUID(),
            definitionId = UUID.randomUUID(),
            definitionName = "CONNECTED_ENTITIES",
            semanticContext = null,
            linkSource = riven.core.enums.integration.SourceType.IDENTITY_MATCH,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
        )

    private fun buildNotification(): Notification =
        Notification(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            userId = null,
            type = NotificationType.REVIEW_REQUEST,
            content = riven.core.models.notification.NotificationContent.ReviewRequest(
                title = "Identity match confirmed",
                message = "Two entities have been linked.",
            ),
            referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
            referenceId = UUID.randomUUID(),
            resolved = false,
            resolvedAt = null,
            expiresAt = null,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
            createdBy = null,
            updatedBy = null,
        )

    private fun buildActivityLog(): riven.core.models.activity.ActivityLog =
        riven.core.models.activity.ActivityLog(
            id = UUID.randomUUID(),
            userId = userId,
            workspaceId = workspaceId,
            activity = Activity.MATCH_SUGGESTION,
            operation = OperationType.UPDATE,
            entityType = ApplicationEntityType.MATCH_SUGGESTION,
            entityId = UUID.randomUUID(),
            timestamp = ZonedDateTime.now(),
            details = emptyMap(),
        )

    /**
     * Stubs the common happy-path confirm flow where neither entity is clustered (Case 1).
     */
    private fun stubBasicConfirmFlow(entityWithId: MatchSuggestionEntity) {
        whenever(authTokenService.getUserId()).thenReturn(userId)
        whenever(matchSuggestionRepository.findById(entityWithId.id!!)).thenReturn(Optional.of(entityWithId))
        whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(buildRelationshipResponse())
        whenever(memberRepository.findByEntityId(any())).thenReturn(null)
        whenever(clusterRepository.save(any())).thenAnswer { invocation ->
            buildSavedCluster(invocation.getArgument(0))
        }
        whenever(memberRepository.save(any<IdentityClusterMemberEntity>())).thenAnswer { invocation ->
            invocation.getArgument<IdentityClusterMemberEntity>(0)
        }
        whenever(matchSuggestionRepository.save(any())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())
        whenever(notificationService.createInternalNotification(any())).thenReturn(buildNotification())
    }

    private fun setAuditField(entity: Any, fieldName: String, value: Any?) {
        var clazz: Class<*>? = entity.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(entity, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
    }
}
