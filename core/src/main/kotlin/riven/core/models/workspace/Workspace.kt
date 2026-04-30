package riven.core.models.workspace

import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import riven.core.entity.util.AuditableModel
import riven.core.enums.workspace.WorkspaceDisplay
import riven.core.enums.workspace.WorkspacePlan
import java.time.ZonedDateTime
import java.util.*

@Schema(requiredProperties = ["id", "name"])
@JsonDeserialize(using = ValueDeserializer.None::class)
data class Workspace(

    val id: UUID,
    var name: String,
    val plan: WorkspacePlan,
    var defaultCurrency: Currency = Currency.getInstance("AUD"), // Default currency for the workspace
    var avatarUrl: String? = null,
    val memberCount: Int,
    val connotationEnabled: Boolean = false,

    override var createdAt: ZonedDateTime? = null,
    override var updatedAt: ZonedDateTime? = null,
    override var createdBy: UUID? = null,
    override var updatedBy: UUID? = null,
) : AuditableModel {

    fun toDisplay(): WorkspaceDisplay =
        WorkspaceDisplay(
            id = requireNotNull(this.id) { "Workspace must have a non-null id" },
            name = this.name,
            avatarUrl = this.avatarUrl
        )
}
