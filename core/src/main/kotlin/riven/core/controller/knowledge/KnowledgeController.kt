package riven.core.controller.knowledge

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionStatus
import riven.core.models.entity.EntityTypeSemanticMetadata
import riven.core.models.knowledge.WorkspaceBusinessDefinition
import riven.core.models.request.entity.type.BulkSaveSemanticMetadataRequest
import riven.core.models.request.entity.type.SaveSemanticMetadataRequest
import riven.core.models.request.knowledge.CreateBusinessDefinitionRequest
import riven.core.models.request.knowledge.UpdateBusinessDefinitionRequest
import riven.core.models.entity.SemanticMetadataBundle
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.knowledge.WorkspaceBusinessDefinitionService
import java.util.*

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "knowledge")
class KnowledgeController(
    private val semanticMetadataService: EntityTypeSemanticMetadataService,
    private val entityTypeService: riven.core.service.entity.type.EntityTypeService,
    private val businessDefinitionService: WorkspaceBusinessDefinitionService,
) {

    // ------ Entity type metadata ------

    @GetMapping("/workspace/{workspaceId}/entity-type/{entityTypeId}")
    @Operation(
        summary = "Get semantic metadata for an entity type",
        description = "Retrieves the semantic metadata record for the entity type itself (not its attributes or relationships)."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Semantic metadata retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type or metadata not found")
    )
    fun getEntityTypeMetadata(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
    ): ResponseEntity<EntityTypeSemanticMetadata> {
        val metadata = semanticMetadataService.getForEntityType(workspaceId, entityTypeId)
        return ResponseEntity.ok(metadata)
    }

    @PutMapping("/workspace/{workspaceId}/entity-type/{entityTypeId}")
    @Operation(
        summary = "Set semantic metadata for an entity type (full replacement)",
        description = "Upserts the semantic metadata for the entity type itself. All fields are fully replaced on every call."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Semantic metadata saved successfully"),
        ApiResponse(responseCode = "400", description = "Invalid classification value"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type not found")
    )
    fun setEntityTypeMetadata(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
        @RequestBody request: SaveSemanticMetadataRequest,
    ): ResponseEntity<EntityTypeSemanticMetadata> {
        val metadata = semanticMetadataService.upsertMetadata(
            workspaceId, entityTypeId, SemanticMetadataTargetType.ENTITY_TYPE, entityTypeId, request
        )
        return ResponseEntity.ok(metadata)
    }

    // ------ Attribute metadata ------

    @GetMapping("/workspace/{workspaceId}/entity-type/{entityTypeId}/attributes")
    @Operation(
        summary = "Get semantic metadata for all attributes of an entity type",
        description = "Retrieves all attribute-level semantic metadata records for the given entity type."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Attribute metadata retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type not found")
    )
    fun getAttributeMetadata(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
    ): ResponseEntity<List<EntityTypeSemanticMetadata>> {
        val metadata = semanticMetadataService.getAttributeMetadata(workspaceId, entityTypeId)
        return ResponseEntity.ok(metadata)
    }

    @PutMapping("/workspace/{workspaceId}/entity-type/{entityTypeId}/attribute/{attributeId}")
    @Operation(
        summary = "Set semantic metadata for a single attribute (full replacement)",
        description = "Upserts semantic metadata for a specific attribute. All fields are fully replaced on every call."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Attribute metadata saved successfully"),
        ApiResponse(responseCode = "400", description = "Invalid classification value"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type not found")
    )
    fun setAttributeMetadata(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
        @PathVariable attributeId: UUID,
        @RequestBody request: SaveSemanticMetadataRequest,
    ): ResponseEntity<EntityTypeSemanticMetadata> {
        val metadata = semanticMetadataService.upsertMetadata(
            workspaceId, entityTypeId, SemanticMetadataTargetType.ATTRIBUTE, attributeId, request
        )
        return ResponseEntity.ok(metadata)
    }

    @PutMapping("/workspace/{workspaceId}/entity-type/{entityTypeId}/attributes/bulk")
    @Operation(
        summary = "Set semantic metadata for multiple attributes (full replacement per attribute)",
        description = "Bulk upserts semantic metadata for multiple attributes of an entity type. All fields are fully replaced per attribute on every call."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Attribute metadata bulk saved successfully"),
        ApiResponse(responseCode = "400", description = "Invalid classification value"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type not found")
    )
    fun bulkSetAttributeMetadata(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
        @RequestBody requests: List<BulkSaveSemanticMetadataRequest>,
    ): ResponseEntity<List<EntityTypeSemanticMetadata>> {
        val metadata = semanticMetadataService.bulkUpsertAttributeMetadata(workspaceId, entityTypeId, requests)
        return ResponseEntity.ok(metadata)
    }

    // ------ Relationship metadata ------

    @GetMapping("/workspace/{workspaceId}/entity-type/{entityTypeId}/relationships")
    @Operation(
        summary = "Get semantic metadata for all relationships of an entity type",
        description = "Retrieves all relationship-level semantic metadata records for the given entity type."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Relationship metadata retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type not found")
    )
    fun getRelationshipMetadata(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
    ): ResponseEntity<List<EntityTypeSemanticMetadata>> {
        val metadata = semanticMetadataService.getRelationshipMetadata(workspaceId, entityTypeId)
        return ResponseEntity.ok(metadata)
    }

    @PutMapping("/workspace/{workspaceId}/entity-type/{entityTypeId}/relationship/{relationshipId}")
    @Operation(
        summary = "Set semantic metadata for a single relationship (full replacement)",
        description = "Upserts semantic metadata for a specific relationship definition. All fields are fully replaced on every call."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Relationship metadata saved successfully"),
        ApiResponse(responseCode = "400", description = "Invalid classification value"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type not found")
    )
    fun setRelationshipMetadata(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
        @PathVariable relationshipId: UUID,
        @RequestBody request: SaveSemanticMetadataRequest,
    ): ResponseEntity<EntityTypeSemanticMetadata> {
        val metadata = semanticMetadataService.upsertMetadata(
            workspaceId, entityTypeId, SemanticMetadataTargetType.RELATIONSHIP, relationshipId, request
        )
        return ResponseEntity.ok(metadata)
    }

    // ------ Full bundle ------

    @GetMapping("/workspace/{workspaceId}/entity-type/{entityTypeId}/all")
    @Operation(
        summary = "Get all semantic metadata for an entity type (entity type + attributes + relationships)",
        description = "Retrieves all semantic metadata records for the entity type itself, its attributes, and its relationships, grouped into a single bundle."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Metadata bundle retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Entity type not found")
    )
    fun getAllMetadata(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
    ): ResponseEntity<SemanticMetadataBundle> {
        val allMetadata = semanticMetadataService.getAllMetadataForEntityType(workspaceId, entityTypeId)
        val bundle = entityTypeService.buildSemanticBundle(entityTypeId, allMetadata)
        return ResponseEntity.ok(bundle)
    }

    // ------ Business definitions ------

    @GetMapping("/workspace/{workspaceId}/definitions")
    @Operation(
        summary = "List all business definitions for a workspace",
        description = "Retrieves all active business definitions, optionally filtered by status and/or category."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Definitions retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
    )
    fun listDefinitions(
        @PathVariable workspaceId: UUID,
        @RequestParam(required = false) status: DefinitionStatus?,
        @RequestParam(required = false) category: DefinitionCategory?,
    ): ResponseEntity<List<WorkspaceBusinessDefinition>> {
        val definitions = businessDefinitionService.listDefinitions(workspaceId, status, category)
        return ResponseEntity.ok(definitions)
    }

    @GetMapping("/workspace/{workspaceId}/definitions/{id}")
    @Operation(
        summary = "Get a single business definition by ID",
        description = "Retrieves a specific business definition within the workspace."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Definition retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Definition not found"),
    )
    fun getDefinition(
        @PathVariable workspaceId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<WorkspaceBusinessDefinition> {
        val definition = businessDefinitionService.getDefinition(workspaceId, id)
        return ResponseEntity.ok(definition)
    }

    @PostMapping("/workspace/{workspaceId}/definitions")
    @Operation(
        summary = "Create a new business definition",
        description = "Creates a workspace-scoped business definition. Requires workspace ADMIN role."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Definition created successfully"),
        ApiResponse(responseCode = "400", description = "Invalid input"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        ApiResponse(responseCode = "409", description = "Duplicate term in workspace"),
    )
    fun createDefinition(
        @PathVariable workspaceId: UUID,
        @RequestBody request: CreateBusinessDefinitionRequest,
    ): ResponseEntity<WorkspaceBusinessDefinition> {
        val definition = businessDefinitionService.createDefinition(workspaceId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(definition)
    }

    @PutMapping("/workspace/{workspaceId}/definitions/{id}")
    @Operation(
        summary = "Update a business definition",
        description = "Full replacement update with optimistic locking. Requires workspace ADMIN role."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Definition updated successfully"),
        ApiResponse(responseCode = "400", description = "Invalid input"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        ApiResponse(responseCode = "404", description = "Definition not found"),
        ApiResponse(responseCode = "409", description = "Version conflict or duplicate term"),
    )
    fun updateDefinition(
        @PathVariable workspaceId: UUID,
        @PathVariable id: UUID,
        @RequestBody request: UpdateBusinessDefinitionRequest,
    ): ResponseEntity<WorkspaceBusinessDefinition> {
        val definition = businessDefinitionService.updateDefinition(workspaceId, id, request)
        return ResponseEntity.ok(definition)
    }

    @DeleteMapping("/workspace/{workspaceId}/definitions/{id}")
    @Operation(
        summary = "Soft-delete a business definition",
        description = "Marks a business definition as deleted. Requires workspace ADMIN role."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Definition deleted successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        ApiResponse(responseCode = "404", description = "Definition not found"),
    )
    fun deleteDefinition(
        @PathVariable workspaceId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        businessDefinitionService.deleteDefinition(workspaceId, id)
        return ResponseEntity.noContent().build()
    }

}
