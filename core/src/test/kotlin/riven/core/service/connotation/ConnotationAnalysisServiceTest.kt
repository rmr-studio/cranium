package riven.core.service.connotation

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.catalog.ScaleMappingType
import riven.core.models.catalog.SentimentScale
import riven.core.models.common.json.JsonObject
import riven.core.models.connotation.AnalysisTier
import riven.core.models.connotation.SentimentLabel
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import java.util.UUID

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        ConnotationAnalysisService::class,
        DeterministicConnotationMapper::class,
        ConnotationAnalysisServiceTest.TestConfig::class,
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
class ConnotationAnalysisServiceTest {

    @Configuration
    class TestConfig {
        @Bean
        fun connotationAnalysisConfigurationProperties() = ConnotationAnalysisConfigurationProperties(
            deterministicCurrentVersion = "v1",
        )
    }

    private val workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")
    private val userId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: ConnotationAnalysisService

    private fun signals(
        tier: AnalysisTier = AnalysisTier.DETERMINISTIC,
        attribute: String = "satisfaction_score",
        sourceMin: Double = 1.0,
        sourceMax: Double = 5.0,
        targetMin: Double = -1.0,
        targetMax: Double = 1.0,
        mappingType: ScaleMappingType = ScaleMappingType.LINEAR,
        themes: List<String> = emptyList(),
    ) = ConnotationSignals(
        tier = tier,
        sentimentAttribute = attribute,
        sentimentScale = SentimentScale(sourceMin, sourceMax, targetMin, targetMax, mappingType),
        themeAttributes = themes,
    )

    @Test
    fun `DETERMINISTIC success populates sentiment metadata with ANALYZED status and logs activity`() {
        val entityId = UUID.randomUUID()
        whenever(
            activityService.logActivity(
                any(), any(), any(), any(), any(), anyOrNull(), any(), any()
            )
        ).thenReturn(mock())

        val metadata = service.analyze(
            entityId = entityId,
            workspaceId = workspaceId,
            signals = signals(themes = listOf("tags")),
            sourceValue = 5.0,
            themeValues = mapOf("tags" to "billing"),
        )

        assertEquals(1.0, metadata.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.VERY_POSITIVE, metadata.sentimentLabel)
        assertEquals(ConnotationStatus.ANALYZED, metadata.status)
        assertEquals(AnalysisTier.DETERMINISTIC, metadata.analysisTier)
        assertEquals("v1", metadata.analysisVersion)
        assertEquals("deterministic-connotation-linear-v1", metadata.analysisModel)
        assertNotNull(metadata.analyzedAt)
        assertEquals(listOf("billing"), metadata.themes)

        val detailsCaptor = argumentCaptor<JsonObject>()
        verify(activityService).logActivity(
            eq(Activity.ENTITY_CONNOTATION),
            eq(OperationType.ANALYZE),
            eq(userId),
            eq(workspaceId),
            eq(ApplicationEntityType.ENTITY_CONNOTATION),
            eq(entityId),
            any(),
            detailsCaptor.capture(),
        )
        val details = detailsCaptor.firstValue
        assertEquals("DETERMINISTIC", details["tier"])
        assertEquals("ANALYZED", details["status"])
        assertEquals(1.0, details["sentiment"])
        assertEquals("v1", details["analysisVersion"])
    }

    @Test
    fun `DETERMINISTIC missing source value returns FAILED metadata and still logs activity`() {
        val entityId = UUID.randomUUID()
        whenever(
            activityService.logActivity(
                any(), any(), any(), any(), any(), anyOrNull(), any(), any()
            )
        ).thenReturn(mock())

        val metadata = service.analyze(
            entityId = entityId,
            workspaceId = workspaceId,
            signals = signals(),
            sourceValue = null,
            themeValues = emptyMap(),
        )

        assertEquals(ConnotationStatus.FAILED, metadata.status)
        assertNull(metadata.sentiment)
        assertNull(metadata.sentimentLabel)
        assertEquals(AnalysisTier.DETERMINISTIC, metadata.analysisTier)
        assertEquals("v1", metadata.analysisVersion)
        assertEquals("deterministic-connotation-linear-v1", metadata.analysisModel)
        assertNotNull(metadata.analyzedAt)
        assertTrue(metadata.themes.isEmpty())

        val detailsCaptor = argumentCaptor<JsonObject>()
        verify(activityService).logActivity(
            eq(Activity.ENTITY_CONNOTATION),
            eq(OperationType.ANALYZE),
            eq(userId),
            eq(workspaceId),
            eq(ApplicationEntityType.ENTITY_CONNOTATION),
            eq(entityId),
            any(),
            detailsCaptor.capture(),
        )
        val details = detailsCaptor.firstValue
        assertEquals("DETERMINISTIC", details["tier"])
        assertEquals("FAILED", details["status"])
        assertNull(details["sentiment"])
    }

    @Test
    fun `DETERMINISTIC non-numeric source value returns FAILED metadata`() {
        val entityId = UUID.randomUUID()
        whenever(
            activityService.logActivity(
                any(), any(), any(), any(), any(), anyOrNull(), any(), any()
            )
        ).thenReturn(mock())

        val metadata = service.analyze(
            entityId = entityId,
            workspaceId = workspaceId,
            signals = signals(),
            sourceValue = "not-a-number",
            themeValues = emptyMap(),
        )

        assertEquals(ConnotationStatus.FAILED, metadata.status)
        assertNull(metadata.sentiment)
        assertEquals(AnalysisTier.DETERMINISTIC, metadata.analysisTier)

        verify(activityService).logActivity(
            eq(Activity.ENTITY_CONNOTATION),
            eq(OperationType.ANALYZE),
            eq(userId),
            eq(workspaceId),
            eq(ApplicationEntityType.ENTITY_CONNOTATION),
            eq(entityId),
            any(),
            any(),
        )
    }

    @Test
    fun `CLASSIFIER returns FAILED sentinel and still logs activity`() {
        val entityId = UUID.randomUUID()
        whenever(
            activityService.logActivity(
                any(), any(), any(), any(), any(), anyOrNull(), any(), any()
            )
        ).thenReturn(mock())

        val metadata = service.analyze(
            entityId = entityId,
            workspaceId = workspaceId,
            signals = signals(tier = AnalysisTier.CLASSIFIER),
            sourceValue = 4.0,
            themeValues = emptyMap(),
        )

        assertEquals(ConnotationStatus.FAILED, metadata.status)
        assertEquals(AnalysisTier.CLASSIFIER, metadata.analysisTier)
        assertNull(metadata.sentiment)

        val detailsCaptor = argumentCaptor<JsonObject>()
        verify(activityService).logActivity(
            eq(Activity.ENTITY_CONNOTATION),
            eq(OperationType.ANALYZE),
            eq(userId),
            eq(workspaceId),
            eq(ApplicationEntityType.ENTITY_CONNOTATION),
            eq(entityId),
            any(),
            detailsCaptor.capture(),
        )
        val details = detailsCaptor.firstValue
        assertEquals("CLASSIFIER", details["tier"])
        assertEquals("FAILED", details["status"])
    }

    @Test
    fun `INFERENCE returns FAILED sentinel and still logs activity`() {
        val entityId = UUID.randomUUID()
        whenever(
            activityService.logActivity(
                any(), any(), any(), any(), any(), anyOrNull(), any(), any()
            )
        ).thenReturn(mock())

        val metadata = service.analyze(
            entityId = entityId,
            workspaceId = workspaceId,
            signals = signals(tier = AnalysisTier.INFERENCE),
            sourceValue = 4.0,
            themeValues = emptyMap(),
        )

        assertEquals(ConnotationStatus.FAILED, metadata.status)
        assertEquals(AnalysisTier.INFERENCE, metadata.analysisTier)

        verify(activityService).logActivity(
            eq(Activity.ENTITY_CONNOTATION),
            eq(OperationType.ANALYZE),
            eq(userId),
            eq(workspaceId),
            eq(ApplicationEntityType.ENTITY_CONNOTATION),
            eq(entityId),
            any(),
            any(),
        )
    }
}
