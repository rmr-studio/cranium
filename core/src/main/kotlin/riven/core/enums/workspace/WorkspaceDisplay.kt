package cranium.core.enums.workspace

import java.util.*

data class WorkspaceDisplay(
    val id: UUID,
    val name: String,
    val avatarUrl: String? = null
)