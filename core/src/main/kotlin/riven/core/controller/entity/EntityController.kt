package riven.core.controller.entity

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import riven.core.models.entity.Entity
import riven.core.models.request.entity.AddRelationshipRequest
import riven.core.models.request.entity.DeleteEntityRequest
import riven.core.models.request.entity.SaveEntityRequest
import riven.core.models.request.entity.UpdateRelationshipRequest
import riven.core.models.response.entity.RelationshipResponse
import riven.core.models.response.entity.DeleteEntityResponse
import riven.core.models.response.entity.SaveEntityResponse
import riven.core.service.entity.EntityRelationshipService
import riven.core.service.entity.EntityService
import java.util.*

@RestController
@RequestMapping("/api/v1/entity")
@Tag(name = "entity")
class EntityController(
    private val entityService: EntityService,
    private val entityRelationshipService: EntityRelationshipService,
) {

    @GetMapping("workspace/{workspaceId}")
    @Operation(
        summary = "Get all entity types for an workspace for all provided type keys",
        description = "Retrieves all entity associated with the specified workspace and specified entity types." +
                "This will also fetch all relevant linked entities."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Entity types retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Workspace not found")
    )
    fun getEntityByTypeIdInForWorkspace(
        @PathVariable workspaceId: UUID,
        @RequestParam ids: List<UUID>
    ): ResponseEntity<Map<UUID, List<Entity>>> {
        val response = entityService.getEntitiesByTypeIds(
            workspaceId = workspaceId,
            typeIds = ids
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("workspace/{workspaceId}/type/{id}")
    @Operation(
        summary = "Get all entity types for an workspace for a provided entity type",
        description = "Retrieves all entity associated with the specified workspace and specified entity type." +
                "This will also fetch all relevant linked entities."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Entity types retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Workspace not found")
    )
    fun getEntityByTypeIdForWorkspace(
        @PathVariable workspaceId: UUID,
        @PathVariable id: UUID
    ): ResponseEntity<List<Entity>> {
        val response = entityService.getEntitiesByTypeId(
            workspaceId = workspaceId,
            typeId = id
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/workspace/{workspaceId}/type/{entityTypeId}")
    @Operation(
        summary = "Saves an entity instance",
        description = "Saves either a new entity, or an updated instance within the specified workspace."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Entity instance saved successfully", content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SaveEntityResponse::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "400", description = "Invalid entity data provided", content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SaveEntityResponse::class)
                )
            ]
        ),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Workspace or entity type not found"),
        ApiResponse(
            responseCode = "409", description = "Conflict of data or unconfirmed impacts", content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SaveEntityResponse::class)
                )
            ]
        )
    )
    fun saveEntity(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
        @RequestBody request: SaveEntityRequest,
    ): ResponseEntity<SaveEntityResponse> {
        val response = entityService.saveEntity(workspaceId, entityTypeId, request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/workspace/{workspaceId}/delete")
    @Operation(summary = "Bulk deletes entities by ID selection or filter-based selection")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Entities deleted successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "No entities found matching selection criteria"),
        ]
    )
    fun deleteEntities(
        @PathVariable workspaceId: UUID,
        @Valid @RequestBody request: DeleteEntityRequest,
    ): ResponseEntity<DeleteEntityResponse> {
        val response = entityService.deleteEntities(workspaceId, request)
        return ResponseEntity.ok(response)
    }

    // ------ Relationships ------

    @PostMapping("/workspace/{workspaceId}/entities/{entityId}/relationships")
    @Operation(summary = "Add a relationship between two entities")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Relationship created successfully"),
        ApiResponse(responseCode = "404", description = "Source or target entity not found"),
        ApiResponse(responseCode = "409", description = "Relationship already exists"),
    )
    fun addRelationship(
        @PathVariable workspaceId: UUID,
        @PathVariable entityId: UUID,
        @RequestBody request: AddRelationshipRequest,
    ): ResponseEntity<RelationshipResponse> {
        val response = entityRelationshipService.addRelationship(workspaceId, entityId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/workspace/{workspaceId}/entities/{entityId}/relationships")
    @Operation(summary = "Get all relationships for an entity")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Relationships retrieved successfully"),
        ApiResponse(responseCode = "404", description = "Entity not found"),
    )
    fun getRelationships(
        @PathVariable workspaceId: UUID,
        @PathVariable entityId: UUID,
        @RequestParam(required = false) definitionId: UUID?,
    ): ResponseEntity<List<RelationshipResponse>> {
        val response = entityRelationshipService.getRelationships(workspaceId, entityId, definitionId)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/workspace/{workspaceId}/relationships/{relationshipId}")
    @Operation(summary = "Update a relationship's semantic context")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Relationship updated successfully"),
        ApiResponse(responseCode = "404", description = "Relationship not found"),
    )
    fun updateRelationship(
        @PathVariable workspaceId: UUID,
        @PathVariable relationshipId: UUID,
        @RequestBody request: UpdateRelationshipRequest,
    ): ResponseEntity<RelationshipResponse> {
        val response = entityRelationshipService.updateRelationship(workspaceId, relationshipId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/workspace/{workspaceId}/relationships/{relationshipId}")
    @Operation(summary = "Delete a relationship")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Relationship deleted successfully"),
        ApiResponse(responseCode = "404", description = "Relationship not found"),
    )
    fun removeRelationship(
        @PathVariable workspaceId: UUID,
        @PathVariable relationshipId: UUID,
    ): ResponseEntity<Void> {
        entityRelationshipService.removeRelationship(workspaceId, relationshipId)
        return ResponseEntity.noContent().build()
    }
}