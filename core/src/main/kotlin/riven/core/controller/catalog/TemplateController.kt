package riven.core.controller.catalog

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import riven.core.models.catalog.BundleDetail
import riven.core.models.catalog.ManifestSummary
import riven.core.models.request.catalog.InstallBundleRequest
import riven.core.models.request.catalog.InstallTemplateRequest
import riven.core.models.response.catalog.BundleInstallationResponse
import riven.core.models.response.catalog.TemplateInstallationResponse
import riven.core.service.catalog.ManifestCatalogService
import riven.core.service.catalog.TemplateInstallationService
import java.util.*

@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "templates")
class TemplateController(
    private val catalogService: ManifestCatalogService,
    private val installationService: TemplateInstallationService,
) {

    @GetMapping
    @Operation(
        summary = "List available templates",
        description = "Returns all templates available for installation. No workspace scoping -- catalog is global."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Templates retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
    )
    fun listTemplates(): ResponseEntity<List<ManifestSummary>> {
        return ResponseEntity.ok(catalogService.getAvailableTemplates())
    }

    @PostMapping("/{workspaceId}/install")
    @Operation(
        summary = "Install template into workspace",
        description = "Installs a template into the specified workspace, creating all entity types, " +
            "relationships, and semantic metadata defined in the template manifest. " +
            "Installation is atomic -- if any step fails, nothing is created."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Template installed successfully"),
        ApiResponse(responseCode = "403", description = "No access to workspace"),
        ApiResponse(responseCode = "404", description = "Template not found"),
    )
    fun installTemplate(
        @PathVariable workspaceId: UUID,
        @Valid @RequestBody request: InstallTemplateRequest,
    ): ResponseEntity<TemplateInstallationResponse> {
        return ResponseEntity.ok(installationService.installTemplate(workspaceId, request.templateKey))
    }

    @GetMapping("/bundles")
    @Operation(
        summary = "List available bundles",
        description = "Returns all bundles available for installation. Each bundle is a curated collection of templates."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Bundles retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
    )
    fun listBundles(): ResponseEntity<List<BundleDetail>> =
        ResponseEntity.ok(catalogService.getAvailableBundles())

    @PostMapping("/{workspaceId}/install-bundle")
    @Operation(
        summary = "Install bundle into workspace",
        description = "Installs all templates in a bundle into the specified workspace. " +
            "Templates already installed are skipped. Installation is atomic."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Bundle installed successfully"),
        ApiResponse(responseCode = "403", description = "No access to workspace"),
        ApiResponse(responseCode = "404", description = "Bundle not found"),
    )
    fun installBundle(
        @PathVariable workspaceId: UUID,
        @Valid @RequestBody request: InstallBundleRequest,
    ): ResponseEntity<BundleInstallationResponse> =
        ResponseEntity.ok(installationService.installBundle(workspaceId, request.bundleKey))
}
