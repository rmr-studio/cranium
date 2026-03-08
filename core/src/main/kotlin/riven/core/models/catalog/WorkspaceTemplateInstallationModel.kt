package riven.core.models.catalog

import java.time.ZonedDateTime
import java.util.*

data class WorkspaceTemplateInstallationModel(
    val id: UUID?,
    val workspaceId: UUID,
    val manifestKey: String,
    val installedBy: UUID,
    val installedAt: ZonedDateTime,
    val attributeMappings: Map<String, Any>,
)
