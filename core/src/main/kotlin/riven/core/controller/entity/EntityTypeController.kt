package riven.core.controller.entity

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import riven.core.models.entity.EntityType
import riven.core.models.request.entity.type.CreateEntityTypeRequest
import riven.core.models.request.entity.type.DeleteTypeDefinitionRequest
import riven.core.models.request.entity.type.SaveTypeDefinitionRequest
import riven.core.models.request.entity.type.UpdateEntityTypeConfigurationRequest
import riven.core.models.response.entity.type.EntityTypeImpactResponse
import riven.core.service.entity.type.EntityTypeService
import java.util.*

@RestController
@RequestMapping("/api/v1/entity/schema")
@Tag(name = "entity")
class EntityTypeController(
    private val entityTypeService: EntityTypeService,
) {

    @GetMapping("workspace/{workspaceId}")
    @Operation(
        summary = "Get all entity types for a workspace",
        description = "Retrieves all entity types associated with the specified workspace, " +
            "including relationship definitions and semantic metadata bundles."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Entity types retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Workspace not found")
    )
    fun getEntityTypesForWorkspace(
        @PathVariable workspaceId: UUID,
    ): ResponseEntity<List<EntityType>> {
        return ResponseEntity.ok(entityTypeService.getWorkspaceEntityTypesWithIncludes(workspaceId))
    }

    @GetMapping("/workspace/{workspaceId}/key/{key}")
    @Operation(
        summary = "Get an entity type by key for a workspace",
        description = "Retrieves a specific entity type by its key associated with the specified workspace, " +
            "including relationship definitions and semantic metadata bundle."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Entity type retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type not found")
    )
    fun getEntityTypeByKeyForWorkspace(
        @PathVariable workspaceId: UUID,
        @PathVariable key: String,
    ): ResponseEntity<EntityType> {
        return ResponseEntity.ok(entityTypeService.getEntityTypeByKeyWithIncludes(workspaceId, key))
    }

    @PostMapping("/workspace/{workspaceId}")
    @Operation(
        summary = "Create a new entity type",
        description = "Creates and publishes a new entity type for the specified workspace."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Entity type created successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
        ApiResponse(responseCode = "401", description = "Unauthorized access")
    )
    fun createEntityType(
        @PathVariable workspaceId: UUID,
        @RequestBody request: CreateEntityTypeRequest
    ): ResponseEntity<EntityType> {
        val newEntityType = entityTypeService.publishEntityType(workspaceId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(newEntityType)
    }

    @PutMapping("/workspace/{workspaceId}/configuration")
    @Operation(
        summary = "Updates an existing entity type configuration",
        description = "Updates the data for an already existing entity type for the specified workspace."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Entity type updated successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
        ApiResponse(responseCode = "401", description = "Unauthorized access")
    )
    fun updateEntityType(
        @PathVariable workspaceId: UUID,
        @RequestBody request: UpdateEntityTypeConfigurationRequest,
    ): ResponseEntity<EntityType> {
        val response = entityTypeService.updateEntityTypeConfiguration(workspaceId, request)
        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @DeleteMapping("/workspace/{workspaceId}/key/{key}")
    @Operation(
        summary = "Delete an entity type by key",
        description = "Deletes the specified entity type by its key for the given workspace."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Entity type deleted successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type not found")
    )
    fun deleteEntityTypeByKey(
        @PathVariable workspaceId: UUID,
        @PathVariable key: String,
        @RequestParam impactConfirmed: Boolean = false,
    ): ResponseEntity<EntityTypeImpactResponse> {
        val response = entityTypeService.deleteEntityType(workspaceId, key, impactConfirmed)
        if (response.impact != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/workspace/{workspaceId}/definition")
    @Operation(
        summary = "Add or update an attribute or relationship",
        description = "Adds or updates an attribute or relationship in the specified entity type for the given workspace."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Entity type definition saved successfully"),
        ApiResponse(
            responseCode = "409",
            description = "Conflict due to cascading impacts on existing entities as a result of aforementioned changes"
        ),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
        ApiResponse(responseCode = "401", description = "Unauthorized access")
    )
    fun saveEntityTypeDefinition(
        @PathVariable workspaceId: UUID,
        @RequestBody request: SaveTypeDefinitionRequest,
        @RequestParam impactConfirmed: Boolean = false,
    ): ResponseEntity<EntityTypeImpactResponse> {
        val response = entityTypeService.saveEntityTypeDefinition(workspaceId, request, impactConfirmed)
        if (response.impact != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
        }
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/workspace/{workspaceId}/definition")
    @Operation(
        summary = "Removes an attribute or relationship from an entity type",
        description = "Removes an attribute or relationship from the specified entity type for the given workspace."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Entity type definition removed successfully"),
        ApiResponse(
            responseCode = "409",
            description = "Conflict due to cascading impacts on existing entities as a result of aforementioned changes"
        ),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
        ApiResponse(responseCode = "401", description = "Unauthorized access")
    )
    fun deleteEntityTypeDefinition(
        @PathVariable workspaceId: UUID,
        @RequestBody request: DeleteTypeDefinitionRequest,
        @RequestParam impactConfirmed: Boolean = false,
    ): ResponseEntity<EntityTypeImpactResponse> {
        val response = entityTypeService.removeEntityTypeDefinition(workspaceId, request, impactConfirmed)
        if (response.impact != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
        }
        return ResponseEntity.ok(response)
    }
}
