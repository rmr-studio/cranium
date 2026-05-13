package cranium.core.controller.entity

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import cranium.core.models.request.entity.EntityQueryRequest
import cranium.core.models.response.entity.EntityQueryResponse
import cranium.core.service.entity.query.EntityQueryFacadeService
import java.util.*

@RestController
@RequestMapping("/api/v1/entity")
@Tag(name = "entity")
class EntityQueryController(
    private val entityQueryFacadeService: EntityQueryFacadeService,
) {

    @PostMapping("/workspace/{workspaceId}/type/{entityTypeId}/query")
    @Operation(summary = "Query entities with filtering, pagination, and sorting")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Query executed successfully"),
            ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameters"),
            ApiResponse(responseCode = "403", description = "Access denied to workspace"),
            ApiResponse(responseCode = "404", description = "Entity type not found"),
        ]
    )
    fun queryEntities(
        @PathVariable workspaceId: UUID,
        @PathVariable entityTypeId: UUID,
        @Valid @RequestBody request: EntityQueryRequest,
    ): ResponseEntity<EntityQueryResponse> {
        val response = entityQueryFacadeService.queryEntities(workspaceId, entityTypeId, request)
        return ResponseEntity.ok(response)
    }
}
