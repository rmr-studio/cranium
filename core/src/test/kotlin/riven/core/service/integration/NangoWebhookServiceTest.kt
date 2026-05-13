package cranium.core.service.integration

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowExecutionAlreadyStarted
import io.temporal.client.WorkflowOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import cranium.core.entity.integration.IntegrationDefinitionEntity
import cranium.core.enums.integration.ConnectionStatus
import cranium.core.enums.integration.InstallationStatus
import cranium.core.enums.integration.IntegrationCategory
import org.springframework.transaction.TransactionStatus
import cranium.core.models.integration.NangoSyncResults
import cranium.core.models.integration.NangoWebhookPayload
import cranium.core.models.integration.NangoWebhookTags
import cranium.core.models.integration.materialization.MaterializationResult
import cranium.core.repository.integration.IntegrationConnectionRepository
import cranium.core.repository.integration.IntegrationDefinitionRepository
import cranium.core.repository.integration.WorkspaceIntegrationInstallationRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.integration.materialization.TemplateMaterializationService
import cranium.core.service.integration.sync.IntegrationSyncWorkflow
import cranium.core.service.util.factory.integration.IntegrationFactory
import java.util.*

/**
 * Unit tests for NangoWebhookService.
 *
 * Note: No @WithUserPersona — the webhook service does not use JWT auth.
 * UserId comes from Nango webhook tags, not from a JWT security context.
 */
@SpringBootTest(
    classes = [
        NangoWebhookServiceTest.TestConfig::class,
        NangoWebhookService::class
    ]
)
class NangoWebhookServiceTest {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var connectionRepository: IntegrationConnectionRepository

    @MockitoBean
    private lateinit var definitionRepository: IntegrationDefinitionRepository

    @MockitoBean
    private lateinit var installationRepository: WorkspaceIntegrationInstallationRepository

    @MockitoBean
    private lateinit var templateMaterializationService: TemplateMaterializationService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var transactionTemplate: TransactionTemplate

    @MockitoBean
    private lateinit var workflowClient: WorkflowClient

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var webhookService: NangoWebhookService

    private val userId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val workspaceId: UUID = UUID.fromString("a1b2c3d4-5e6f-7890-abcd-ef1234567890")
    private val integrationDefinitionId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private lateinit var testDefinition: IntegrationDefinitionEntity
    private val nangoConnectionId = "nango-conn-test-123"

    @BeforeEach
    fun setup() {
        reset(connectionRepository, definitionRepository, installationRepository, templateMaterializationService, activityService, transactionTemplate, workflowClient)

        // TransactionTemplate.execute should immediately invoke the callback
        whenever(transactionTemplate.execute<Any?>(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as org.springframework.transaction.support.TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }

        testDefinition = IntegrationFactory.createIntegrationDefinition(
            id = integrationDefinitionId,
            slug = "hubspot",
            name = "HubSpot",
            category = IntegrationCategory.CRM,
            nangoProviderKey = "hubspot"
        )

        whenever(definitionRepository.findById(integrationDefinitionId))
            .thenReturn(Optional.of(testDefinition))

        whenever(connectionRepository.findByWorkspaceIdAndIntegrationId(any(), any()))
            .thenReturn(null)

        whenever(installationRepository.findByWorkspaceIdAndIntegrationDefinitionId(any(), any()))
            .thenReturn(null)

        whenever(installationRepository.findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(any(), any()))
            .thenReturn(null)

        whenever(connectionRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(installationRepository.save(any())).thenAnswer { it.arguments[0] }

        whenever(templateMaterializationService.materializeIntegrationTemplates(any(), any(), any()))
            .thenReturn(MaterializationResult(1, 0, 0, "hubspot", emptyList()))
    }

    // ------ Helpers ------

    private fun buildAuthPayload(
        success: Boolean? = true,
        tags: NangoWebhookTags? = buildValidTags(),
        connectionId: String? = nangoConnectionId
    ) = NangoWebhookPayload(
        type = "auth",
        success = success,
        connectionId = connectionId,
        tags = tags
    )

    private fun buildValidTags() = NangoWebhookTags(
        endUserId = userId.toString(),
        organizationId = workspaceId.toString(),
        endUserEmail = integrationDefinitionId.toString()
    )

    // ------ AuthEventTests ------

    @Nested
    inner class AuthEventTests {

        @Test
        fun `auth event with valid tags creates new connection in CONNECTED state`() {
            webhookService.handleWebhook(buildAuthPayload())

            verify(connectionRepository).save(argThat { conn ->
                conn.workspaceId == workspaceId &&
                    conn.integrationId == integrationDefinitionId &&
                    conn.nangoConnectionId == nangoConnectionId &&
                    conn.status == ConnectionStatus.CONNECTED
            })
        }

        @Test
        fun `auth event with valid tags creates installation with ACTIVE status`() {
            webhookService.handleWebhook(buildAuthPayload())

            verify(installationRepository).save(argThat { inst ->
                inst.workspaceId == workspaceId &&
                    inst.integrationDefinitionId == integrationDefinitionId &&
                    inst.installedBy == userId &&
                    inst.status == InstallationStatus.ACTIVE
            })
        }

        @Test
        fun `auth event triggers materialization after connection and installation creation`() {
            webhookService.handleWebhook(buildAuthPayload())

            verify(templateMaterializationService).materializeIntegrationTemplates(
                workspaceId,
                testDefinition.slug,
                integrationDefinitionId
            )
        }

        @Test
        fun `materialization failure sets installation to FAILED and connection is preserved as CONNECTED`() {
            whenever(templateMaterializationService.materializeIntegrationTemplates(any(), any(), any()))
                .thenThrow(RuntimeException("Materialization failed"))

            // Track the installation saved by save()
            var savedInstallation: cranium.core.entity.integration.WorkspaceIntegrationInstallationEntity? = null
            whenever(installationRepository.save(any())).thenAnswer { invocation ->
                val inst = invocation.arguments[0] as cranium.core.entity.integration.WorkspaceIntegrationInstallationEntity
                savedInstallation = inst
                inst
            }

            webhookService.handleWebhook(buildAuthPayload())

            // Verify connection is still saved with CONNECTED status
            verify(connectionRepository).save(argThat { conn ->
                conn.status == ConnectionStatus.CONNECTED
            })

            // Verify installation was ultimately saved with FAILED status
            // (save is called twice: once for ACTIVE, once for FAILED after materialization failure)
            verify(installationRepository, org.mockito.kotlin.atLeastOnce()).save(argThat { inst ->
                inst.status == InstallationStatus.FAILED
            })
        }

        @Test
        fun `auth event with missing tags (null tags) logs error and does not create anything`() {
            val payload = buildAuthPayload(tags = null)

            webhookService.handleWebhook(payload)

            verify(connectionRepository, never()).save(any())
            verify(installationRepository, never()).save(any())
            verify(templateMaterializationService, never()).materializeIntegrationTemplates(any(), any(), any())
        }

        @Test
        fun `auth event with missing userId tag logs error and does not create anything`() {
            val tagsWithMissingUserId = NangoWebhookTags(
                endUserId = null,
                organizationId = workspaceId.toString(),
                endUserEmail = integrationDefinitionId.toString()
            )
            val payload = buildAuthPayload(tags = tagsWithMissingUserId)

            webhookService.handleWebhook(payload)

            verify(connectionRepository, never()).save(any())
            verify(installationRepository, never()).save(any())
        }

        @Test
        fun `auth event with invalid UUID in tags logs error and does not create anything`() {
            val tagsWithBadUuid = NangoWebhookTags(
                endUserId = "not-a-uuid",
                organizationId = workspaceId.toString(),
                endUserEmail = integrationDefinitionId.toString()
            )
            val payload = buildAuthPayload(tags = tagsWithBadUuid)

            webhookService.handleWebhook(payload)

            verify(connectionRepository, never()).save(any())
            verify(installationRepository, never()).save(any())
        }

        @Test
        fun `auth event with success=false logs warning and does not create anything`() {
            val payload = buildAuthPayload(success = false)

            webhookService.handleWebhook(payload)

            verify(connectionRepository, never()).save(any())
            verify(installationRepository, never()).save(any())
        }

        @Test
        fun `auth event for existing DISCONNECTED connection reconnects to CONNECTED`() {
            val existingConnection = IntegrationFactory.createIntegrationConnection(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                integrationId = integrationDefinitionId,
                nangoConnectionId = "old-conn-id",
                status = ConnectionStatus.DISCONNECTED
            )

            whenever(connectionRepository.findByWorkspaceIdAndIntegrationId(workspaceId, integrationDefinitionId))
                .thenReturn(existingConnection)

            webhookService.handleWebhook(buildAuthPayload())

            verify(connectionRepository).save(argThat { conn ->
                conn.status == ConnectionStatus.CONNECTED &&
                    conn.nangoConnectionId == nangoConnectionId
            })
        }

        @Test
        fun `auth event for existing CONNECTED connection is idempotent`() {
            val existingConnection = IntegrationFactory.createIntegrationConnection(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                integrationId = integrationDefinitionId,
                nangoConnectionId = nangoConnectionId,
                status = ConnectionStatus.CONNECTED
            )

            whenever(connectionRepository.findByWorkspaceIdAndIntegrationId(workspaceId, integrationDefinitionId))
                .thenReturn(existingConnection)

            webhookService.handleWebhook(buildAuthPayload())

            // Materialization should still be triggered
            verify(templateMaterializationService).materializeIntegrationTemplates(any(), any(), any())
        }
    }

    // ------ SyncDispatchTests ------

    @Nested
    inner class SyncDispatchTests {

        private val connectionId: UUID = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
        private val nangoSyncConnectionId = "nango-conn-1"

        @BeforeEach
        fun setupSyncMocks() {
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                workspaceId = workspaceId,
                integrationId = integrationDefinitionId,
                nangoConnectionId = nangoSyncConnectionId,
                status = ConnectionStatus.CONNECTED
            )
            whenever(connectionRepository.findByNangoConnectionId(nangoSyncConnectionId)).thenReturn(connection)
        }

        @Test
        fun `dispatches workflow with deterministic ID on sync event`() {
            val mockWorkflow = mock<IntegrationSyncWorkflow>()
            whenever(workflowClient.newWorkflowStub(eq(IntegrationSyncWorkflow::class.java), any<WorkflowOptions>()))
                .thenReturn(mockWorkflow)

            val payload = NangoWebhookPayload(
                type = "sync",
                connectionId = nangoSyncConnectionId,
                model = "contacts",
                providerConfigKey = "hubspot"
            )

            webhookService.handleWebhook(payload)

            verify(workflowClient).newWorkflowStub(
                eq(IntegrationSyncWorkflow::class.java),
                argThat<WorkflowOptions> { options ->
                    options.workflowId == "sync-$connectionId-contacts"
                }
            )
            verify(mockWorkflow).execute(argThat { input ->
                input.connectionId == connectionId && input.model == "contacts"
            })
        }

        @Test
        fun `ignores duplicate webhook when workflow already running`() {
            val mockWorkflow = mock<IntegrationSyncWorkflow>()
            whenever(workflowClient.newWorkflowStub(eq(IntegrationSyncWorkflow::class.java), any<WorkflowOptions>()))
                .thenReturn(mockWorkflow)

            val alreadyStarted = WorkflowExecutionAlreadyStarted(
                WorkflowExecution.newBuilder()
                    .setWorkflowId("sync-$connectionId-contacts")
                    .setRunId("run-1")
                    .build(),
                "IntegrationSyncWorkflow",
                null
            )
            whenever(mockWorkflow.execute(any())).thenThrow(alreadyStarted)

            val payload = NangoWebhookPayload(
                type = "sync",
                connectionId = nangoSyncConnectionId,
                model = "contacts",
                providerConfigKey = "hubspot"
            )

            assertDoesNotThrow {
                webhookService.handleWebhook(payload)
            }
        }

        @Test
        fun `skips dispatch when connectionId is null`() {
            val payload = NangoWebhookPayload(
                type = "sync",
                connectionId = null,
                model = "contacts"
            )

            webhookService.handleWebhook(payload)

            verify(workflowClient, never()).newWorkflowStub(any<Class<*>>(), any<WorkflowOptions>())
        }

        @Test
        fun `skips dispatch when model is null`() {
            val payload = NangoWebhookPayload(
                type = "sync",
                connectionId = nangoSyncConnectionId,
                model = null
            )

            webhookService.handleWebhook(payload)

            verify(workflowClient, never()).newWorkflowStub(any<Class<*>>(), any<WorkflowOptions>())
        }

        @Test
        fun `skips dispatch when connection not found`() {
            whenever(connectionRepository.findByNangoConnectionId("unknown-conn")).thenReturn(null)

            val payload = NangoWebhookPayload(
                type = "sync",
                connectionId = "unknown-conn",
                model = "contacts"
            )

            webhookService.handleWebhook(payload)

            verify(workflowClient, never()).newWorkflowStub(any<Class<*>>(), any<WorkflowOptions>())
        }
    }

    // ------ ErrorHandlingTests ------

    @Nested
    inner class ErrorHandlingTests {

        /**
         * Regression: Uncaught exceptions from webhook processing leaked as 5xx, causing
         * Nango retries. Fix wraps handleWebhook() dispatch in try-catch.
         * Verifies the catch swallows the exception so the controller returns 200.
         */
        @Test
        fun `unexpected exception during auth processing does not propagate`() {
            whenever(connectionRepository.save(any())).thenThrow(RuntimeException("DB connection lost"))

            assertDoesNotThrow {
                webhookService.handleWebhook(buildAuthPayload())
            }
        }
    }

    // ------ EventRoutingTests ------

    @Nested
    inner class EventRoutingTests {

        @Test
        fun `unknown event type is ignored without creating anything`() {
            val unknownPayload = NangoWebhookPayload(
                type = "unknown-event-type"
            )

            webhookService.handleWebhook(unknownPayload)

            verify(connectionRepository, never()).save(any())
            verify(installationRepository, never()).save(any())
            verify(templateMaterializationService, never()).materializeIntegrationTemplates(any(), any(), any())
        }
    }
}
