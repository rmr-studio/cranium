package riven.core.enums.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "NotificationReferenceType",
    description = "Domain entity type that a notification references for navigation.",
    enumAsRef = true,
)
enum class NotificationReferenceType {
    @JsonProperty("ENTITY_RESOLUTION")
    ENTITY_RESOLUTION,

    @JsonProperty("WORKFLOW_STEP")
    WORKFLOW_STEP,

    @JsonProperty("WORKFLOW_DEFINITION")
    WORKFLOW_DEFINITION,

    @JsonProperty("ENTITY")
    ENTITY,

    @JsonProperty("ENTITY_TYPE")
    ENTITY_TYPE,

    @JsonProperty("WORKSPACE")
    WORKSPACE,
}
