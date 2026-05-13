package cranium.core.repository.activity

import cranium.core.entity.activity.ActivityLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface ActivityLogRepository : JpaRepository<ActivityLogEntity, UUID>