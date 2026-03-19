package riven.core.controller.identity

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import riven.core.models.identity.IdentityCluster
import riven.core.models.request.identity.AddClusterMemberRequest
import riven.core.models.request.identity.RenameClusterRequest
import riven.core.models.response.identity.ClusterDetailResponse
import riven.core.models.response.identity.ClusterSummaryResponse
import riven.core.models.response.identity.PendingMatchCountResponse
import riven.core.models.response.identity.SuggestionResponse
import riven.core.service.identity.IdentityClusterService
import riven.core.service.identity.IdentityConfirmationService
import riven.core.service.identity.IdentityReadService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/identity/{workspaceId}")
@Tag(name = "identity")
class IdentityController(
    private val identityReadService: IdentityReadService,
    private val identityConfirmationService: IdentityConfirmationService,
    private val identityClusterService: IdentityClusterService,
) {

    // ------ Suggestion endpoints ------

    @GetMapping("/suggestions")
    @Operation(summary = "List identity match suggestions")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Suggestions retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
    )
    fun listSuggestions(
        @PathVariable workspaceId: UUID,
    ): ResponseEntity<List<SuggestionResponse>> {
        return ResponseEntity.ok(identityReadService.listSuggestions(workspaceId))
    }

    @GetMapping("/suggestions/{suggestionId}")
    @Operation(summary = "Get suggestion detail with signal breakdown")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Suggestion retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "404", description = "Suggestion not found"),
    )
    fun getSuggestion(
        @PathVariable workspaceId: UUID,
        @PathVariable suggestionId: UUID,
    ): ResponseEntity<SuggestionResponse> {
        return ResponseEntity.ok(identityReadService.getSuggestion(workspaceId, suggestionId))
    }

    @PostMapping("/suggestions/{suggestionId}/confirm")
    @Operation(summary = "Confirm an identity match suggestion")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Suggestion confirmed successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "404", description = "Suggestion not found"),
        ApiResponse(responseCode = "409", description = "Suggestion already resolved"),
    )
    fun confirmSuggestion(
        @PathVariable workspaceId: UUID,
        @PathVariable suggestionId: UUID,
    ): ResponseEntity<SuggestionResponse> {
        val result = identityConfirmationService.confirmSuggestion(workspaceId, suggestionId)
        return ResponseEntity.ok(SuggestionResponse.from(result))
    }

    @PostMapping("/suggestions/{suggestionId}/reject")
    @Operation(summary = "Reject an identity match suggestion")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Suggestion rejected successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "404", description = "Suggestion not found"),
        ApiResponse(responseCode = "409", description = "Suggestion already resolved"),
    )
    fun rejectSuggestion(
        @PathVariable workspaceId: UUID,
        @PathVariable suggestionId: UUID,
    ): ResponseEntity<SuggestionResponse> {
        val result = identityConfirmationService.rejectSuggestion(workspaceId, suggestionId)
        return ResponseEntity.ok(SuggestionResponse.from(result))
    }

    // ------ Cluster endpoints ------

    @GetMapping("/clusters")
    @Operation(summary = "List identity clusters")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Clusters retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
    )
    fun listClusters(
        @PathVariable workspaceId: UUID,
    ): ResponseEntity<List<ClusterSummaryResponse>> {
        return ResponseEntity.ok(identityReadService.listClusters(workspaceId))
    }

    @GetMapping("/clusters/{clusterId}")
    @Operation(summary = "Get cluster detail with member entities")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Cluster retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "404", description = "Cluster not found"),
    )
    fun getClusterDetail(
        @PathVariable workspaceId: UUID,
        @PathVariable clusterId: UUID,
    ): ResponseEntity<ClusterDetailResponse> {
        return ResponseEntity.ok(identityReadService.getClusterDetail(workspaceId, clusterId))
    }

    @PostMapping("/clusters/{clusterId}/members")
    @Operation(summary = "Manually add an entity to a cluster")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Entity added to cluster successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "404", description = "Cluster or entity not found"),
        ApiResponse(responseCode = "409", description = "Entity already in a cluster"),
    )
    fun addEntityToCluster(
        @PathVariable workspaceId: UUID,
        @PathVariable clusterId: UUID,
        @Valid @RequestBody request: AddClusterMemberRequest,
    ): ResponseEntity<ClusterDetailResponse> {
        return ResponseEntity.ok(identityClusterService.addEntityToCluster(workspaceId, clusterId, request))
    }

    @PatchMapping("/clusters/{clusterId}")
    @Operation(summary = "Rename an identity cluster")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Cluster renamed successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "404", description = "Cluster not found"),
    )
    fun renameCluster(
        @PathVariable workspaceId: UUID,
        @PathVariable clusterId: UUID,
        @Valid @RequestBody request: RenameClusterRequest,
    ): ResponseEntity<IdentityCluster> {
        return ResponseEntity.ok(identityClusterService.renameCluster(workspaceId, clusterId, request))
    }

    // ------ Entity match count endpoint ------

    @GetMapping("/entities/{entityId}/matches")
    @Operation(summary = "Get pending match count for an entity")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Pending match count retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
    )
    fun getPendingMatchCount(
        @PathVariable workspaceId: UUID,
        @PathVariable entityId: UUID,
    ): ResponseEntity<PendingMatchCountResponse> {
        return ResponseEntity.ok(identityReadService.getPendingMatchCount(workspaceId, entityId))
    }
}
