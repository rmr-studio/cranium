package cranium.core.entity.workflow

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import cranium.core.entity.util.AuditableSoftDeletableEntity
import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.workflow.WorkflowDefinitionStatus
import cranium.core.models.common.Icon
import cranium.core.models.workflow.WorkflowDefinition
import cranium.core.models.workflow.WorkflowGraph
import java.util.*

@Entity
@Table(
    name = "workflow_definitions",
    indexes = [
        Index(name = "idx_workflow_definitions_workspace_id", columnList = "workspace_id"),
    ]
)
@SQLRestriction("deleted = false")
data class WorkflowDefinitionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "name", nullable = false)
    val name: String,
    @Column(name = "description", columnDefinition = "text", nullable = true)
    val description: String? = null,

    @Column(name = "published_version", nullable = false, columnDefinition = "uuid")
    val versionNumber: Int,


    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: WorkflowDefinitionStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_colour", nullable = false)
    var iconColour: IconColour = IconColour.NEUTRAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    var iconType: IconType = IconType.FILE,

    @Type(JsonBinaryType::class)
    @Column(name = "tags", columnDefinition = "jsonb", nullable = false)
    val tags: List<String>,
) : AuditableSoftDeletableEntity() {
    fun toModel(
        workflow: WorkflowDefinitionVersionEntity,
        graph: WorkflowGraph? = null
    ): WorkflowDefinition {
        val id = requireNotNull(this.id)
        assert(this.versionNumber == workflow.versionNumber) {
            "Workflow definition version number mismatch: entity(${this.versionNumber}) vs workflow(${workflow.versionNumber})"
        }

        return WorkflowDefinition(
            id = id,
            workspaceId = this.workspaceId,
            name = this.name,
            description = this.description,
            definition = workflow.toModel(graph),
            status = this.status,
            icon = Icon(
                colour = this.iconColour,
                type = this.iconType
            ),
            tags = this.tags,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            updatedBy = this.updatedBy,
            createdBy = this.createdBy
        )
    }
}