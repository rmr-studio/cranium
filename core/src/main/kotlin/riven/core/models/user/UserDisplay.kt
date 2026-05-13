package cranium.core.models.user

import java.util.*

data class UserDisplay(
    val id: UUID,
    var email: String,
    var name: String,
    var avatarUrl: String? = null
)