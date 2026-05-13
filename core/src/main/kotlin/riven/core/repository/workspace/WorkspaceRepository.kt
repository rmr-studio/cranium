package cranium.core.repository.workspace

import org.springframework.data.jpa.repository.JpaRepository
import cranium.core.entity.workspace.WorkspaceEntity
import java.util.*

interface WorkspaceRepository : JpaRepository<WorkspaceEntity, UUID>