package riven.core.controller.connector

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import riven.core.configuration.properties.ApplicationConfigurationProperties
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.exceptions.ExceptionHandler
import riven.core.models.connector.CursorIndexWarning
import riven.core.models.connector.response.DataConnectorMappingSaveResponse
import riven.core.models.connector.response.DataConnectorSchemaResponse
import riven.core.models.connector.response.DriftStatus
import riven.core.models.connector.response.TableSchemaResponse
import riven.core.service.connector.mapping.DataConnectorFieldMappingService
import riven.core.service.connector.mapping.DataConnectorSchemaInferenceService
import java.util.UUID

/**
 * MockMvc wire-level tests for [DataConnectorMappingController] (Phase 3 plan 03-03).
 *
 * Per Phase 2 02-04 lesson, @PreAuthorize workspace-scoping is not exercised
 * here (standalone MockMvc does not load method security). The 403
 * workspace-mismatch assertion lives at the service-layer SpringBootTest
 * (see [riven.core.service.connector.mapping.DataConnectorFieldMappingServiceTest]
 * + [riven.core.service.connector.mapping.DataConnectorSchemaInferenceServiceTest]).
 */
class DataConnectorMappingControllerTest {

    private val inferenceService: DataConnectorSchemaInferenceService =
        mock(DataConnectorSchemaInferenceService::class.java)
    private val fieldMappingService: DataConnectorFieldMappingService =
        mock(DataConnectorFieldMappingService::class.java)
    private val logger: KLogger = mock(KLogger::class.java)

    private val controller = DataConnectorMappingController(inferenceService, fieldMappingService)

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val config = ApplicationConfigurationProperties(
        includeStackTrace = false,
        supabaseUrl = "http://test",
        supabaseKey = "test",
    )
    private val advice = ExceptionHandler(logger, config)

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setControllerAdvice(advice)
        .build()

    private val workspaceId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val connectionId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @BeforeEach
    fun setup() {
        reset(inferenceService, fieldMappingService)
    }

    @Test
    fun getSchemaReturns200WithTablesAndDriftIndicator() {
        val response = DataConnectorSchemaResponse(
            tables = listOf(
                TableSchemaResponse(
                    tableName = "customers",
                    schemaHash = "hash-abc",
                    driftStatus = DriftStatus.CLEAN,
                    columns = emptyList(),
                ),
            ),
        )
        whenever(inferenceService.getSchema(workspaceId, connectionId)).thenReturn(response)

        mockMvc.perform(
            get("/api/v1/custom-sources/connections/{connectionId}/schema", connectionId)
                .param("workspaceId", workspaceId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tables[0].tableName").value("customers"))
            .andExpect(jsonPath("$.tables[0].driftStatus").value("CLEAN"))
    }

    @Test
    fun saveMappingReturns201WithCreatedEntityTypeId() {
        val entityTypeId = UUID.randomUUID()
        whenever(fieldMappingService.saveMapping(any(), any(), any(), any()))
            .thenReturn(DataConnectorMappingSaveResponse(entityTypeId = entityTypeId))

        val body = objectMapper.writeValueAsString(validRequest())

        mockMvc.perform(
            post(
                "/api/v1/custom-sources/connections/{connectionId}/schema/tables/{tableName}/mapping",
                connectionId, "customers",
            )
                .param("workspaceId", workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/v1/entity-types/$entityTypeId"))
            .andExpect(jsonPath("$.entityTypeId").value(entityTypeId.toString()))
    }

    @Test
    fun saveMappingResponseIncludesCursorIndexWarningWhenColumnUnindexed() {
        val entityTypeId = UUID.randomUUID()
        whenever(fieldMappingService.saveMapping(any(), any(), any(), any()))
            .thenReturn(
                DataConnectorMappingSaveResponse(
                    entityTypeId = entityTypeId,
                    cursorIndexWarning = CursorIndexWarning(
                        column = "updated_at",
                        suggestedDdl = "CREATE INDEX ON \"public\".\"customers\" (\"updated_at\");",
                    ),
                ),
            )

        val body = objectMapper.writeValueAsString(validRequest())

        mockMvc.perform(
            post(
                "/api/v1/custom-sources/connections/{connectionId}/schema/tables/{tableName}/mapping",
                connectionId, "customers",
            )
                .param("workspaceId", workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.cursorIndexWarning.column").value("updated_at"))
            .andExpect(jsonPath("$.cursorIndexWarning.suggestedDdl").value(org.hamcrest.Matchers.containsString("CREATE INDEX")))
    }

    // 403 workspace-mismatch assertion is exercised at the service layer via
    // @SpringBootTest + @PreAuthorize: see
    // DataConnectorSchemaInferenceServiceTest.getSchemaScopedToWorkspaceViaPreAuthorize
    // and DataConnectorFieldMappingServiceTest.saveScopedToWorkspaceViaPreAuthorize.
    // A standalone MockMvc setup (used here) does not load method security, so
    // a controller-layer "403" test would be a no-op — removed per PR review.

    @Test
    fun saveMappingValidatesRequestBody() {
        val invalidBody = """
            {
              "lifecycleDomain": "UNCATEGORIZED",
              "semanticGroup": "UNCATEGORIZED",
              "columns": []
            }
        """.trimIndent()

        mockMvc.perform(
            post(
                "/api/v1/custom-sources/connections/{connectionId}/schema/tables/{tableName}/mapping",
                connectionId, "customers",
            )
                .param("workspaceId", workspaceId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody),
        )
            .andExpect(status().isBadRequest)
    }

    // ------ Helpers ------

    private fun validRequest(): Map<String, Any> = mapOf(
        "lifecycleDomain" to LifecycleDomain.UNCATEGORIZED.name,
        "semanticGroup" to SemanticGroup.UNCATEGORIZED.name,
        "columns" to listOf(
            mapOf(
                "columnName" to "id",
                "attributeName" to "id",
                "schemaType" to SchemaType.ID.name,
                "isIdentifier" to false,
                "isSyncCursor" to false,
                "isMapped" to true,
            ),
        ),
    )
}
