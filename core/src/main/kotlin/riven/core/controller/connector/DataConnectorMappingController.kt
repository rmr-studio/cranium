package riven.core.controller.connector

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import riven.core.models.connector.request.SaveDataConnectorMappingRequest
import riven.core.models.connector.response.DataConnectorMappingSaveResponse
import riven.core.models.connector.response.DataConnectorSchemaResponse
import riven.core.service.connector.mapping.DataConnectorFieldMappingService
import riven.core.service.connector.mapping.DataConnectorSchemaInferenceService
import java.util.UUID

/**
 * REST surface for custom-source Postgres schema inference + mapping Save
 * (Phase 3 plan 03-03). Thin — delegates to the two mapping services;
 * workspace scoping + business logic live in the service layer per
 * core/CLAUDE.md.
 */
@RestController
@ConditionalOnProperty(prefix = "riven.connector", name = ["enabled"], havingValue = "true")
@RequestMapping("/api/v1/custom-sources/connections/{connectionId}")
@Tag(
    name = "custom-source-mapping",
    description = "Introspect live Postgres schema + persist column-to-attribute mappings for a data-connector connection",
)
class DataConnectorMappingController(
    private val inferenceService: DataConnectorSchemaInferenceService,
    private val fieldMappingService: DataConnectorFieldMappingService,
) {

    @GetMapping("/schema")
    @Operation(summary = "Introspect live tables + stored mappings + drift + cursor-index warnings")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Schema + drift payload"),
        ApiResponse(responseCode = "403", description = "Workspace access denied"),
        ApiResponse(responseCode = "404", description = "Connection not found"),
    )
    fun getSchema(
        @RequestParam workspaceId: UUID,
        @PathVariable connectionId: UUID,
    ): ResponseEntity<DataConnectorSchemaResponse> =
        ResponseEntity.ok(inferenceService.getSchema(workspaceId, connectionId))

    @PostMapping("/schema/tables/{tableName}/mapping")
    @Operation(summary = "Persist column mappings; creates or updates the readonly EntityType")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Mapping saved; EntityType created or updated"),
        ApiResponse(responseCode = "400", description = "Invalid mapping request"),
        ApiResponse(responseCode = "403", description = "Workspace access denied"),
        ApiResponse(responseCode = "404", description = "Connection not found"),
    )
    fun saveMapping(
        @RequestParam workspaceId: UUID,
        @PathVariable connectionId: UUID,
        @PathVariable tableName: String,
        @Valid @RequestBody request: SaveDataConnectorMappingRequest,
    ): ResponseEntity<DataConnectorMappingSaveResponse> {
        val response = fieldMappingService.saveMapping(workspaceId, connectionId, tableName, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "/api/v1/entity-types/${response.entityTypeId}")
            .body(response)
    }
}
