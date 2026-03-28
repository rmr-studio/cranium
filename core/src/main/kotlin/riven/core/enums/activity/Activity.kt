package riven.core.enums.activity

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "Activity",
    description = "Enumeration of possible activities within the system.",
    enumAsRef = true,
)
enum class Activity {
    @JsonProperty("LINE_ITEM") LINE_ITEM,
    @JsonProperty("CLIENT") CLIENT,
    @JsonProperty("WORKSPACE") WORKSPACE,
    @JsonProperty("WORKSPACE_MEMBER_INVITE") WORKSPACE_MEMBER_INVITE,
    @JsonProperty("WORKSPACE_MEMBER") WORKSPACE_MEMBER,
    @JsonProperty("INVOICE") INVOICE,
    @JsonProperty("BLOCK") BLOCK,
    @JsonProperty("BLOCK_TYPE") BLOCK_TYPE,
    @JsonProperty("BLOCK_OPERATION") BLOCK_OPERATION,
    @JsonProperty("COMPANY") COMPANY,
    @JsonProperty("REPORT") REPORT,
    @JsonProperty("TEMPLATE") TEMPLATE,
    @JsonProperty("ENTITY_TYPE") ENTITY_TYPE,
    @JsonProperty("ENTITY") ENTITY,
    @JsonProperty("ENTITY_RELATIONSHIP") ENTITY_RELATIONSHIP,
    @JsonProperty("ENTITY_CONNECTION") ENTITY_CONNECTION,
    @JsonProperty("WORKFLOW_DEFINITION") WORKFLOW_DEFINITION,
    @JsonProperty("WORKFLOW_NODE") WORKFLOW_NODE,
    @JsonProperty("WORKFLOW_EDGE") WORKFLOW_EDGE,
    @JsonProperty("INTEGRATION_CONNECTION") INTEGRATION_CONNECTION,
    @JsonProperty("INTEGRATION_ENABLEMENT") INTEGRATION_ENABLEMENT,
    @JsonProperty("FILE_UPLOAD") FILE_UPLOAD,
    @JsonProperty("FILE_DELETE") FILE_DELETE,
    @JsonProperty("FILE_UPDATE") FILE_UPDATE,
    @JsonProperty("ONBOARDING") ONBOARDING,
    @JsonProperty("NOTIFICATION") NOTIFICATION,
    @JsonProperty("MATCH_SUGGESTION") MATCH_SUGGESTION,
    @JsonProperty("IDENTITY_CLUSTER") IDENTITY_CLUSTER,
    @JsonProperty("NOTE") NOTE,
}
