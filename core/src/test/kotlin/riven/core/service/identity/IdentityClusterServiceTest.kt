package cranium.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.identity.IdentityClusterEntity
import cranium.core.entity.identity.IdentityClusterMemberEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.exceptions.ConflictException
import cranium.core.exceptions.NotFoundException
import cranium.core.models.common.json.JsonObject
import cranium.core.models.identity.IdentityCluster
import cranium.core.models.request.identity.AddClusterMemberRequest
import cranium.core.models.request.identity.RenameClusterRequest
import cranium.core.models.response.identity.ClusterDetailResponse
import cranium.core.models.response.identity.ClusterMemberContext
import cranium.core.repository.identity.IdentityClusterMemberRepository
import cranium.core.repository.identity.IdentityClusterRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.entity.EntityRelationshipService
import cranium.core.service.entity.EntityService
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.SecurityTestConfig
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import cranium.core.service.util.factory.entity.EntityFactory
import cranium.core.service.util.factory.identity.IdentityFactory
import cranium.core.enums.workspace.WorkspaceRoles
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [IdentityClusterService].
 *
 * Covers cluster mutation operations:
 * - Manual add: adds entity to existing cluster with relationship creation, conflict check, and activity logging
 * - Rename: updates cluster name with activity logging
 *
 * @WithUserPersona is required at class level because this service uses @PreAuthorize and authTokenService.getUserId().
 * JUnit 5 @Nested inner classes do not inherit this annotation — it is re-applied on each Nested class.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        IdentityClusterService::class,
        IdentityClusterServiceTest.TestConfig::class,
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
class IdentityClusterServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var clusterRepository: IdentityClusterRepository

    @MockitoBean
    private lateinit var memberRepository: IdentityClusterMemberRepository

    @MockitoBean
    private lateinit var entityRelationshipService: EntityRelationshipService

    @MockitoBean
    private lateinit var entityService: EntityService

    @MockitoBean
    private lateinit var identityReadService: IdentityReadService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var authTokenService: AuthTokenService

    @Autowired
    private lateinit var service: IdentityClusterService

    // ------ AddEntityToCluster ------

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
    inner class AddEntityToCluster {

        /**
         * Happy path: entity added to cluster, relationship created, memberCount incremented,
         * member saved, activity logged, and updated cluster detail returned.
         */
        @Test
        fun `addEntityToCluster adds entity, creates relationship, increments memberCount, logs activity`() {
            val clusterId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val targetMemberId = UUID.randomUUID()
            val request = AddClusterMemberRequest(entityId = entityId, targetMemberId = targetMemberId)

            val cluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, memberCount = 2),
                clusterId,
            )
            val targetMemberEntity = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = clusterId,
                entityId = targetMemberId,
            )
            val entityEntity = buildEntityEntity(entityId, workspaceId)
            val expectedDetail = buildClusterDetailResponse(clusterId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId)).thenReturn(Optional.of(cluster))
            whenever(entityService.getEntitiesByIds(setOf(entityId))).thenReturn(listOf(entityEntity))
            whenever(memberRepository.findByEntityId(entityId)).thenReturn(null)
            whenever(memberRepository.findByClusterIdAndEntityId(clusterId, targetMemberId)).thenReturn(targetMemberEntity)
            whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(buildRelationshipResponse())
            whenever(memberRepository.save(any<IdentityClusterMemberEntity>())).thenAnswer { it.getArgument(0) }
            whenever(clusterRepository.save(any())).thenAnswer { buildSavedCluster(it.getArgument(0)) }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(buildActivityLog())
            whenever(identityReadService.getClusterDetail(workspaceId, clusterId)).thenReturn(expectedDetail)

            val result = service.addEntityToCluster(workspaceId, clusterId, request)

            assertEquals(expectedDetail, result)

            // Verify member saved
            val memberCaptor = argumentCaptor<IdentityClusterMemberEntity>()
            verify(memberRepository).save(memberCaptor.capture())
            assertEquals(clusterId, memberCaptor.firstValue.clusterId)
            assertEquals(entityId, memberCaptor.firstValue.entityId)

            // Verify memberCount incremented
            val clusterCaptor = argumentCaptor<IdentityClusterEntity>()
            verify(clusterRepository).save(clusterCaptor.capture())
            assertEquals(3, clusterCaptor.firstValue.memberCount)

            // Verify relationship created
            verify(entityRelationshipService).addRelationship(eq(workspaceId), eq(entityId), any())

            // Verify activity logged
            verify(activityService).logActivity(
                eq(Activity.IDENTITY_CLUSTER),
                eq(OperationType.CREATE),
                eq(userId),
                eq(workspaceId),
                eq(ApplicationEntityType.IDENTITY_CLUSTER),
                eq(clusterId),
                any(),
                any(),
            )
        }

        /**
         * Throws ConflictException if entity is already in ANY cluster (not just this one).
         */
        @Test
        fun `addEntityToCluster throws ConflictException when entity is already in a cluster`() {
            val clusterId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val targetMemberId = UUID.randomUUID()
            val request = AddClusterMemberRequest(entityId = entityId, targetMemberId = targetMemberId)

            val cluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId),
                clusterId,
            )
            val entityEntity = buildEntityEntity(entityId, workspaceId)
            val existingMembership = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = UUID.randomUUID(), // entity is already in a different cluster
                entityId = entityId,
            )

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId)).thenReturn(Optional.of(cluster))
            whenever(entityService.getEntitiesByIds(setOf(entityId))).thenReturn(listOf(entityEntity))
            whenever(memberRepository.findByEntityId(entityId)).thenReturn(existingMembership)

            assertThrows<ConflictException> {
                service.addEntityToCluster(workspaceId, clusterId, request)
            }
        }

        /**
         * Throws NotFoundException if the cluster is not found in the given workspace.
         */
        @Test
        fun `addEntityToCluster throws NotFoundException when cluster not found`() {
            val clusterId = UUID.randomUUID()
            val request = AddClusterMemberRequest(entityId = UUID.randomUUID(), targetMemberId = UUID.randomUUID())

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId)).thenReturn(Optional.empty())

            assertThrows<NotFoundException> {
                service.addEntityToCluster(workspaceId, clusterId, request)
            }
        }

        /**
         * Throws NotFoundException if the targetMemberId is not a member of the cluster.
         */
        @Test
        fun `addEntityToCluster throws NotFoundException when targetMember is not in cluster`() {
            val clusterId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val targetMemberId = UUID.randomUUID()
            val request = AddClusterMemberRequest(entityId = entityId, targetMemberId = targetMemberId)

            val cluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId),
                clusterId,
            )
            val entityEntity = buildEntityEntity(entityId, workspaceId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId)).thenReturn(Optional.of(cluster))
            whenever(entityService.getEntitiesByIds(setOf(entityId))).thenReturn(listOf(entityEntity))
            whenever(memberRepository.findByEntityId(entityId)).thenReturn(null)
            whenever(memberRepository.findByClusterIdAndEntityId(clusterId, targetMemberId)).thenReturn(null)

            assertThrows<NotFoundException> {
                service.addEntityToCluster(workspaceId, clusterId, request)
            }
        }

        /**
         * Throws NotFoundException if the entity does not belong to the given workspace.
         * Returns 404 for both missing and wrong-workspace entities to prevent information leakage.
         */
        @Test
        fun `addEntityToCluster throws NotFoundException when entity is not in workspace`() {
            val clusterId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val request = AddClusterMemberRequest(entityId = entityId, targetMemberId = UUID.randomUUID())

            val cluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId),
                clusterId,
            )

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId)).thenReturn(Optional.of(cluster))
            // Entity belongs to a different workspace
            val entityEntity = buildEntityEntity(entityId, UUID.randomUUID())
            whenever(entityService.getEntitiesByIds(setOf(entityId))).thenReturn(listOf(entityEntity))

            assertThrows<NotFoundException> {
                service.addEntityToCluster(workspaceId, clusterId, request)
            }
        }
    }

    // ------ RenameCluster ------

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
    inner class RenameCluster {

        /**
         * Happy path: cluster name is updated, saved, activity is logged, and the updated model is returned.
         */
        @Test
        fun `renameCluster updates name, logs activity, and returns updated model`() {
            val clusterId = UUID.randomUUID()
            val oldName = "Old Name"
            val newName = "New Name"
            val request = RenameClusterRequest(name = newName)

            val cluster = buildSavedClusterWithId(
                IdentityFactory.createIdentityClusterEntity(workspaceId = workspaceId, name = oldName),
                clusterId,
            )

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId)).thenReturn(Optional.of(cluster))
            whenever(clusterRepository.save(any())).thenAnswer { buildSavedCluster(it.getArgument(0)) }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(buildActivityLog())

            val result = service.renameCluster(workspaceId, clusterId, request)

            assertEquals(newName, result.name)

            // Verify saved with new name
            val clusterCaptor = argumentCaptor<IdentityClusterEntity>()
            verify(clusterRepository).save(clusterCaptor.capture())
            assertEquals(newName, clusterCaptor.firstValue.name)

            // Verify activity logged
            val detailsCaptor = argumentCaptor<JsonObject>()
            verify(activityService).logActivity(
                eq(Activity.IDENTITY_CLUSTER),
                eq(OperationType.UPDATE),
                eq(userId),
                eq(workspaceId),
                eq(ApplicationEntityType.IDENTITY_CLUSTER),
                eq(clusterId),
                any(),
                detailsCaptor.capture(),
            )
            assertEquals(oldName, detailsCaptor.firstValue["oldName"])
            assertEquals(newName, detailsCaptor.firstValue["newName"])
        }

        /**
         * Throws NotFoundException if the cluster is not found in the given workspace.
         */
        @Test
        fun `renameCluster throws NotFoundException when cluster not found`() {
            val clusterId = UUID.randomUUID()
            val request = RenameClusterRequest(name = "New Name")

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId)).thenReturn(Optional.empty())

            assertThrows<NotFoundException> {
                service.renameCluster(workspaceId, clusterId, request)
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
         * Verifies that addEntityToCluster rejects requests when the authenticated user
         * does not have access to the target workspace. The @PreAuthorize annotation
         * on the service method should trigger an AccessDeniedException before any
         * business logic executes.
         */
        @Test
        fun `addEntityToCluster throws AccessDeniedException for unauthorized workspace`() {
            val request = AddClusterMemberRequest(entityId = UUID.randomUUID(), targetMemberId = UUID.randomUUID())

            assertThrows<AccessDeniedException> {
                service.addEntityToCluster(workspaceId, UUID.randomUUID(), request)
            }
        }

        /**
         * Verifies that renameCluster rejects requests when the authenticated user
         * does not have access to the target workspace. The @PreAuthorize annotation
         * on the service method should trigger an AccessDeniedException before any
         * business logic executes.
         */
        @Test
        fun `renameCluster throws AccessDeniedException for unauthorized workspace`() {
            val request = RenameClusterRequest(name = "New Name")

            assertThrows<AccessDeniedException> {
                service.renameCluster(workspaceId, UUID.randomUUID(), request)
            }
        }
    }

    // ------ Test helpers ------

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

    private fun buildEntityEntity(entityId: UUID, entityWorkspaceId: UUID): cranium.core.entity.entity.EntityEntity =
        EntityFactory.createEntityEntity(id = entityId, workspaceId = entityWorkspaceId)

    private fun buildClusterDetailResponse(clusterId: UUID): ClusterDetailResponse =
        ClusterDetailResponse(
            id = clusterId,
            workspaceId = workspaceId,
            name = "Test Cluster",
            memberCount = 3,
            members = emptyList<ClusterMemberContext>(),
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
        )

    private fun buildRelationshipResponse(): cranium.core.models.response.entity.RelationshipResponse =
        cranium.core.models.response.entity.RelationshipResponse(
            id = UUID.randomUUID(),
            sourceEntityId = UUID.randomUUID(),
            targetEntityId = UUID.randomUUID(),
            definitionId = UUID.randomUUID(),
            definitionName = "SYSTEM_CONNECTION",
            semanticContext = null,
            linkSource = cranium.core.enums.integration.SourceType.IDENTITY_MATCH,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
        )

    private fun buildActivityLog(): cranium.core.models.activity.ActivityLog =
        cranium.core.models.activity.ActivityLog(
            id = UUID.randomUUID(),
            userId = userId,
            workspaceId = workspaceId,
            activity = Activity.IDENTITY_CLUSTER,
            operation = OperationType.CREATE,
            entityType = ApplicationEntityType.IDENTITY_CLUSTER,
            entityId = UUID.randomUUID(),
            timestamp = ZonedDateTime.now(),
            details = emptyMap(),
        )

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
