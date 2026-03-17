package riven.core.controller.integration

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import riven.core.models.integration.IntegrationConnectionModel
import riven.core.models.integration.IntegrationDefinitionModel
import riven.core.models.request.integration.DisableIntegrationRequest
import riven.core.models.response.integration.IntegrationDisableResponse
import riven.core.service.integration.IntegrationConnectionService
import riven.core.service.integration.IntegrationDefinitionService
import riven.core.service.integration.IntegrationEnablementService
import java.util.*

@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "integrations")
class IntegrationController(
    private val enablementService: IntegrationEnablementService,
    private val definitionService: IntegrationDefinitionService,
    private val connectionService: IntegrationConnectionService
) {

    @GetMapping
    @Operation(summary = "List all available integrations")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of available integration definitions")
    )
    fun listAvailableIntegrations(): ResponseEntity<List<IntegrationDefinitionModel>> {
        return ResponseEntity.ok(definitionService.getActiveIntegrations().map { it.toModel() })
    }

    @GetMapping("/{workspaceId}/status")
    @Operation(summary = "Get integration status for workspace")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of workspace integration connections"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "403", description = "Forbidden — workspace access denied")
    )
    fun getWorkspaceIntegrationStatus(
        @PathVariable workspaceId: UUID
    ): ResponseEntity<List<IntegrationConnectionModel>> {
        return ResponseEntity.ok(connectionService.getConnectionsByWorkspace(workspaceId).map { it.toModel() })
    }

    @PostMapping("/{workspaceId}/disable")
    @Operation(summary = "Disable an integration for a workspace")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Integration disabled successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "403", description = "Forbidden — admin role required"),
        ApiResponse(responseCode = "404", description = "Integration not enabled in this workspace")
    )
    fun disableIntegration(
        @PathVariable workspaceId: UUID,
        @Valid @RequestBody request: DisableIntegrationRequest
    ): ResponseEntity<IntegrationDisableResponse> {
        return ResponseEntity.ok(enablementService.disableIntegration(workspaceId, request))
    }
}
