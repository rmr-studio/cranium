package riven.core.entity.workspace

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.workspace.WorkspacePlan
import riven.core.models.workspace.Workspace
import java.util.*

@Entity
@Table(
    name = "workspaces",
    uniqueConstraints = [
        UniqueConstraint(name = "workspace_name_unique", columnNames = ["name"])
    ]
)
@SQLRestriction("deleted = false")
data class WorkspaceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID DEFAULT uuid_generate_v4()", nullable = false)
    val id: UUID? = null,

    @Column(name = "name", nullable = false, updatable = true)
    var name: String,

    @Column(name = "default_currency", nullable = false, updatable = true)
    var defaultCurrency: Currency = Currency.getInstance("AUD"), // Default currency for the workspace

    @Column(name = "avatarUrl", nullable = true, updatable = true)
    var avatarUrl: String? = null,

    @Column(name = "member_count", nullable = false, updatable = false)
    val memberCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    var plan: WorkspacePlan = WorkspacePlan.FREE, // Default plan is FREE

    ) : AuditableSoftDeletableEntity() {
    fun toModel(audit: Boolean = true): Workspace {
        val id = requireNotNull(this.id) { "WorkspaceEntity must have a non-null id" }
        return Workspace(
            id = id,
            name = this.name,
            plan = this.plan,
            defaultCurrency = this.defaultCurrency,
            avatarUrl = this.avatarUrl,
            memberCount = this.memberCount
        ).also {
            if (audit) {
                it.createdAt = this.createdAt
                it.updatedAt = this.updatedAt
                it.createdBy = this.createdBy
                it.updatedBy = this.updatedBy
            }
        }
    }

}

