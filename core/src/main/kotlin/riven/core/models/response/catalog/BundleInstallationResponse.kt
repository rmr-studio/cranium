package riven.core.models.response.catalog

data class BundleInstallationResponse(
    val bundleKey: String,
    val bundleName: String,
    val templatesInstalled: List<String>,
    val templatesSkipped: List<String>,
    val entityTypesCreated: Int,
    val relationshipsCreated: Int,
    val entityTypes: List<CreatedEntityTypeSummary>,
)
