package riven.core.service.connotation

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
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.configuration.properties.ConnotationAnalysisConfigurationProperties
import riven.core.enums.activity.Activity
import riven.core.enums.connotation.ConnotationAxisName
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.common.json.JsonObject
import riven.core.models.connotation.AnalysisTier
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import java.util.UUID

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
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
            tier1CurrentVersion = "v2",
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
    fun `Tier 1 reanalyze enqueues mismatched rows and returns count`() {
        whenever(
            executionQueueRepository.enqueueByAxisVersionMismatch(
                axisName = eq("SENTIMENT"),
                currentVersion = eq("v2"),
                workspaceId = eq(workspaceId),
            )
        ).thenReturn(42)
        whenever(
            activityService.logActivity(
                any(), any(), any(), any(), any(), anyOrNull(), any(), any()
            )
        ).thenReturn(mock())

        val count = service.reanalyzeAxisWhereVersionMismatch(
            axis = ConnotationAxisName.SENTIMENT,
            tier = AnalysisTier.TIER_1,
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
        assertEquals("SENTIMENT", details["axis"])
        assertEquals("TIER_1", details["tier"])
        assertEquals("v2", details["currentVersion"])
        assertEquals(42, details["enqueued"])
    }

    @Test
    fun `Tier 2 reanalyze throws NotImplementedError`() {
        assertThrows<NotImplementedError> {
            service.reanalyzeAxisWhereVersionMismatch(
                axis = ConnotationAxisName.SENTIMENT,
                tier = AnalysisTier.TIER_2,
                workspaceId = workspaceId,
            )
        }
    }

    @Test
    fun `RELATIONAL axis reanalyze throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            service.reanalyzeAxisWhereVersionMismatch(
                axis = ConnotationAxisName.RELATIONAL,
                tier = AnalysisTier.TIER_1,
                workspaceId = workspaceId,
            )
        }
    }

    @Test
    fun `STRUCTURAL axis reanalyze throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            service.reanalyzeAxisWhereVersionMismatch(
                axis = ConnotationAxisName.STRUCTURAL,
                tier = AnalysisTier.TIER_1,
                workspaceId = workspaceId,
            )
        }
    }
}
