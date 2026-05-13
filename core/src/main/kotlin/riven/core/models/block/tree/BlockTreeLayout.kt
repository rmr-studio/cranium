package cranium.core.models.block.tree

import cranium.core.entity.util.AuditableModel
import cranium.core.models.block.layout.TreeLayout
import java.io.Serializable
import java.time.ZonedDateTime
import java.util.*

data class BlockTreeLayout(
    val id: UUID,
    val workspaceId: UUID,
    val layout: TreeLayout,
    val version: Int = 1,
    // Keep these hidden unless within an internal workspace context
    override var createdAt: ZonedDateTime? = null,
    override var updatedAt: ZonedDateTime? = null,
    override var createdBy: UUID? = null,
    override var updatedBy: UUID? = null,
) : Serializable, AuditableModel