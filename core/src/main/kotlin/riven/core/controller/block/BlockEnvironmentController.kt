package cranium.core.controller.block

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.models.block.BlockEnvironment
import cranium.core.models.request.block.HydrateBlocksRequest
import cranium.core.models.request.block.OverwriteEnvironmentRequest
import cranium.core.models.request.block.SaveEnvironmentRequest
import cranium.core.models.response.block.OverwriteEnvironmentResponse
import cranium.core.models.response.block.SaveEnvironmentResponse
import cranium.core.models.response.block.internal.BlockHydrationResult
import cranium.core.service.block.BlockEnvironmentService
import java.util.*

@RestController
@RequestMapping("/api/v1/block/environment")
@Tag(name = "block")
class BlockEnvironmentController(
    private val environmentService: BlockEnvironmentService,
) {
    @PostMapping("/")
    @Operation(
        summary = "Save Block Environment",
        description = "Saves the block environment including layout and structural operations."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Environment saved successfully"),
        ApiResponse(responseCode = "409", description = "Conflict in versioning when saving environment"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "400", description = "Invalid request data")
    )
    fun saveBlockEnvironment(@Valid @RequestBody request: SaveEnvironmentRequest): ResponseEntity<SaveEnvironmentResponse> {
        val response = environmentService.saveBlockEnvironment(request)
        if (response.conflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
        }

        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @PostMapping("/overwrite")
    @Operation(
        summary = "Overwrite Block Environment",
        description = "Overwrites the entire block environment with the provided data."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Environment overwritten successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "400", description = "Invalid request data")
    )
    fun overwriteBlockEnvironment(@Valid @RequestBody request: OverwriteEnvironmentRequest): ResponseEntity<OverwriteEnvironmentResponse> {
        val response = environmentService.overwriteBlockEnvironment(request)
        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @GetMapping("workspace/{workspaceId}/type/{type}/id/{entityId}")
    @Operation(
        summary = "Get Block Environment",
        description = "Retrieves the block environment for the specified entity. Creates a default layout if none exists (lazy initialization)."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Environment retrieved successfully"),
        ApiResponse(responseCode = "404", description = "Entity not found"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    )
    fun getBlockEnvironment(
        @PathVariable workspaceId: UUID,
        @PathVariable type: ApplicationEntityType,
        @PathVariable entityId: UUID,
    ): ResponseEntity<BlockEnvironment> {
        val environment = environmentService.loadBlockEnvironment(entityId, type, workspaceId)
        return ResponseEntity.ok(environment)
    }

    @PostMapping("/hydrate")
    @Operation(
        summary = "Hydrate Blocks",
        description = "Resolves entity references for one or more blocks in a single batched request. " +
                "This is used for progressive loading of entity data without fetching everything upfront. " +
                "Only blocks with entity reference metadata will be hydrated; other blocks are skipped."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Blocks hydrated successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    )
    fun hydrateBlocks(@Valid @RequestBody request: HydrateBlocksRequest): ResponseEntity<Map<UUID, BlockHydrationResult>> {
        val results = environmentService.hydrateEnvironment(request)
        return ResponseEntity.ok(results)
    }
}