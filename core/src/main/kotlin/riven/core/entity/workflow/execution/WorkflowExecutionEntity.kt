package cranium.core.entity.workflow.execution

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.enums.workflow.WorkflowTriggerType
import cranium.core.models.common.json.JsonValue
import cranium.core.models.workflow.engine.execution.WorkflowExecutionRecord
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

@Entity
@Table(name = "workflow_executions")
data class WorkflowExecutionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "workflow_definition_id", nullable = false, columnDefinition = "uuid")
    val workflowDefinitionId: UUID,
    @Column(name = "workflow_definition_version_id", nullable = false, columnDefinition = "uuid")
    val workflowVersionId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: WorkflowStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    val triggerType: WorkflowTriggerType,


    @Column(name = "started_at", nullable = false, columnDefinition = "timestamptz")
    val startedAt: ZonedDateTime,
    @Column(name = "completed_at", columnDefinition = "timestamptz")
    val completedAt: ZonedDateTime?,
    @Column(name = "duration_ms", nullable = false, columnDefinition = "bigint")
    val durationMs: Long,

    @Type(JsonBinaryType::class)
    @Column(name = "error", columnDefinition = "jsonb")
    val error: JsonValue,

    @Type(JsonBinaryType::class)
    @Column(name = "input", columnDefinition = "jsonb")
    val input: JsonValue,

    @Type(JsonBinaryType::class)
    @Column(name = "output", columnDefinition = "jsonb")
    val output: JsonValue
) {
    fun toModel(): WorkflowExecutionRecord {
        val id: UUID = requireNotNull(this.id)
        return WorkflowExecutionRecord(
            id = id,
            workspaceId = this.workspaceId,
            workflowDefinitionId = this.workflowDefinitionId,
            workflowVersionId = this.workflowVersionId,
            status = this.status,
            startedAt = this.startedAt,
            completedAt = this.completedAt,
            duration = Duration.ofMillis(this.durationMs),
            input = this.input,
            output = this.output,
            error = this.error,
            triggerType = this.triggerType
        )
    }
}