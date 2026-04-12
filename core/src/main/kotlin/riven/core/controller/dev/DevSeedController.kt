package riven.core.controller.dev

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import riven.core.models.response.catalog.TemplateInstallationResponse
import riven.core.models.response.dev.DevSeedResponse
import riven.core.service.dev.DevSeedService
import java.util.*

@RestController
@RequestMapping("/api/v1/dev/seed")
@Tag(name = "dev")
@ConditionalOnProperty(name = ["riven.dev.seed.enabled"], havingValue = "true")
class DevSeedController(
    private val devSeedService: DevSeedService,
) {

    @PostMapping("workspace/{workspaceId}")
    @Operation(
        summary = "Seed workspace with mock entity data",
        description = "Populates the workspace with realistic mock entities and relationships based on its installed template. Idempotent — returns early if data already exists.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Workspace seeded successfully or already seeded"),
    )
    fun seedWorkspace(@PathVariable workspaceId: UUID): ResponseEntity<DevSeedResponse> {
        return ResponseEntity.ok(devSeedService.seedWorkspace(workspaceId))
    }

    @PostMapping("workspace/{workspaceId}/template/{templateKey}/reinstall")
    @Operation(
        summary = "Re-install a template into a workspace",
        description = "Dev-only. Removes the existing template installation record (if present) and re-runs installation for the provided template key. Existing workspace entity types with matching keys are reused; missing types are created along with their relationships and semantic metadata.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Template re-installed successfully"),
    )
    fun reinstallTemplate(
        @PathVariable workspaceId: UUID,
        @PathVariable templateKey: String,
    ): ResponseEntity<TemplateInstallationResponse> {
        return ResponseEntity.ok(devSeedService.reinstallTemplate(workspaceId, templateKey))
    }
}
