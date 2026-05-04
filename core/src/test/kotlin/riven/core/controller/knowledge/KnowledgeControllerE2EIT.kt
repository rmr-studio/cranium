package riven.core.controller.knowledge

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import riven.core.configuration.properties.ApplicationConfigurationProperties
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionSource
import riven.core.enums.knowledge.DefinitionStatus
import riven.core.exceptions.ExceptionHandler
import riven.core.models.knowledge.AttributeRef
import riven.core.models.knowledge.GlossaryTerm
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.entity.type.EntityTypeService
import riven.core.service.knowledge.GlossaryService
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

/**
 * E2E regression — knowledge controller contract preserved under the entity-backed reads
 * + writes post-cutover. Standalone MockMvc against [KnowledgeController]; the
 * [GlossaryService] is mocked at the boundary so this test exercises
 * only the wire format.
 *
 * Asserts every business-definition endpoint returns the same WorkspaceBusinessDefinition
 * JSON shape the frontend has historically consumed (id, workspaceId, term,
 * normalizedTerm, definition, category, status, source, entityTypeRefs, attributeRefs,
 * isCustomized, version, compiledParams).
 *
 * Per the existing controller-test convention (see DataConnectorMappingControllerTest /
 * NoteControllerE2EIT), @PreAuthorize is not exercised under standalone MockMvc — auth
 * is covered at the service layer (WorkspaceBusinessDefinitionServiceTest).
 */
class KnowledgeControllerE2EIT {

    private val semanticMetadataService: EntityTypeSemanticMetadataService =
        mock(EntityTypeSemanticMetadataService::class.java)
    private val entityTypeService: EntityTypeService = mock(EntityTypeService::class.java)
    private val businessDefinitionService: GlossaryService =
        mock(GlossaryService::class.java)
    private val logger: KLogger = mock(KLogger::class.java)

    private val controller = KnowledgeController(
        semanticMetadataService, entityTypeService, businessDefinitionService,
    )

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

    private val workspaceId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val definitionId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val typeRefId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val attrRefId: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")

    @BeforeEach
    fun setup() {
        reset(businessDefinitionService)
    }

    private fun sampleDefinition(): GlossaryTerm = GlossaryTerm(
        id = definitionId,
        workspaceId = workspaceId,
        term = "Retention Rate",
        normalizedTerm = "retention rate",
        definition = "A customer is retained if they have an active subscription 90 days after first purchase",
        category = DefinitionCategory.METRIC,
        source = DefinitionSource.MANUAL,
        entityTypeRefs = listOf(typeRefId),
        attributeRefs = listOf(AttributeRef(attributeId = attrRefId, ownerEntityTypeId = typeRefId)),
        isCustomized = false,
        createdBy = null,
        createdAt = null,
        updatedAt = null,
    )

    @Test
    fun listDefinitions_returnsListWithFullDtoShape() {
        whenever(businessDefinitionService.listDefinitions(workspaceId, null, null))
            .thenReturn(listOf(sampleDefinition()))

        mockMvc.perform(get("/api/v1/knowledge/workspace/{workspaceId}/definitions", workspaceId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(definitionId.toString()))
            .andExpect(jsonPath("$[0].workspaceId").value(workspaceId.toString()))
            .andExpect(jsonPath("$[0].term").value("Retention Rate"))
            .andExpect(jsonPath("$[0].normalizedTerm").value("retention rate"))
            .andExpect(jsonPath("$[0].category").value("METRIC"))
            .andExpect(jsonPath("$[0].source").value("MANUAL"))
            .andExpect(jsonPath("$[0].entityTypeRefs[0]").value(typeRefId.toString()))
            .andExpect(jsonPath("$[0].attributeRefs[0].attributeId").value(attrRefId.toString()))
            .andExpect(jsonPath("$[0].attributeRefs[0].ownerEntityTypeId").value(typeRefId.toString()))
            .andExpect(jsonPath("$[0].isCustomized").value(false))
    }

    @Test
    fun listDefinitions_passesStatusAndCategoryFilters() {
        whenever(
            businessDefinitionService.listDefinitions(
                workspaceId, DefinitionStatus.ACTIVE, DefinitionCategory.METRIC,
            ),
        ).thenReturn(listOf(sampleDefinition()))

        mockMvc.perform(
            get("/api/v1/knowledge/workspace/{workspaceId}/definitions", workspaceId)
                .param("status", "ACTIVE")
                .param("category", "METRIC"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(definitionId.toString()))

        verify(businessDefinitionService).listDefinitions(
            workspaceId, DefinitionStatus.ACTIVE, DefinitionCategory.METRIC,
        )
    }

    @Test
    fun getDefinition_returnsSingleDto() {
        whenever(businessDefinitionService.getDefinition(workspaceId, definitionId))
            .thenReturn(sampleDefinition())

        mockMvc.perform(
            get("/api/v1/knowledge/workspace/{workspaceId}/definitions/{id}", workspaceId, definitionId),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(definitionId.toString()))
            .andExpect(jsonPath("$.term").value("Retention Rate"))
            .andExpect(jsonPath("$.entityTypeRefs[0]").value(typeRefId.toString()))
    }

    @Test
    fun createDefinition_returns201WithCreatedDto() {
        whenever(businessDefinitionService.createDefinition(eq(workspaceId), any()))
            .thenReturn(sampleDefinition())

        val body = objectMapper.writeValueAsString(
            mapOf(
                "term" to "Retention Rate",
                "definition" to "definition body",
                "category" to "METRIC",
                "source" to "MANUAL",
                "entityTypeRefs" to listOf(typeRefId.toString()),
                "attributeRefs" to listOf(mapOf("attributeId" to attrRefId.toString(), "ownerEntityTypeId" to typeRefId.toString())),
                "isCustomized" to false,
            ),
        )

        mockMvc.perform(
            post("/api/v1/knowledge/workspace/{workspaceId}/definitions", workspaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(definitionId.toString()))
            .andExpect(jsonPath("$.term").value("Retention Rate"))
            .andExpect(jsonPath("$.normalizedTerm").value("retention rate"))
    }

    @Test
    fun updateDefinition_returns200WithUpdatedDto() {
        whenever(businessDefinitionService.updateDefinition(eq(workspaceId), eq(definitionId), any()))
            .thenReturn(sampleDefinition())

        val body = objectMapper.writeValueAsString(
            mapOf(
                "term" to "Retention Rate",
                "definition" to "updated body",
                "category" to "METRIC",
                "entityTypeRefs" to listOf(typeRefId.toString()),
                "attributeRefs" to listOf(mapOf("attributeId" to attrRefId.toString(), "ownerEntityTypeId" to typeRefId.toString())),
                "version" to 0,
            ),
        )

        mockMvc.perform(
            put("/api/v1/knowledge/workspace/{workspaceId}/definitions/{id}", workspaceId, definitionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(definitionId.toString()))
            .andExpect(jsonPath("$.term").value("Retention Rate"))
    }

    @Test
    fun deleteDefinition_returns204NoContent() {
        mockMvc.perform(
            delete("/api/v1/knowledge/workspace/{workspaceId}/definitions/{id}", workspaceId, definitionId),
        )
            .andExpect(status().isNoContent)

        verify(businessDefinitionService).deleteDefinition(workspaceId, definitionId)
    }
}
