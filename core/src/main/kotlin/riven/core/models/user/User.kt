package cranium.core.models.user

import cranium.core.enums.user.AcquisitionChannel
import cranium.core.models.workspace.Workspace
import cranium.core.models.workspace.WorkspaceMember
import java.time.ZonedDateTime
import java.util.*

data class User(
    val id: UUID,
    var email: String,
    var name: String,
    var phone: String? = null,
    var avatarUrl: String? = null,
    val memberships: List<WorkspaceMember> = listOf(),
    var defaultWorkspace: Workspace? = null,
    var onboardingCompletedAt: ZonedDateTime? = null,
    var acquisitionChannels: List<AcquisitionChannel>? = null,
) {
    /**
     * Convert User to UserDisplay (lightweight representation).
     */
    fun toDisplay(): UserDisplay = UserDisplay(
        id = this.id,
        email = this.email,
        name = this.name,
        avatarUrl = this.avatarUrl
    )
}



