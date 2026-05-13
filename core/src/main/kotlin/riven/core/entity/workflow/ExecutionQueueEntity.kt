package cranium.core.entity.workflow

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import kotlinx.serialization.json.JsonObject
import org.hibernate.annotations.Type
import cranium.core.enums.workflow.ExecutionJobType
import cranium.core.enums.workflow.ExecutionQueueStatus
import cranium.core.models.workflow.engine.queue.ExecutionQueueRequest
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for the generic execution queue table.
 *
 * Serves multiple job types via the job_type discriminator column:
 * - WORKFLOW_EXECUTION: Standard workflow dispatch jobs
 * - IDENTITY_MATCH: Entity identity resolution jobs
 *
 * Queue items progress through: PENDING -> CLAIMED -> DISPATCHED (or FAILED)
 *
 * Uses PostgreSQL FOR UPDATE SKIP LOCKED for concurrent-safe claiming.
 */
@Entity
@Table(
    name = "execution_queue",
    indexes = [
        Index(name = "idx_execution_queue_workspace", columnList = "workspace_id, status")
    ]
)
data class ExecutionQueueEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    val jobType: ExecutionJobType = ExecutionJobType.WORKFLOW_EXECUTION,

    @Column(name = "entity_id", columnDefinition = "uuid")
    val entityId: UUID? = null,

    @Column(name = "workflow_definition_id", nullable = true, columnDefinition = "uuid")
    val workflowDefinitionId: UUID? = null,

    @Column(name = "execution_id", columnDefinition = "uuid")
    var executionId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ExecutionQueueStatus = ExecutionQueueStatus.PENDING,

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "claimed_at", columnDefinition = "timestamptz")
    var claimedAt: ZonedDateTime? = null,

    @Column(name = "dispatched_at", columnDefinition = "timestamptz")
    var dispatchedAt: ZonedDateTime? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "input", columnDefinition = "jsonb")
    val input: JsonObject? = null,

    @Column(name = "attempts", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "last_error", columnDefinition = "text")
    var lastError: String? = null
) {
    fun toModel(): ExecutionQueueRequest {
        val id = requireNotNull(this.id) { "ExecutionQueueEntity id must not be null when converting to model" }
        return ExecutionQueueRequest(
            id = id,
            workspaceId = this.workspaceId,
            jobType = this.jobType,
            entityId = this.entityId,
            workflowDefinitionId = this.workflowDefinitionId,
            executionId = this.executionId,
            status = this.status,
            createdAt = this.createdAt,
            claimedAt = this.claimedAt,
            dispatchedAt = this.dispatchedAt,
            attemptCount = this.attemptCount,
            lastError = this.lastError
        )
    }
}
