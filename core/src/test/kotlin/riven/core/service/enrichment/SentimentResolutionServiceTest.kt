package riven.core.service.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.enums.connotation.ConnotationStatus
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.catalog.ScaleMappingType
import riven.core.models.catalog.SentimentScale
import riven.core.models.connotation.AnalysisTier
import riven.core.models.connotation.SentimentMetadata
import riven.core.repository.workspace.WorkspaceRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.catalog.ManifestCatalogService
import riven.core.service.connotation.ConnotationAnalysisService
import riven.core.service.entity.EntityAttributeService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.WorkspaceFactory
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.enums.workspace.WorkspaceRoles
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [SentimentResolutionService] (extracted from [EnrichmentAnalysisService] in Plan 02-02).
 *
 * Lifted from [EnrichmentAnalysisServiceTest] to cover sentiment gating on the new service.
 * The originals in [EnrichmentAnalysisServiceTest] are NOT deleted in this plan — Plan 02-03
 * deletes them when it shrinks EnrichmentAnalysisService to use SentimentResolutionService.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        SentimentResolutionService::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@example.com",
    displayName = "Test User",
    roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
)
class SentimentResolutionServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var connotationAnalysisService: ConnotationAnalysisService

    @MockitoBean
    private lateinit var workspaceRepository: WorkspaceRepository

    @MockitoBean
    private lateinit var manifestCatalogService: ManifestCatalogService

    @MockitoBean
    private lateinit var entityAttributeService: EntityAttributeService

    @Autowired
    private lateinit var sentimentResolutionService: SentimentResolutionService

    private fun makeSignals(sentimentAttribute: String = "nps_score") = ConnotationSignals(
        tier = AnalysisTier.DETERMINISTIC,
        sentimentAttribute = sentimentAttribute,
        sentimentScale = SentimentScale(0.0, 100.0, -1.0, 1.0, ScaleMappingType.LINEAR),
        themeAttributes = emptyList(),
    )

    // ------ Test 1: workspace flag off ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class WorkspaceFlagOff {

        @Test
        fun `resolve returns NOT_APPLICABLE when workspace connotationEnabled is false`() {
            val entityId = UUID.randomUUID()
            val entityType = EntityFactory.createEntityType(workspaceId = workspaceId)
            whenever(workspaceRepository.findById(workspaceId)).thenReturn(
                Optional.of(WorkspaceFactory.createWorkspace(id = workspaceId, connotationEnabled = false))
            )

            val result = sentimentResolutionService.resolve(entityId, workspaceId, entityType)

            assertEquals(ConnotationStatus.NOT_APPLICABLE, result.status)
            verify(connotationAnalysisService, never()).analyze(any(), any(), any(), any(), any())
        }
    }

    // ------ Test 2: manifest signals null ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class ManifestSignalsNull {

        @Test
        fun `resolve returns NOT_APPLICABLE when manifest connotation signals are null`() {
            val entityId = UUID.randomUUID()
            val entityType = EntityFactory.createEntityType(workspaceId = workspaceId)
            whenever(workspaceRepository.findById(workspaceId)).thenReturn(
                Optional.of(WorkspaceFactory.createWorkspace(id = workspaceId, connotationEnabled = true))
            )
            whenever(manifestCatalogService.getConnotationSignalsForEntityType(any())).thenReturn(null)

            val result = sentimentResolutionService.resolve(entityId, workspaceId, entityType)

            assertEquals(ConnotationStatus.NOT_APPLICABLE, result.status)
            verify(connotationAnalysisService, never()).analyze(any(), any(), any(), any(), any())
        }
    }

    // ------ Test 3: manifest has signals + flag on → delegates to ConnotationAnalysisService ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class DelegatesWithSignals {

        @Test
        fun `resolve delegates to ConnotationAnalysisService when workspace flag on and manifest has signals and mapping exists`() {
            val entityId = UUID.randomUUID()
            val attrId = UUID.randomUUID()
            val signals = makeSignals(sentimentAttribute = "nps_score")
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                attributeKeyMapping = mapOf("nps_score" to attrId.toString())
            )

            whenever(workspaceRepository.findById(workspaceId)).thenReturn(
                Optional.of(WorkspaceFactory.createWorkspace(id = workspaceId, connotationEnabled = true))
            )
            whenever(manifestCatalogService.getConnotationSignalsForEntityType(any())).thenReturn(signals)

            val expected = SentimentMetadata(status = ConnotationStatus.ANALYZED)
            whenever(connotationAnalysisService.analyze(any(), any(), any(), anyOrNull(), any())).thenReturn(expected)

            val result = sentimentResolutionService.resolve(entityId, workspaceId, entityType)

            assertNotNull(result)
            assertEquals(ConnotationStatus.ANALYZED, result.status)
            verify(connotationAnalysisService).analyze(any(), any(), any(), anyOrNull(), any())
        }
    }
}
