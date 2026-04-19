package riven.core.entity.workflow.execution

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import riven.core.entity.workflow.WorkflowNodeEntity
import riven.core.enums.workflow.WorkflowStatus
import riven.core.models.common.json.JsonValue
import riven.core.models.workflow.engine.execution.WorkflowExecutionNodeRecord
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

@Entity
@Table(name = "workflow_node_executions")
data class WorkflowExecutionNodeEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "workflow_execution_id", nullable = false, columnDefinition = "uuid")
    val workflowExecutionId: UUID,

    @Column(name = "workflow_node_id", nullable = false, columnDefinition = "uuid")
    val nodeId: UUID,

    @Column(name = "sequence_index", nullable = false, columnDefinition = "int")
    val sequenceIndex: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: WorkflowStatus,

    @Column(name = "started_at", nullable = false, columnDefinition = "timestamptz")
    val startedAt: ZonedDateTime,
    @Column(name = "completed_at", columnDefinition = "timestamptz")
    val completedAt: ZonedDateTime?,
    @Column(name = "duration_ms", nullable = false, columnDefinition = "bigint")
    val durationMs: Long,
    @Column(name = "attempt", nullable = false)
    val attempt: Int,

    @Type(JsonBinaryType::class)
    @Column(name = "error", columnDefinition = "jsonb")
    val error: Any,

    @Type(JsonBinaryType::class)
    @Column(name = "input", columnDefinition = "jsonb")
    val input: JsonValue,

    @Type(JsonBinaryType::class)
    @Column(name = "output", columnDefinition = "jsonb")
    val output: JsonValue

) {

    fun toModel(node: WorkflowNodeEntity? = null): WorkflowExecutionNodeRecord {

        val id = requireNotNull(this.id)
        return WorkflowExecutionNodeRecord(
            id = id,
            workspaceId = this.workspaceId,
            executionId = this.workflowExecutionId,
            node = node?.toModel(),
            sequenceIndex = this.sequenceIndex,
            status = this.status,
            startedAt = this.startedAt,
            completedAt = this.completedAt,
            duration = Duration.ofMillis(this.durationMs),
            attempt = this.attempt,
            input = this.input,
            output = this.output,
            error = this.error
        )
    }
}