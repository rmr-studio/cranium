package riven.core.enums.core

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "ApplicationEntityType",
    description = "Enumeration of possible entity types within the system.",
    enumAsRef = true,
)
enum class ApplicationEntityType {
    WORKSPACE,
    BLOCK_TYPE,
    BLOCK,
    USER,
    ENTITY,
    ENTITY_TYPE,
    WORKFLOW_DEFINITION,
    WORKFLOW_NODE,
    WORKFLOW_EDGE,
    INTEGRATION_CONNECTION,
    FILE
}
