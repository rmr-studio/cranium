package cranium.core.models.request.identity

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request body for renaming an identity cluster.
 *
 * @param name The new display name for the cluster.
 */
data class RenameClusterRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
)
