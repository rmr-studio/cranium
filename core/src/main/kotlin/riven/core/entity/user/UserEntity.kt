package riven.core.entity.user

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.entity.workspace.WorkspaceEntity
import riven.core.enums.user.AcquisitionChannel
import riven.core.models.user.User
import riven.core.models.user.UserDisplay
import riven.core.models.workspace.WorkspaceMember
import riven.core.util.AvatarUrlResolver
import java.time.ZonedDateTime
import java.util.*

@Entity
@Table(
    name = "users",
)
@SQLRestriction("deleted = false")
data class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    val id: UUID? = null,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "email", nullable = false)
    var email: String,

    @Column(name = "phone", nullable = true)
    var phone: String?,

    @Column(name = "avatar_url", nullable = true)
    var avatarUrl: String? = null,


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_workspace_id", referencedColumnName = "id", insertable = true, updatable = true)
    var defaultWorkspace: WorkspaceEntity? = null,

    @Column(name = "onboarding_completed_at", nullable = true)
    var onboardingCompletedAt: ZonedDateTime? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "acquisition_channels", columnDefinition = "jsonb")
    var acquisitionChannels: List<AcquisitionChannel>? = null,

    ) : AuditableSoftDeletableEntity() {


    fun toModel(memberships: List<WorkspaceMember> = emptyList()): User {
        this.id.let {
            if (it == null) {
                throw IllegalArgumentException("UserEntity id cannot be null")
            }
            return User(
                id = it,
                email = this.email,
                phone = this.phone,
                name = this.name,
                avatarUrl = AvatarUrlResolver.userAvatarUrl(it, this.avatarUrl),
                memberships = memberships,
                defaultWorkspace = this.defaultWorkspace?.toModel(),
                onboardingCompletedAt = this.onboardingCompletedAt,
                acquisitionChannels = this.acquisitionChannels,
            )
        }
    }
}

/**
 * Extension function to convert UserEntity to UserDisplay.
 */
fun UserEntity.toDisplay(): UserDisplay {
    val id = requireNotNull(this.id) { "UserEntity must have a non-null id" }
    return UserDisplay(
        id = id,
        email = this.email,
        name = this.name,
        avatarUrl = AvatarUrlResolver.userAvatarUrl(id, this.avatarUrl)
    )
}

