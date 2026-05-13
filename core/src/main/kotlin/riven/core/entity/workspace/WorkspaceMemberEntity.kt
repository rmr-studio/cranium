package cranium.core.entity.workspace

import jakarta.persistence.*
import cranium.core.entity.user.UserEntity
import cranium.core.entity.user.toDisplay
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.models.workspace.WorkspaceMember
import java.time.ZonedDateTime
import java.util.*

@Entity
@Table(
    name = "workspace_members",
    uniqueConstraints = [UniqueConstraint(
        name = "uq_workspace_user",
        columnNames = ["workspace_id", "user_id"]
    )]
)
data class WorkspaceMemberEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID DEFAULT uuid_generate_v4()", nullable = false)
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = true)
    var role: WorkspaceRoles,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "member_since", nullable = false, updatable = false)
    val memberSince: ZonedDateTime = ZonedDateTime.now(),
) {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    var user: UserEntity? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "workspace_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false
    )
    var workspace: WorkspaceEntity? = null

    fun toModel(): WorkspaceMember {
        this.user.let { user ->
            if (user == null) {
                throw IllegalArgumentException("WorkspaceMemberEntity must have a non-null user")
            }

            if (user.id == null) {
                throw IllegalArgumentException("UserEntity must have a non-null id")
            }

            this.workspace.let { workspace ->
                if (workspace == null) {
                    throw IllegalArgumentException("WorkspaceMemberEntity must have a non-null workspace")
                }

                return WorkspaceMember(
                    user = user.toDisplay(),
                    workspace = workspace.toModel(audit = false).toDisplay(),
                    role = this.role,
                    memberSince = this.memberSince,
                )
            }
        }
    }


}

