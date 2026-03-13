package riven.core.enums.activity

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "Activity",
    description = "Enumeration of possible activities within the system.",
    enumAsRef = true,
)
enum class Activity {
    LINE_ITEM,
    CLIENT,
    WORKSPACE,
    WORKSPACE_MEMBER_INVITE,
    WORKSPACE_MEMBER,
    INVOICE,
    BLOCK,
    BLOCK_TYPE,
    BLOCK_OPERATION,
    COMPANY,
    REPORT,
    TEMPLATE,
    ENTITY_TYPE,
    ENTITY,
    ENTITY_RELATIONSHIP,
    ENTITY_CONNECTION,
    WORKFLOW_DEFINITION,
    WORKFLOW_NODE,
    WORKFLOW_EDGE,
    INTEGRATION_CONNECTION,
    FILE_UPLOAD,
    FILE_DELETE,
    FILE_UPDATE,
    ONBOARDING
}

