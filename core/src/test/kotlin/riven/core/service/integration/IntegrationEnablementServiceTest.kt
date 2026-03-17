package riven.core.service.integration

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.integration.IntegrationConnectionEntity
import riven.core.entity.integration.IntegrationDefinitionEntity
import riven.core.entity.integration.WorkspaceIntegrationInstallationEntity
import riven.core.enums.integration.ConnectionStatus
import riven.core.enums.integration.IntegrationCategory
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.NotFoundException
import riven.core.models.request.integration.DisableIntegrationRequest
import riven.core.repository.integration.IntegrationDefinitionRepository
import riven.core.repository.integration.WorkspaceIntegrationInstallationRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.type.EntityTypeService
import riven.core.models.integration.IntegrationSoftDeleteResult
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import org.junit.jupiter.api.assertThrows
import org.springframework.security.access.AccessDeniedException
import java.util.*

/**
 * Unit tests for IntegrationEnablementService.
 *
 * Tests the disable lifecycle orchestration including:
 * - Disabling an integration (soft-delete entity types + disconnect + soft-delete installation)
 * - Handling missing connections gracefully during disable
 * - Security access control
 *
 * Note: enableIntegration() was removed in Phase 2 — integration enablement is now
 * webhook-driven. Connection creation happens via the Nango auth webhook handler.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        IntegrationEnablementServiceTest.TestConfig::class,
        IntegrationEnablementService::class
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.ADMIN
        )
    ]
)
class IntegrationEnablementServiceTest {

    @Configuration
    class TestConfig

    private val userId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")

    @MockitoBean
    private lateinit var installationRepository: WorkspaceIntegrationInstallationRepository

    @MockitoBean
    private lateinit var definitionRepository: IntegrationDefinitionRepository

    @MockitoBean
    private lateinit var connectionService: IntegrationConnectionService

    @MockitoBean
    private lateinit var entityTypeService: EntityTypeService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var authTokenService: AuthTokenService

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var enablementService: IntegrationEnablementService

    private lateinit var testIntegrationId: UUID
    private lateinit var testInstallationId: UUID
    private lateinit var testDefinition: IntegrationDefinitionEntity

    @BeforeEach
    fun setup() {
        reset(
            installationRepository, definitionRepository, connectionService,
            entityTypeService, activityService
        )

        testIntegrationId = UUID.randomUUID()
        testInstallationId = UUID.randomUUID()

        testDefinition = IntegrationDefinitionEntity(
            id = testIntegrationId,
            slug = "hubspot",
            name = "HubSpot",
            category = IntegrationCategory.CRM,
            nangoProviderKey = "hubspot",
            capabilities = emptyMap(),
            syncConfig = emptyMap(),
            authConfig = emptyMap()
        )

        whenever(authTokenService.getUserId()).thenReturn(userId)
    }

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class Disable {

        @Test
        fun `disableIntegration - soft-deletes entity types, disconnects, and marks installation deleted`() {
            val installation = WorkspaceIntegrationInstallationEntity(
                id = testInstallationId,
                workspaceId = workspaceId,
                integrationDefinitionId = testIntegrationId,
                manifestKey = "hubspot",
                installedBy = userId
            )
            val request = DisableIntegrationRequest(integrationDefinitionId = testIntegrationId)
            val connection = IntegrationConnectionEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                integrationId = testIntegrationId,
                nangoConnectionId = "nango-conn-123",
                status = ConnectionStatus.CONNECTED
            )

            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(installation)
            whenever(entityTypeService.softDeleteByIntegration(workspaceId, testIntegrationId))
                .thenReturn(IntegrationSoftDeleteResult(entityTypesSoftDeleted = 3, relationshipsSoftDeleted = 2))
            whenever(connectionService.getConnection(workspaceId, testIntegrationId))
                .thenReturn(connection)
            whenever(connectionService.disconnectConnection(workspaceId, connection.id!!))
                .thenReturn(connection.copy(status = ConnectionStatus.DISCONNECTED))
            whenever(installationRepository.save(any<WorkspaceIntegrationInstallationEntity>()))
                .thenAnswer { invocation -> invocation.getArgument(0) as WorkspaceIntegrationInstallationEntity }

            val result = enablementService.disableIntegration(workspaceId, request)

            assertEquals(testIntegrationId, result.integrationDefinitionId)
            assertEquals("HubSpot", result.integrationName)
            assertEquals(3, result.entityTypesSoftDeleted)
            assertEquals(2, result.relationshipsSoftDeleted)

            verify(entityTypeService).softDeleteByIntegration(workspaceId, testIntegrationId)
            verify(connectionService).disconnectConnection(workspaceId, connection.id!!)
            verify(installationRepository).save(argThat { inst ->
                inst.deleted && inst.deletedAt != null
            })
            verify(activityService).logActivity(
                activity = any(),
                operation = any(),
                userId = any(),
                workspaceId = any(),
                entityType = any(),
                entityId = anyOrNull(),
                timestamp = any(),
                details = any()
            )
        }

        @Test
        fun `disableIntegration - snapshots lastSyncedAt before soft-deleting`() {
            val installation = WorkspaceIntegrationInstallationEntity(
                id = testInstallationId,
                workspaceId = workspaceId,
                integrationDefinitionId = testIntegrationId,
                manifestKey = "hubspot",
                installedBy = userId,
                lastSyncedAt = null
            )
            val request = DisableIntegrationRequest(integrationDefinitionId = testIntegrationId)

            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(installation)
            whenever(entityTypeService.softDeleteByIntegration(workspaceId, testIntegrationId))
                .thenReturn(IntegrationSoftDeleteResult(0, 0))
            whenever(connectionService.getConnection(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(installationRepository.save(any<WorkspaceIntegrationInstallationEntity>()))
                .thenAnswer { invocation -> invocation.getArgument(0) as WorkspaceIntegrationInstallationEntity }

            enablementService.disableIntegration(workspaceId, request)

            verify(installationRepository).save(argThat { inst ->
                inst.lastSyncedAt != null && inst.deleted
            })
        }

        @Test
        fun `disableIntegration - throws NotFoundException when integration not enabled`() {
            val request = DisableIntegrationRequest(integrationDefinitionId = testIntegrationId)

            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(null)

            assertThrows<NotFoundException> {
                enablementService.disableIntegration(workspaceId, request)
            }

            verify(entityTypeService, never()).softDeleteByIntegration(any(), any())
        }

        @Test
        fun `disableIntegration - handles missing connection gracefully`() {
            val installation = WorkspaceIntegrationInstallationEntity(
                id = testInstallationId,
                workspaceId = workspaceId,
                integrationDefinitionId = testIntegrationId,
                manifestKey = "hubspot",
                installedBy = userId
            )
            val request = DisableIntegrationRequest(integrationDefinitionId = testIntegrationId)

            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(installation)
            whenever(entityTypeService.softDeleteByIntegration(workspaceId, testIntegrationId))
                .thenReturn(IntegrationSoftDeleteResult(1, 0))
            whenever(connectionService.getConnection(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(installationRepository.save(any<WorkspaceIntegrationInstallationEntity>()))
                .thenAnswer { invocation -> invocation.getArgument(0) as WorkspaceIntegrationInstallationEntity }

            val result = enablementService.disableIntegration(workspaceId, request)

            assertEquals(1, result.entityTypesSoftDeleted)
            verify(connectionService, never()).disconnectConnection(any(), any())
        }
    }

    // ========== Security Tests ==========

    @Nested
    inner class Security {

        @Test
        @WithUserPersona(
            userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
            email = "test@test.com",
            displayName = "Test User",
            roles = [
                WorkspaceRole(
                    workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                    role = WorkspaceRoles.MEMBER
                )
            ]
        )
        fun `disableIntegration - rejects non-admin user`() {
            val request = DisableIntegrationRequest(integrationDefinitionId = UUID.randomUUID())

            assertThrows<AccessDeniedException> {
                enablementService.disableIntegration(workspaceId, request)
            }
        }
    }
}
