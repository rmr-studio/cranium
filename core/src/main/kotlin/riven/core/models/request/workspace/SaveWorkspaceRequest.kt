package cranium.core.models.request.workspace

import cranium.core.enums.workspace.WorkspacePlan
import java.util.*

data class SaveWorkspaceRequest(
    val id: UUID? = null,
    val name: String,
    val plan: WorkspacePlan,
    val defaultCurrency: String, // Default currency for the workspace, can be a string representation of the currency code
    val isDefault: Boolean = false,
    val removeAvatar: Boolean = false,
)