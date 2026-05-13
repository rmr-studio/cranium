package cranium.core.service.connotation

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.configuration.properties.ConnotationAnalysisConfigurationProperties
import cranium.core.enums.activity.Activity
import cranium.core.enums.connotation.ConnotationMetadataType
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.models.common.json.JsonObject
import cranium.core.models.connotation.AnalysisTier
import cranium.core.repository.workflow.ExecutionQueueRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.util.SecurityTestConfig
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import org.springframework.security.access.AccessDeniedException
import java.util.UUID

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        ConnotationAdminService::class,
        ConnotationAdminServiceTest.TestConfig::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.OWNER,
        )
    ]
)
class ConnotationAdminServiceTest {

    @Configuration
    class TestConfig {
        @Bean
        fun connotationAnalysisConfigurationProperties() = ConnotationAnalysisConfigurationProperties(
            deterministicCurrentVersion = "v2",
        )
    }

    private val workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")
    private val userId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: ConnotationAdminService

    @Test
    fun `DETERMINISTIC reanalyze enqueues mismatched rows and returns count`() {
        whenever(
            executionQueueRepository.enqueueByMetadataVersionMismatch(
                metadataKey = eq("SENTIMENT"),
                currentVersion = eq("v2"),
                workspaceId = eq(workspaceId),
            )
        ).thenReturn(42)
        whenever(
            activityService.logActivity(
                any(), any(), any(), any(), any(), anyOrNull(), any(), any()
            )
        ).thenReturn(mock())

        val count = service.reanalyzeWhereMetadataVersionMismatch(
            metadataType = ConnotationMetadataType.SENTIMENT,
            tier = AnalysisTier.DETERMINISTIC,
            workspaceId = workspaceId,
        )

        assertEquals(42, count)

        val detailsCaptor = argumentCaptor<JsonObject>()
        verify(activityService).logActivity(
            eq(Activity.ENTITY_CONNOTATION),
            eq(OperationType.REANALYZE),
            eq(userId),
            eq(workspaceId),
            eq(ApplicationEntityType.ENTITY_CONNOTATION),
            eq(null),
            any(),
            detailsCaptor.capture(),
        )
        val details = detailsCaptor.firstValue
        assertEquals("SENTIMENT", details["metadataType"])
        assertEquals("DETERMINISTIC", details["tier"])
        assertEquals("v2", details["currentVersion"])
        assertEquals(42, details["enqueued"])
    }


    @Test
    fun `CLASSIFIER reanalyze throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            service.reanalyzeWhereMetadataVersionMismatch(
                metadataType = ConnotationMetadataType.SENTIMENT,
                tier = AnalysisTier.CLASSIFIER,
                workspaceId = workspaceId,
            )
        }
    }

    @Test
    fun `RELATIONAL reanalyze throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            service.reanalyzeWhereMetadataVersionMismatch(
                metadataType = ConnotationMetadataType.RELATIONAL,
                tier = AnalysisTier.DETERMINISTIC,
                workspaceId = workspaceId,
            )
        }
    }

    @Test
    fun `STRUCTURAL reanalyze throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            service.reanalyzeWhereMetadataVersionMismatch(
                metadataType = ConnotationMetadataType.STRUCTURAL,
                tier = AnalysisTier.DETERMINISTIC,
                workspaceId = workspaceId,
            )
        }
    }

    /**
     * Caller without a workspace role for the target workspaceId must be rejected by the
     * @PreAuthorize guard. The class-level persona owns one workspace; calling with a
     * different (unauthorized) workspaceId proves the @PreAuthorize check fires before
     * any business logic runs.
     */
    @Test
    fun `reanalyzeWhereVersionMismatch throws AccessDeniedException for unauthorized workspace`() {
        val unauthorizedWorkspaceId = UUID.randomUUID()

        assertThrows<AccessDeniedException> {
            service.reanalyzeWhereMetadataVersionMismatch(
                metadataType = ConnotationMetadataType.SENTIMENT,
                tier = AnalysisTier.DETERMINISTIC,
                workspaceId = unauthorizedWorkspaceId,
            )
        }
    }
}
