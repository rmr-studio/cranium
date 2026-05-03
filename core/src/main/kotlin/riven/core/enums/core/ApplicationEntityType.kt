package riven.core.enums.core

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "ApplicationEntityType",
    description = "Enumeration of possible entity types within the system.",
    enumAsRef = true,
)
enum class ApplicationEntityType {
    @JsonProperty("WORKSPACE") WORKSPACE,
    @JsonProperty("BLOCK_TYPE") BLOCK_TYPE,
    @JsonProperty("BLOCK") BLOCK,
    @JsonProperty("USER") USER,
    @JsonProperty("ENTITY") ENTITY,
    @JsonProperty("ENTITY_TYPE") ENTITY_TYPE,
    @JsonProperty("WORKFLOW_DEFINITION") WORKFLOW_DEFINITION,
    @JsonProperty("WORKFLOW_NODE") WORKFLOW_NODE,
    @JsonProperty("WORKFLOW_EDGE") WORKFLOW_EDGE,
    @JsonProperty("INTEGRATION_CONNECTION") INTEGRATION_CONNECTION,
    @JsonProperty("FILE") FILE,
    @JsonProperty("NOTIFICATION") NOTIFICATION,
    @JsonProperty("INTEGRATION_INSTALLATION") INTEGRATION_INSTALLATION,
    @JsonProperty("MATCH_SUGGESTION") MATCH_SUGGESTION,
    @JsonProperty("IDENTITY_CLUSTER") IDENTITY_CLUSTER,
    @JsonProperty("NOTE") NOTE,
    @JsonProperty("BUSINESS_DEFINITION") BUSINESS_DEFINITION,
    @JsonProperty("DATA_CONNECTOR_CONNECTION") DATA_CONNECTOR_CONNECTION,
    @JsonProperty("ENTITY_CONNOTATION") ENTITY_CONNOTATION,
}
