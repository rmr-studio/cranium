package cranium.core.entity.workspace

import jakarta.persistence.*
import cranium.core.entity.user.UserEntity
import cranium.core.enums.workspace.WorkspaceInviteStatus
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.models.workspace.WorkspaceInvite
import java.time.ZonedDateTime
import java.util.*

@Entity
@Table(
    name = "workspace_invites",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uc_workspace_invites_email",
            columnNames = ["workspace_id", "email", "invite_code"]
        )
    ],
    indexes = [
        Index(name = "idx_workspace_invites_email", columnList = "email")
    ]
)
data class WorkspaceInviteEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID DEFAULT uuid_generate_v4()", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "workspace_id", columnDefinition = "UUID", nullable = false)
    val workspaceId: UUID,

    @Column(name = "email")
    val email: String,

    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    val role: WorkspaceRoles = WorkspaceRoles.MEMBER,

    @Column(name = "invite_code", length = 12, nullable = false)
    val token: String = this.generateSecureToken(),

    @Column(name = "invited_by", nullable = false, columnDefinition = "UUID")
    val invitedBy: UUID,

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    var inviteStatus: WorkspaceInviteStatus = WorkspaceInviteStatus.PENDING,

    @Column(name = "expires_at")
    val expiresAt: ZonedDateTime = ZonedDateTime.now().plusDays(1),

    @Column(name = "created_at", updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now()
) {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", referencedColumnName = "id", insertable = false, updatable = false)
    var workspace: WorkspaceEntity? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", referencedColumnName = "id", insertable = false, updatable = false)
    var invitedByUser: UserEntity? = null


    companion object Factory {
        fun generateSecureToken(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            return (1..12)
                .map { chars.random() }
                .joinToString("")
        }
    }
}

fun WorkspaceInviteEntity.toModel(): WorkspaceInvite {
    this.id.let {
        if (it == null) {
            throw IllegalArgumentException("WorkspaceInviteEntity must have a non-null id")
        }
        return WorkspaceInvite(
            id = it,
            workspaceId = this.workspaceId,
            email = this.email,
            inviteToken = this.token,
            invitedBy = this.invitedBy,
            createdAt = this.createdAt,
            expiresAt = this.expiresAt,
            role = this.role,
            status = this.inviteStatus
        )
    }
}