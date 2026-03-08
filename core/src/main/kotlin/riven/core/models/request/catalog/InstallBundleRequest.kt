package riven.core.models.request.catalog

import jakarta.validation.constraints.NotBlank

data class InstallBundleRequest(
    @field:NotBlank
    val bundleKey: String,
)
