package cranium.core.service.util.factory.workflow

import cranium.core.entity.workflow.WorkflowDefinitionEntity
import cranium.core.entity.workflow.WorkflowDefinitionVersionEntity
import cranium.core.entity.workflow.WorkflowNodeEntity
import cranium.core.entity.workflow.execution.WorkflowExecutionEntity
import cranium.core.entity.workflow.execution.WorkflowExecutionNodeEntity
import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.workflow.WorkflowDefinitionStatus
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.enums.workflow.WorkflowTriggerType
import cranium.core.models.workflow.WorkflowGraphReference
import cranium.core.models.workflow.node.config.WorkflowFunctionConfig
import cranium.core.models.workflow.node.config.WorkflowNodeConfig
import java.time.ZonedDateTime
import java.util.*

/**
 * Factory for creating workflow-related test data.
 */
object WorkflowFactory {

    /**
     * Create a WorkflowDefinitionEntity for testing.
     */
    fun createDefinition(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        name: String = "Test Workflow",
        description: String? = null,
        versionNumber: Int = 1,
        status: WorkflowDefinitionStatus = WorkflowDefinitionStatus.DRAFT,
        iconColour: IconColour = IconColour.NEUTRAL,
        iconType: IconType = IconType.WORKFLOW,
        tags: List<String> = emptyList(),
        deleted: Boolean = false,
        deletedAt: ZonedDateTime? = null
    ): WorkflowDefinitionEntity {
        return WorkflowDefinitionEntity(
            id = id,
            workspaceId = workspaceId,
            name = name,
            description = description,
            versionNumber = versionNumber,
            status = status,
            iconColour = iconColour,
            iconType = iconType,
            tags = tags,
        ).also {
            it.deleted = deleted
            it.deletedAt = deletedAt
        }
    }

    /**
     * Create a WorkflowDefinitionVersionEntity for testing.
     */
    fun createVersion(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        workflowDefinitionId: UUID,
        versionNumber: Int = 1,
        workflow: WorkflowGraphReference = WorkflowGraphReference(nodeIds = setOf(), edgeIds = setOf()),
        canvas: Any = emptyMap<String, Any>(),
        deleted: Boolean = false,
        deletedAt: ZonedDateTime? = null
    ): WorkflowDefinitionVersionEntity {
        return WorkflowDefinitionVersionEntity(
            id = id,
            workspaceId = workspaceId,
            workflowDefinitionId = workflowDefinitionId,
            versionNumber = versionNumber,
            workflow = workflow,
            canvas = canvas,
        ).also {
            it.deleted = deleted
            it.deletedAt = deletedAt
        }
    }

    /**
     * Create a WorkflowExecutionEntity for testing.
     */
    fun createExecution(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        workflowDefinitionId: UUID,
        workflowVersionId: UUID = UUID.randomUUID(),
        status: WorkflowStatus = WorkflowStatus.RUNNING,
        triggerType: WorkflowTriggerType = WorkflowTriggerType.FUNCTION,
        startedAt: ZonedDateTime = ZonedDateTime.now(),
        completedAt: ZonedDateTime? = null,
        durationMs: Long = 0,
        error: Any? = null,
        input: Any? = null,
        output: Any? = null
    ): WorkflowExecutionEntity {
        return WorkflowExecutionEntity(
            id = id,
            workspaceId = workspaceId,
            workflowDefinitionId = workflowDefinitionId,
            workflowVersionId = workflowVersionId,
            status = status,
            triggerType = triggerType,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMs = durationMs,
            error = error,
            input = input,
            output = output
        )
    }

    /**
     * Create a WorkflowExecutionNodeEntity for testing.
     */
    fun createNodeExecution(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        workflowExecutionId: UUID,
        nodeId: UUID,
        sequenceIndex: Int = 0,
        status: WorkflowStatus = WorkflowStatus.COMPLETED,
        startedAt: ZonedDateTime = ZonedDateTime.now(),
        completedAt: ZonedDateTime? = ZonedDateTime.now(),
        durationMs: Long = 100,
        attempt: Int = 1,
        error: Any = emptyMap<String, Any>(),
        input: Any? = null,
        output: Any? = null
    ): WorkflowExecutionNodeEntity {
        return WorkflowExecutionNodeEntity(
            id = id,
            workspaceId = workspaceId,
            workflowExecutionId = workflowExecutionId,
            nodeId = nodeId,
            sequenceIndex = sequenceIndex,
            status = status,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMs = durationMs,
            attempt = attempt,
            error = error,
            input = input,
            output = output
        )
    }

    /**
     * Create a WorkflowNodeEntity for testing.
     */
    fun createNode(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        key: String = "test-node-${UUID.randomUUID().toString().take(8)}",
        name: String = "Test Node",
        description: String? = null,
        type: WorkflowNodeType = WorkflowNodeType.FUNCTION,
        version: Int = 1,
        sourceId: UUID? = null,
        config: WorkflowNodeConfig = WorkflowFunctionConfig(version = 1),
        system: Boolean = false,
        deleted: Boolean = false,
        deletedAt: ZonedDateTime? = null
    ): WorkflowNodeEntity {
        return WorkflowNodeEntity(
            id = id,
            workspaceId = workspaceId,
            key = key,
            name = name,
            description = description,
            type = type,
            version = version,
            sourceId = sourceId,
            config = config,
            system = system,
        ).also {
            it.deleted = deleted
            it.deletedAt = deletedAt
        }
    }
}
