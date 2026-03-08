package riven.core.models.request.catalog

import jakarta.validation.constraints.NotBlank

data class InstallTemplateRequest(
    @field:NotBlank
    val templateKey: String,
)
