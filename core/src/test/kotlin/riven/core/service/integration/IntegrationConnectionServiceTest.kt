package riven.core.service.integration

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.integration.IntegrationConnectionEntity
import riven.core.entity.integration.IntegrationDefinitionEntity
import riven.core.enums.integration.ConnectionStatus
import riven.core.enums.integration.IntegrationCategory
import riven.core.exceptions.InvalidStateTransitionException
import riven.core.repository.integration.IntegrationConnectionRepository
import riven.core.repository.integration.IntegrationDefinitionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.integration.materialization.TemplateMaterializationService
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.enums.workspace.WorkspaceRoles
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.springframework.security.access.AccessDeniedException
import java.util.*

/**
 * Unit tests for IntegrationConnectionService.
 *
 * Tests the connection lifecycle management including:
 * - Updating connection status with state machine validation
 * - Disconnecting connections
 * - Security access control
 *
 * Note: createConnection() and enableConnection() were removed in Phase 2 —
 * connections are now created exclusively by the Nango auth webhook handler.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        IntegrationConnectionServiceTest.TestConfig::class,
        IntegrationConnectionService::class
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
class IntegrationConnectionServiceTest {

    @Configuration
    class TestConfig

    private val userId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")

    @MockitoBean
    private lateinit var connectionRepository: IntegrationConnectionRepository

    @MockitoBean
    private lateinit var definitionRepository: IntegrationDefinitionRepository

    @MockitoBean
    private lateinit var nangoClientWrapper: NangoClientWrapper

    @MockitoBean
    private lateinit var templateMaterializationService: TemplateMaterializationService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var authTokenService: AuthTokenService

    @MockitoBean
    private lateinit var transactionTemplate: TransactionTemplate

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var integrationConnectionService: IntegrationConnectionService

    private lateinit var testIntegrationId: UUID
    private lateinit var testConnectionId: UUID
    private lateinit var testDefinition: IntegrationDefinitionEntity

    @BeforeEach
    fun setup() {
        reset(connectionRepository, definitionRepository, nangoClientWrapper, activityService, transactionTemplate, templateMaterializationService)

        testIntegrationId = UUID.randomUUID()
        testConnectionId = UUID.randomUUID()

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

        // Configure TransactionTemplate to execute callbacks directly (no real transaction needed in unit tests)
        @Suppress("UNCHECKED_CAST")
        whenever(transactionTemplate.execute(any<TransactionCallback<Any?>>())).thenAnswer { invocation ->
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock<TransactionStatus>())
        }
    }

    // ========== updateConnectionStatus Tests ==========

    @Test
    fun `updateConnectionStatus - valid transition succeeds`() {
        val connection = IntegrationConnectionEntity(
            id = testConnectionId,
            workspaceId = workspaceId,
            integrationId = testIntegrationId,
            nangoConnectionId = "nango-conn-123",
            status = ConnectionStatus.CONNECTED
        )

        whenever(connectionRepository.findById(testConnectionId))
            .thenReturn(Optional.of(connection))

        val updatedConnection = connection.copy(status = ConnectionStatus.SYNCING)
        whenever(connectionRepository.save(any<IntegrationConnectionEntity>()))
            .thenReturn(updatedConnection)

        val result = integrationConnectionService.updateConnectionStatus(
            workspaceId = workspaceId,
            connectionId = testConnectionId,
            newStatus = ConnectionStatus.SYNCING
        )

        assertEquals(ConnectionStatus.SYNCING, result.status)

        verify(connectionRepository).save(argThat { conn ->
            conn.id == testConnectionId &&
                conn.status == ConnectionStatus.SYNCING
        })
    }

    @Test
    fun `updateConnectionStatus - invalid transition throws InvalidStateTransitionException`() {
        val connection = IntegrationConnectionEntity(
            id = testConnectionId,
            workspaceId = workspaceId,
            integrationId = testIntegrationId,
            nangoConnectionId = "nango-conn-123",
            status = ConnectionStatus.DISCONNECTED
        )

        whenever(connectionRepository.findById(testConnectionId))
            .thenReturn(Optional.of(connection))

        assertThrows(InvalidStateTransitionException::class.java) {
            integrationConnectionService.updateConnectionStatus(
                workspaceId = workspaceId,
                connectionId = testConnectionId,
                newStatus = ConnectionStatus.SYNCING
            )
        }

        verify(connectionRepository, never()).save(any<IntegrationConnectionEntity>())
    }

    @Test
    fun `updateConnectionStatus - merges metadata with existing metadata`() {
        val connection = IntegrationConnectionEntity(
            id = testConnectionId,
            workspaceId = workspaceId,
            integrationId = testIntegrationId,
            nangoConnectionId = "nango-conn-123",
            status = ConnectionStatus.CONNECTED,
            connectionMetadata = mapOf("existing_key" to "existing_value")
        )

        whenever(connectionRepository.findById(testConnectionId))
            .thenReturn(Optional.of(connection))

        val updatedConnection = connection.copy(
            status = ConnectionStatus.HEALTHY,
            connectionMetadata = mapOf(
                "existing_key" to "existing_value",
                "new_key" to "new_value"
            )
        )
        whenever(connectionRepository.save(any<IntegrationConnectionEntity>()))
            .thenReturn(updatedConnection)

        val result = integrationConnectionService.updateConnectionStatus(
            workspaceId = workspaceId,
            connectionId = testConnectionId,
            newStatus = ConnectionStatus.HEALTHY,
            metadata = mapOf("new_key" to "new_value")
        )

        assertEquals(2, result.connectionMetadata?.size)
        assertEquals("existing_value", result.connectionMetadata?.get("existing_key"))
        assertEquals("new_value", result.connectionMetadata?.get("new_key"))
    }

    // ========== disconnectConnection Tests ==========

    @Test
    fun `disconnectConnection - successfully transitions to DISCONNECTED`() {
        val connection = IntegrationConnectionEntity(
            id = testConnectionId,
            workspaceId = workspaceId,
            integrationId = testIntegrationId,
            nangoConnectionId = "nango-conn-123",
            status = ConnectionStatus.HEALTHY
        )

        whenever(connectionRepository.findById(testConnectionId))
            .thenReturn(Optional.of(connection))
        whenever(definitionRepository.findById(testIntegrationId))
            .thenReturn(Optional.of(testDefinition))
        whenever(connectionRepository.save(any<IntegrationConnectionEntity>()))
            .thenAnswer { invocation -> invocation.getArgument(0) as IntegrationConnectionEntity }

        val result = integrationConnectionService.disconnectConnection(
            workspaceId = workspaceId,
            connectionId = testConnectionId
        )

        assertEquals(ConnectionStatus.DISCONNECTED, result.status)

        verify(nangoClientWrapper).deleteConnection(
            providerConfigKey = "hubspot",
            connectionId = "nango-conn-123"
        )
    }

    @Test
    fun `disconnectConnection - marks as DISCONNECTED even if Nango delete fails`() {
        val connection = IntegrationConnectionEntity(
            id = testConnectionId,
            workspaceId = workspaceId,
            integrationId = testIntegrationId,
            nangoConnectionId = "nango-conn-123",
            status = ConnectionStatus.HEALTHY
        )

        whenever(connectionRepository.findById(testConnectionId))
            .thenReturn(Optional.of(connection))
        whenever(definitionRepository.findById(testIntegrationId))
            .thenReturn(Optional.of(testDefinition))
        whenever(connectionRepository.save(any<IntegrationConnectionEntity>()))
            .thenAnswer { invocation -> invocation.getArgument(0) as IntegrationConnectionEntity }

        whenever(nangoClientWrapper.deleteConnection(any(), any()))
            .thenThrow(RuntimeException("Nango API error"))

        val result = integrationConnectionService.disconnectConnection(
            workspaceId = workspaceId,
            connectionId = testConnectionId
        )

        assertEquals(ConnectionStatus.DISCONNECTED, result.status)
    }

    @Test
    fun `disconnectConnection - throws InvalidStateTransitionException for invalid state`() {
        val connection = IntegrationConnectionEntity(
            id = testConnectionId,
            workspaceId = workspaceId,
            integrationId = testIntegrationId,
            nangoConnectionId = "nango-conn-123",
            status = ConnectionStatus.SYNCING
        )

        whenever(connectionRepository.findById(testConnectionId))
            .thenReturn(Optional.of(connection))

        assertThrows(InvalidStateTransitionException::class.java) {
            integrationConnectionService.disconnectConnection(
                workspaceId = workspaceId,
                connectionId = testConnectionId
            )
        }

        verify(nangoClientWrapper, never()).deleteConnection(any(), any())
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
        fun `disconnectConnection - rejects non-admin user`() {
            assertThrows<AccessDeniedException> {
                integrationConnectionService.disconnectConnection(
                    workspaceId = workspaceId,
                    connectionId = UUID.randomUUID()
                )
            }
        }
    }
}
