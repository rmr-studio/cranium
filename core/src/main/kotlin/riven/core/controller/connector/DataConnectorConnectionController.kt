package cranium.core.controller.connector

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import cranium.core.models.connector.ConnectorTestResult
import cranium.core.models.connector.DataConnectorConnectionModel
import cranium.core.models.connector.request.CreateDataConnectorConnectionRequest
import cranium.core.models.connector.request.DataConnectorConnectionTestRequest
import cranium.core.models.connector.request.UpdateDataConnectorConnectionRequest
import cranium.core.service.connector.DataConnectorConnectionService
import java.util.UUID

/**
 * REST surface for custom-source Postgres connections (Phase 2 CONN-05).
 *
 * Thin — delegates to [DataConnectorConnectionService]; workspace scoping and
 * business logic live in the service layer per core/CLAUDE.md.
 */
@RestController
@ConditionalOnProperty(prefix = "cranium.connector", name = ["enabled"], havingValue = "true")
@RequestMapping("/api/v1/connector/connections")
@Tag(name = "data-connector", description = "Manage connections to custom data sources for use in data connector syncs and queries")
class DataConnectorConnectionController(
    private val service: DataConnectorConnectionService,
) {

    @PostMapping("/test")
    @Operation(summary = "Dry-run gate validation for a prospective connection")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Gate validation result (pass/fail)"),
        ApiResponse(responseCode = "400", description = "Invalid request or gate failure"),
    )
    fun test(
        @Valid @RequestBody request: DataConnectorConnectionTestRequest,
    ): ResponseEntity<ConnectorTestResult> = ResponseEntity.ok(service.test(request))

    @PostMapping
    @Operation(summary = "Create a custom-source Postgres connection")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Connection created"),
        ApiResponse(responseCode = "400", description = "Invalid request, SSRF rejected, or role not read-only"),
        ApiResponse(responseCode = "403", description = "Workspace access denied"),
    )
    fun create(
        @Valid @RequestBody request: CreateDataConnectorConnectionRequest,
    ): ResponseEntity<DataConnectorConnectionModel> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(request))

    @GetMapping
    @Operation(summary = "List connections for a workspace")
    @ApiResponses(
        ApiResponse(responseCode = "200"),
        ApiResponse(responseCode = "403", description = "Workspace access denied"),
    )
    fun list(
        @RequestParam workspaceId: UUID,
    ): ResponseEntity<List<DataConnectorConnectionModel>> =
        ResponseEntity.ok(service.listByWorkspace(workspaceId))

    @GetMapping("/{id}")
    @Operation(summary = "Get a connection by id")
    @ApiResponses(
        ApiResponse(responseCode = "200"),
        ApiResponse(responseCode = "403", description = "Workspace access denied"),
        ApiResponse(responseCode = "404", description = "Connection not found"),
    )
    fun getById(
        @RequestParam workspaceId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<DataConnectorConnectionModel> =
        ResponseEntity.ok(service.getById(workspaceId, id))

    @PatchMapping("/{id}")
    @Operation(summary = "Partial update of a connection (re-runs gates on credential-touching fields)")
    @ApiResponses(
        ApiResponse(responseCode = "200"),
        ApiResponse(responseCode = "400", description = "Invalid request, SSRF rejected, or role not read-only"),
        ApiResponse(responseCode = "403"),
        ApiResponse(responseCode = "404"),
    )
    fun update(
        @RequestParam workspaceId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateDataConnectorConnectionRequest,
    ): ResponseEntity<DataConnectorConnectionModel> =
        ResponseEntity.ok(service.update(workspaceId, id, request))

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a connection")
    @ApiResponses(
        ApiResponse(responseCode = "204"),
        ApiResponse(responseCode = "403"),
        ApiResponse(responseCode = "404"),
    )
    fun delete(
        @RequestParam workspaceId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        service.softDelete(workspaceId, id)
        return ResponseEntity.noContent().build()
    }
}
