package riven.core.controller.workspace

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import riven.core.models.catalog.ReconciliationImpact
import riven.core.models.catalog.SchemaHealthResponse
import riven.core.models.request.entity.type.SchemaReconcileRequest
import riven.core.service.catalog.SchemaReconciliationService
import riven.core.service.entity.type.EntityTypeService
import java.util.*

@RestController
@RequestMapping("/api/v1/workspace")
@Tag(name = "workspace")
class WorkspaceSchemaController(
    private val entityTypeService: EntityTypeService,
    private val schemaReconciliationService: SchemaReconciliationService,
) {

    @GetMapping("/{workspaceId}/schema-health")
    @Operation(
        summary = "Get schema health status for workspace entity types",
        description = "Returns per-entity-type schema drift status, pending changes, and a summary. " +
            "Does NOT trigger reconciliation — read-only inspection."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Schema health retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
    )
    fun getSchemaHealth(
        @PathVariable workspaceId: UUID,
    ): ResponseEntity<SchemaHealthResponse> {
        val entityTypes = entityTypeService.getWorkspaceEntityTypeEntities(workspaceId)
        val health = schemaReconciliationService.getSchemaHealth(workspaceId, entityTypes)
        return ResponseEntity.ok(health)
    }

    @PostMapping("/{workspaceId}/schema-reconcile")
    @Operation(
        summary = "Apply pending schema changes for workspace entity types",
        description = "Applies breaking schema changes after user confirmation. " +
            "If impactConfirmed is false, returns an impact analysis. " +
            "If true, applies all changes including data deletion for removed fields."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Reconciliation completed or impact returned"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
    )
    fun reconcileSchema(
        @PathVariable workspaceId: UUID,
        @Valid @RequestBody request: SchemaReconcileRequest,
    ): ResponseEntity<Any> {
        val result = schemaReconciliationService.applyBreakingChanges(
            workspaceId = workspaceId,
            entityTypeIds = request.entityTypeIds,
            impactConfirmed = request.impactConfirmed,
        )

        return when (result) {
            is ReconciliationImpact -> ResponseEntity.ok(result)
            else -> ResponseEntity.ok(result)
        }
    }
}
