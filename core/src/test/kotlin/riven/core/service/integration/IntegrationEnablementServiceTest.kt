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
import riven.core.models.integration.SyncConfiguration
import riven.core.models.integration.materialization.MaterializationResult
import riven.core.models.request.integration.DisableIntegrationRequest
import riven.core.models.request.integration.EnableIntegrationRequest
import riven.core.repository.integration.IntegrationDefinitionRepository
import riven.core.repository.integration.WorkspaceIntegrationInstallationRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.type.EntityTypeService
import riven.core.models.integration.IntegrationSoftDeleteResult
import riven.core.service.integration.materialization.TemplateMaterializationService
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import org.junit.jupiter.api.assertThrows
import org.springframework.security.access.AccessDeniedException
import java.util.*

/**
 * Unit tests for IntegrationEnablementService.
 *
 * Tests the enable/disable lifecycle orchestration including:
 * - Enabling an integration (connection + materialization + installation tracking)
 * - Idempotent enable when already enabled
 * - Re-enable via soft-deleted installation restore
 * - Disabling an integration (soft-delete entity types + disconnect + soft-delete installation)
 * - Full enable/disable/re-enable lifecycle
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
    private lateinit var materializationService: TemplateMaterializationService

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
            materializationService, entityTypeService, activityService
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
    inner class Enable {

        @Test
        fun `enableIntegration - creates connection, materializes, tracks installation, and logs activity`() {
            val request = EnableIntegrationRequest(
                integrationDefinitionId = testIntegrationId,
                nangoConnectionId = "nango-conn-123"
            )
            val savedInstallation = WorkspaceIntegrationInstallationEntity(
                id = testInstallationId,
                workspaceId = workspaceId,
                integrationDefinitionId = testIntegrationId,
                manifestKey = "hubspot",
                installedBy = userId
            )
            val connectionEntity = IntegrationConnectionEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                integrationId = testIntegrationId,
                nangoConnectionId = "nango-conn-123",
                status = ConnectionStatus.CONNECTED
            )
            val materializationResult = MaterializationResult(
                entityTypesCreated = 3,
                entityTypesRestored = 0,
                relationshipsCreated = 2,
                integrationSlug = "hubspot"
            )

            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(installationRepository.findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(connectionService.enableConnection(workspaceId, testIntegrationId, "nango-conn-123"))
                .thenReturn(connectionEntity)
            whenever(materializationService.materializeIntegrationTemplates(workspaceId, "hubspot"))
                .thenReturn(materializationResult)
            whenever(installationRepository.save(any<WorkspaceIntegrationInstallationEntity>()))
                .thenReturn(savedInstallation)

            val result = enablementService.enableIntegration(workspaceId, request)

            assertEquals(testIntegrationId, result.integrationDefinitionId)
            assertEquals("HubSpot", result.integrationName)
            assertEquals("hubspot", result.integrationSlug)
            assertEquals(3, result.entityTypesCreated)
            assertEquals(0, result.entityTypesRestored)
            assertEquals(2, result.relationshipsCreated)

            verify(connectionService).enableConnection(workspaceId, testIntegrationId, "nango-conn-123")
            verify(materializationService).materializeIntegrationTemplates(workspaceId, "hubspot")
            verify(installationRepository).save(any<WorkspaceIntegrationInstallationEntity>())
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
        fun `enableIntegration - defaults syncConfig to ALL when not provided`() {
            val request = EnableIntegrationRequest(
                integrationDefinitionId = testIntegrationId,
                nangoConnectionId = "nango-conn-123",
                syncConfig = null
            )
            val savedInstallation = WorkspaceIntegrationInstallationEntity(
                id = testInstallationId,
                workspaceId = workspaceId,
                integrationDefinitionId = testIntegrationId,
                manifestKey = "hubspot",
                installedBy = userId
            )
            val connectionEntity = IntegrationConnectionEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                integrationId = testIntegrationId,
                nangoConnectionId = "nango-conn-123",
                status = ConnectionStatus.CONNECTED
            )

            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(installationRepository.findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(connectionService.enableConnection(workspaceId, testIntegrationId, "nango-conn-123"))
                .thenReturn(connectionEntity)
            whenever(materializationService.materializeIntegrationTemplates(workspaceId, "hubspot"))
                .thenReturn(MaterializationResult(0, 0, 0, "hubspot"))
            whenever(installationRepository.save(any<WorkspaceIntegrationInstallationEntity>()))
                .thenReturn(savedInstallation)

            val result = enablementService.enableIntegration(workspaceId, request)

            assertEquals(SyncConfiguration(), result.syncConfig)
        }

        @Test
        fun `enableIntegration - returns existing result when already enabled (idempotent)`() {
            val existingInstallation = WorkspaceIntegrationInstallationEntity(
                id = testInstallationId,
                workspaceId = workspaceId,
                integrationDefinitionId = testIntegrationId,
                manifestKey = "hubspot",
                installedBy = userId
            )
            val request = EnableIntegrationRequest(
                integrationDefinitionId = testIntegrationId,
                nangoConnectionId = "nango-conn-123"
            )

            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(existingInstallation)

            val result = enablementService.enableIntegration(workspaceId, request)

            assertEquals(testIntegrationId, result.integrationDefinitionId)
            assertEquals(0, result.entityTypesCreated)
            assertEquals(0, result.entityTypesRestored)
            assertEquals(0, result.relationshipsCreated)

            verify(connectionService, never()).enableConnection(any(), any(), any())
            verify(materializationService, never()).materializeIntegrationTemplates(any(), any())
        }

        @Test
        fun `enableIntegration - restores soft-deleted installation on re-enable`() {
            val softDeletedInstallation = WorkspaceIntegrationInstallationEntity(
                id = testInstallationId,
                workspaceId = workspaceId,
                integrationDefinitionId = testIntegrationId,
                manifestKey = "hubspot",
                installedBy = userId,
                deleted = true,
                deletedAt = java.time.ZonedDateTime.now()
            )
            val request = EnableIntegrationRequest(
                integrationDefinitionId = testIntegrationId,
                nangoConnectionId = "nango-conn-456"
            )
            val connectionEntity = IntegrationConnectionEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                integrationId = testIntegrationId,
                nangoConnectionId = "nango-conn-456",
                status = ConnectionStatus.CONNECTED
            )

            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(installationRepository.findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(softDeletedInstallation)
            whenever(connectionService.enableConnection(workspaceId, testIntegrationId, "nango-conn-456"))
                .thenReturn(connectionEntity)
            whenever(materializationService.materializeIntegrationTemplates(workspaceId, "hubspot"))
                .thenReturn(MaterializationResult(1, 2, 1, "hubspot"))
            whenever(installationRepository.save(any<WorkspaceIntegrationInstallationEntity>()))
                .thenAnswer { invocation -> invocation.getArgument(0) as WorkspaceIntegrationInstallationEntity }

            val result = enablementService.enableIntegration(workspaceId, request)

            assertEquals(2, result.entityTypesRestored)

            verify(installationRepository).save(argThat { installation ->
                !installation.deleted && installation.deletedAt == null
            })
        }

        @Test
        fun `enableIntegration - throws NotFoundException for unknown integration`() {
            val request = EnableIntegrationRequest(
                integrationDefinitionId = testIntegrationId,
                nangoConnectionId = "nango-conn-123"
            )

            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.empty())

            assertThrows<NotFoundException> {
                enablementService.enableIntegration(workspaceId, request)
            }

            verify(connectionService, never()).enableConnection(any(), any(), any())
            verify(installationRepository, never()).save(any<WorkspaceIntegrationInstallationEntity>())
        }
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
    inner class Lifecycle {

        @Test
        fun `enable then disable then re-enable cycle works`() {
            val connectionEntity = IntegrationConnectionEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                integrationId = testIntegrationId,
                nangoConnectionId = "nango-conn-123",
                status = ConnectionStatus.CONNECTED
            )
            val savedInstallation = WorkspaceIntegrationInstallationEntity(
                id = testInstallationId,
                workspaceId = workspaceId,
                integrationDefinitionId = testIntegrationId,
                manifestKey = "hubspot",
                installedBy = userId
            )

            // --- Enable ---
            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(installationRepository.findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(connectionService.enableConnection(workspaceId, testIntegrationId, "nango-conn-123"))
                .thenReturn(connectionEntity)
            whenever(materializationService.materializeIntegrationTemplates(workspaceId, "hubspot"))
                .thenReturn(MaterializationResult(3, 0, 2, "hubspot"))
            whenever(installationRepository.save(any<WorkspaceIntegrationInstallationEntity>()))
                .thenReturn(savedInstallation)

            val enableResult = enablementService.enableIntegration(
                workspaceId,
                EnableIntegrationRequest(testIntegrationId, "nango-conn-123")
            )
            assertEquals(3, enableResult.entityTypesCreated)

            // --- Disable ---
            reset(installationRepository, entityTypeService, connectionService)
            whenever(definitionRepository.findById(testIntegrationId))
                .thenReturn(Optional.of(testDefinition))
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(savedInstallation)
            whenever(entityTypeService.softDeleteByIntegration(workspaceId, testIntegrationId))
                .thenReturn(IntegrationSoftDeleteResult(3, 2))
            whenever(connectionService.getConnection(workspaceId, testIntegrationId))
                .thenReturn(connectionEntity)
            whenever(connectionService.disconnectConnection(workspaceId, connectionEntity.id!!))
                .thenReturn(connectionEntity.copy(status = ConnectionStatus.DISCONNECTED))
            whenever(installationRepository.save(any<WorkspaceIntegrationInstallationEntity>()))
                .thenAnswer { invocation -> invocation.getArgument(0) as WorkspaceIntegrationInstallationEntity }

            val disableResult = enablementService.disableIntegration(
                workspaceId,
                DisableIntegrationRequest(testIntegrationId)
            )
            assertEquals(3, disableResult.entityTypesSoftDeleted)

            // --- Re-enable ---
            val softDeletedInstallation = savedInstallation.copy(
                deleted = true,
                deletedAt = java.time.ZonedDateTime.now()
            )
            reset(installationRepository, connectionService, materializationService)
            whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(null)
            whenever(installationRepository.findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(workspaceId, testIntegrationId))
                .thenReturn(softDeletedInstallation)
            whenever(connectionService.enableConnection(workspaceId, testIntegrationId, "nango-conn-456"))
                .thenReturn(connectionEntity.copy(nangoConnectionId = "nango-conn-456"))
            whenever(materializationService.materializeIntegrationTemplates(workspaceId, "hubspot"))
                .thenReturn(MaterializationResult(0, 3, 2, "hubspot"))
            whenever(installationRepository.save(any<WorkspaceIntegrationInstallationEntity>()))
                .thenAnswer { invocation -> invocation.getArgument(0) as WorkspaceIntegrationInstallationEntity }

            val reEnableResult = enablementService.enableIntegration(
                workspaceId,
                EnableIntegrationRequest(testIntegrationId, "nango-conn-456")
            )
            assertEquals(0, reEnableResult.entityTypesCreated)
            assertEquals(3, reEnableResult.entityTypesRestored)

            verify(installationRepository).save(argThat { inst ->
                !inst.deleted && inst.deletedAt == null
            })
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
                    workspaceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                    role = WorkspaceRoles.ADMIN
                )
            ]
        )
        fun `enableIntegration - rejects user without workspace access`() {
            val request = EnableIntegrationRequest(
                integrationDefinitionId = UUID.randomUUID(),
                nangoConnectionId = "nango-conn-123"
            )

            assertThrows<AccessDeniedException> {
                enablementService.enableIntegration(workspaceId, request)
            }
        }

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
        fun `enableIntegration - rejects non-admin user`() {
            val request = EnableIntegrationRequest(
                integrationDefinitionId = UUID.randomUUID(),
                nangoConnectionId = "nango-conn-123"
            )

            assertThrows<AccessDeniedException> {
                enablementService.enableIntegration(workspaceId, request)
            }
        }

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
