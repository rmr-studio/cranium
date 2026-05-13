package cranium.core.models.request.user

import cranium.core.enums.user.AcquisitionChannel
import java.time.ZonedDateTime
import java.util.*

data class SaveUserRequest(
    val name: String,
    val email: String,
    val phone: String? = null,
    val defaultWorkspaceId: UUID? = null,
    val onboardingCompletedAt: ZonedDateTime? = null,
    val removeAvatar: Boolean = false,
    val acquisitionChannels: List<AcquisitionChannel>? = null,
)
