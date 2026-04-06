package riven.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
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
import riven.core.entity.identity.MatchSuggestionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.identity.MatchSuggestionStatus
import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.enums.util.OperationType
import riven.core.exceptions.ConflictException
import riven.core.models.common.json.JsonObject
import riven.core.models.notification.Notification
import riven.core.models.request.notification.CreateNotificationRequest
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
 * - CONF-02: Cluster resolution is delegated to [IdentityClusterService.resolveClusterMembership]
 * - CONF-04: Rejecting transitions PENDING -> REJECTED with resolvedBy/At, rejectionSignals snapshot, soft-delete
 * - CONF-05: Invalid state transitions throw ConflictException (double-confirm, double-reject, cross-transitions)
 *
 * Cluster resolution logic (5-case) is tested in [IdentityClusterServiceTest]. This test verifies
 * delegation only — that resolveClusterMembership is called with correct arguments.
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
    private lateinit var identityClusterService: IdentityClusterService

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
         * CONF-02: Confirming delegates cluster resolution to IdentityClusterService.resolveClusterMembership
         * with the correct workspaceId, sourceEntityId, targetEntityId, clusterName, and userId.
         */
        @Test
        fun `confirmSuggestion delegates cluster resolution to IdentityClusterService`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            stubBasicConfirmFlow(entityWithId)

            service.confirmSuggestion(workspaceId, suggestionId)

            verify(identityClusterService).resolveClusterMembership(
                workspaceId = eq(workspaceId),
                sourceEntityId = eq(suggestion.sourceEntityId),
                targetEntityId = eq(suggestion.targetEntityId),
                clusterName = isNull(),
                userId = eq(userId),
            )
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

    // ------ ConfirmClusterDelegation — CONF-02 ------

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
    inner class ConfirmClusterDelegation {

        /**
         * CONF-02: Cluster resolution is delegated to IdentityClusterService with correct args.
         * The 5-case cluster logic is tested in IdentityClusterServiceTest — here we verify delegation.
         */
        @Test
        fun `confirmSuggestion delegates to resolveClusterMembership with correct arguments`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            stubBasicConfirmFlow(entityWithId)

            service.confirmSuggestion(workspaceId, suggestionId)

            verify(identityClusterService).resolveClusterMembership(
                workspaceId = eq(workspaceId),
                sourceEntityId = eq(suggestion.sourceEntityId),
                targetEntityId = eq(suggestion.targetEntityId),
                clusterName = isNull(),
                userId = eq(userId),
            )
        }

        /**
         * CONF-02: When the suggestion has a NAME signal, clusterName is resolved from sourceValue.
         */
        @Test
        fun `confirmSuggestion passes NAME signal sourceValue as clusterName`() {
            val nameSignal = mapOf<String, Any?>(
                "type" to "NAME",
                "sourceValue" to "Acme Corp",
                "targetValue" to "ACME Corporation",
            )
            val suggestion = IdentityFactory.createMatchSuggestionEntity(
                workspaceId = workspaceId,
                status = MatchSuggestionStatus.PENDING,
                confidenceScore = BigDecimal("0.8500"),
                signals = listOf(nameSignal),
            )
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)

            stubBasicConfirmFlow(entityWithId)

            service.confirmSuggestion(workspaceId, suggestionId)

            verify(identityClusterService).resolveClusterMembership(
                workspaceId = eq(workspaceId),
                sourceEntityId = eq(suggestion.sourceEntityId),
                targetEntityId = eq(suggestion.targetEntityId),
                clusterName = eq("Acme Corp"),
                userId = eq(userId),
            )
        }

        /**
         * The returned cluster entity is used in activity logging and notification.
         * Verifies that the cluster returned by resolveClusterMembership flows through to activity details.
         */
        @Test
        fun `confirmSuggestion uses returned cluster for activity and notification`() {
            val suggestion = buildPendingSuggestion()
            val suggestionId = UUID.randomUUID()
            val entityWithId = buildEntityWithId(suggestion, suggestionId)
            val returnedClusterId = UUID.randomUUID()
            val returnedCluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 5),
                returnedClusterId,
            )

            stubBasicConfirmFlow(entityWithId)
            whenever(identityClusterService.resolveClusterMembership(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(returnedCluster)

            service.confirmSuggestion(workspaceId, suggestionId)

            // Verify activity log references the returned cluster
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
            assertEquals(returnedClusterId.toString(), detailsCaptor.firstValue["clusterId"])
            assertEquals(5, detailsCaptor.firstValue["clusterMemberCount"])
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
     * Stubs the common happy-path confirm flow with cluster resolution delegated to IdentityClusterService.
     */
    private fun stubBasicConfirmFlow(entityWithId: MatchSuggestionEntity) {
        val defaultCluster = buildSavedClusterWithId(
            IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 2),
            UUID.randomUUID(),
        )

        whenever(authTokenService.getUserId()).thenReturn(userId)
        whenever(matchSuggestionRepository.findById(requireNotNull(entityWithId.id) { "Entity ID required for stub" }))
            .thenReturn(Optional.of(entityWithId))
        whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(buildRelationshipResponse())
        whenever(identityClusterService.resolveClusterMembership(any(), any(), any(), anyOrNull(), any()))
            .thenReturn(defaultCluster)
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
