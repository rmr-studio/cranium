package riven.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.identity.IdentityClusterEntity
import riven.core.entity.identity.IdentityClusterMemberEntity
import riven.core.entity.identity.MatchSuggestionEntity
import riven.core.enums.identity.MatchSuggestionStatus
import riven.core.enums.integration.SourceType
import riven.core.exceptions.NotFoundException
import riven.core.models.response.identity.ClusterDetailResponse
import riven.core.models.response.identity.ClusterSummaryResponse
import riven.core.models.response.identity.PendingMatchCountResponse
import riven.core.models.response.identity.SuggestionResponse
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.identity.IdentityClusterRepository
import riven.core.repository.identity.MatchSuggestionRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.identity.IdentityFactory
import riven.core.enums.workspace.WorkspaceRoles
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [IdentityReadService].
 *
 * Covers all 5 read operations:
 * - listSuggestions: returns mapped suggestions for workspace
 * - getSuggestion: returns suggestion by id, throws NotFoundException on wrong workspace or missing id
 * - listClusters: returns mapped cluster summaries for workspace
 * - getClusterDetail: returns cluster with enriched members, handles missing entities gracefully
 * - getPendingMatchCount: returns count from native query
 *
 * @WithUserPersona is required at class level because this service uses @PreAuthorize
 * and authTokenService.getUserId(). JUnit 5 @Nested inner classes do not inherit this
 * annotation — it is re-applied on each Nested class.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        IdentityReadService::class,
        IdentityReadServiceTest.TestConfig::class,
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
class IdentityReadServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var matchSuggestionRepository: MatchSuggestionRepository

    @MockitoBean
    private lateinit var clusterRepository: IdentityClusterRepository

    @MockitoBean
    private lateinit var memberRepository: IdentityClusterMemberRepository

    @MockitoBean
    private lateinit var entityService: EntityService

    @MockitoBean
    private lateinit var authTokenService: AuthTokenService

    @Autowired
    private lateinit var service: IdentityReadService

    // ------ ListSuggestions ------

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
    inner class ListSuggestions {

        /**
         * listSuggestions delegates to findByWorkspaceId and maps each entity to SuggestionResponse.
         * Status and IDs are preserved in the mapped response.
         */
        @Test
        fun `listSuggestions returns mapped suggestions for workspace`() {
            val entity = buildSuggestionEntityWithId(UUID.randomUUID())
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findByWorkspaceId(workspaceId)).thenReturn(listOf(entity))

            val result = service.listSuggestions(workspaceId)

            assertEquals(1, result.size)
            val response = result.first()
            assertEquals(entity.id, response.id)
            assertEquals(entity.workspaceId, response.workspaceId)
            assertEquals(entity.status, response.status)
        }

        /**
         * listSuggestions returns an empty list when there are no suggestions for the workspace.
         */
        @Test
        fun `listSuggestions returns empty list when no suggestions exist`() {
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findByWorkspaceId(workspaceId)).thenReturn(emptyList())

            val result = service.listSuggestions(workspaceId)

            assertEquals(0, result.size)
        }
    }

    // ------ GetSuggestion ------

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
    inner class GetSuggestion {

        /**
         * getSuggestion returns the suggestion when it exists and belongs to the workspace.
         */
        @Test
        fun `getSuggestion returns suggestion by id for correct workspace`() {
            val suggestionId = UUID.randomUUID()
            val entity = buildSuggestionEntityWithId(suggestionId)
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entity))

            val result = service.getSuggestion(workspaceId, suggestionId)

            assertEquals(suggestionId, result.id)
            assertEquals(workspaceId, result.workspaceId)
        }

        /**
         * getSuggestion throws NotFoundException when the suggestion belongs to a different workspace.
         */
        @Test
        fun `getSuggestion throws NotFoundException for suggestion in wrong workspace`() {
            val suggestionId = UUID.randomUUID()
            val otherWorkspace = UUID.randomUUID()
            val entity = buildSuggestionEntityWithId(suggestionId, workspaceId = otherWorkspace)
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.of(entity))

            assertThrows<NotFoundException> {
                service.getSuggestion(workspaceId, suggestionId)
            }
        }

        /**
         * getSuggestion throws NotFoundException when the suggestion ID does not exist.
         */
        @Test
        fun `getSuggestion throws NotFoundException when suggestion not found`() {
            val suggestionId = UUID.randomUUID()
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.findById(suggestionId)).thenReturn(Optional.empty())

            assertThrows<NotFoundException> {
                service.getSuggestion(workspaceId, suggestionId)
            }
        }
    }

    // ------ ListClusters ------

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
    inner class ListClusters {

        /**
         * listClusters delegates to findByWorkspaceId and maps each cluster entity to ClusterSummaryResponse.
         */
        @Test
        fun `listClusters returns mapped cluster summaries for workspace`() {
            val clusterId = UUID.randomUUID()
            val entity = buildClusterEntityWithId(clusterId)
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByWorkspaceId(workspaceId)).thenReturn(listOf(entity))

            val result = service.listClusters(workspaceId)

            assertEquals(1, result.size)
            val response = result.first()
            assertEquals(clusterId, response.id)
            assertEquals(workspaceId, response.workspaceId)
            assertEquals(entity.name, response.name)
            assertEquals(entity.memberCount, response.memberCount)
        }

        /**
         * listClusters returns empty list when no clusters exist.
         */
        @Test
        fun `listClusters returns empty list when no clusters exist`() {
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByWorkspaceId(workspaceId)).thenReturn(emptyList())

            val result = service.listClusters(workspaceId)

            assertEquals(0, result.size)
        }
    }

    // ------ GetClusterDetail ------

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
    inner class GetClusterDetail {

        /**
         * getClusterDetail returns cluster metadata with enriched member list.
         * Each member has entityId, typeKey, sourceType, and identifierKey populated from EntityService.
         */
        @Test
        fun `getClusterDetail returns cluster with enriched members`() {
            val clusterId = UUID.randomUUID()
            val clusterEntity = buildClusterEntityWithId(clusterId)
            val memberEntityId = UUID.randomUUID()
            val member = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = clusterId,
                entityId = memberEntityId,
            )
            val entityEntity = buildEntityEntity(memberEntityId)

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId))
                .thenReturn(Optional.of(clusterEntity))
            whenever(memberRepository.findByClusterId(clusterId)).thenReturn(listOf(member))
            whenever(entityService.getEntitiesByIds(setOf(memberEntityId))).thenReturn(listOf(entityEntity))

            val result = service.getClusterDetail(workspaceId, clusterId)

            assertEquals(clusterId, result.id)
            assertEquals(1, result.members.size)
            val memberContext = result.members.first()
            assertEquals(memberEntityId, memberContext.entityId)
            assertEquals("person", memberContext.typeKey)
            assertEquals(SourceType.INTEGRATION, memberContext.sourceType)
        }

        /**
         * getClusterDetail handles missing entities gracefully — typeKey, sourceType, identifierKey
         * are null when the entity has been soft-deleted after joining the cluster.
         */
        @Test
        fun `getClusterDetail sets null fields for missing entity`() {
            val clusterId = UUID.randomUUID()
            val clusterEntity = buildClusterEntityWithId(clusterId)
            val memberEntityId = UUID.randomUUID()
            val member = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = clusterId,
                entityId = memberEntityId,
            )

            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId))
                .thenReturn(Optional.of(clusterEntity))
            whenever(memberRepository.findByClusterId(clusterId)).thenReturn(listOf(member))
            // EntityService returns empty list — entity was soft-deleted
            whenever(entityService.getEntitiesByIds(setOf(memberEntityId))).thenReturn(emptyList())

            val result = service.getClusterDetail(workspaceId, clusterId)

            assertEquals(1, result.members.size)
            val memberContext = result.members.first()
            assertEquals(memberEntityId, memberContext.entityId)
            assertNull(memberContext.typeKey)
            assertNull(memberContext.sourceType)
            assertNull(memberContext.identifierKey)
        }

        /**
         * getClusterDetail throws NotFoundException when cluster not found in workspace.
         */
        @Test
        fun `getClusterDetail throws NotFoundException when cluster not found`() {
            val clusterId = UUID.randomUUID()
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(clusterRepository.findByIdAndWorkspaceId(clusterId, workspaceId))
                .thenReturn(Optional.empty())

            assertThrows<NotFoundException> {
                service.getClusterDetail(workspaceId, clusterId)
            }
        }
    }

    // ------ GetPendingMatchCount ------

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
    inner class GetPendingMatchCount {

        /**
         * getPendingMatchCount delegates to countPendingForEntity and wraps in PendingMatchCountResponse.
         */
        @Test
        fun `getPendingMatchCount returns count from repository`() {
            val entityId = UUID.randomUUID()
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.countPendingForEntity(workspaceId, entityId)).thenReturn(3L)

            val result = service.getPendingMatchCount(workspaceId, entityId)

            assertEquals(entityId, result.entityId)
            assertEquals(3L, result.pendingCount)
        }

        /**
         * getPendingMatchCount returns 0 when there are no pending suggestions for the entity.
         */
        @Test
        fun `getPendingMatchCount returns 0 when no pending suggestions`() {
            val entityId = UUID.randomUUID()
            whenever(authTokenService.getUserId()).thenReturn(userId)
            whenever(matchSuggestionRepository.countPendingForEntity(workspaceId, entityId)).thenReturn(0L)

            val result = service.getPendingMatchCount(workspaceId, entityId)

            assertEquals(0L, result.pendingCount)
        }
    }

    // ------ UnauthorizedAccessTests ------

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
         * Verifies that @PreAuthorize on listSuggestions rejects requests when the
         * authenticated user does not have access to the target workspace.
         */
        @Test
        fun `listSuggestions throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.listSuggestions(workspaceId)
            }
        }

        /**
         * Verifies that @PreAuthorize on getSuggestion rejects unauthorized workspace access.
         */
        @Test
        fun `getSuggestion throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.getSuggestion(workspaceId, UUID.randomUUID())
            }
        }

        /**
         * Verifies that @PreAuthorize on listClusters rejects unauthorized workspace access.
         */
        @Test
        fun `listClusters throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.listClusters(workspaceId)
            }
        }

        /**
         * Verifies that @PreAuthorize on getClusterDetail rejects unauthorized workspace access.
         */
        @Test
        fun `getClusterDetail throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.getClusterDetail(workspaceId, UUID.randomUUID())
            }
        }

        /**
         * Verifies that @PreAuthorize on getPendingMatchCount rejects unauthorized workspace access.
         */
        @Test
        fun `getPendingMatchCount throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.getPendingMatchCount(workspaceId, UUID.randomUUID())
            }
        }
    }

    // ------ Test helpers ------

    private fun buildSuggestionEntityWithId(
        id: UUID,
        workspaceId: UUID = this.workspaceId,
    ): MatchSuggestionEntity {
        val entity = IdentityFactory.createMatchSuggestionEntity(
            workspaceId = workspaceId,
            status = MatchSuggestionStatus.PENDING,
            confidenceScore = BigDecimal("0.8500"),
        ).copy(id = id)
        val now = ZonedDateTime.now()
        setAuditField(entity, "createdAt", now)
        setAuditField(entity, "updatedAt", now)
        return entity
    }

    private fun buildClusterEntityWithId(id: UUID): IdentityClusterEntity {
        val entity = IdentityFactory.createIdentityClusterEntity(
            workspaceId = workspaceId,
            name = "Test Cluster",
            memberCount = 2,
        ).copy(id = id)
        val now = ZonedDateTime.now()
        setAuditField(entity, "createdAt", now)
        setAuditField(entity, "updatedAt", now)
        return entity
    }

    private fun buildEntityEntity(id: UUID): EntityEntity =
        EntityFactory.createEntityEntity(
            id = id,
            workspaceId = workspaceId,
            typeKey = "person",
            sourceType = SourceType.INTEGRATION,
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
