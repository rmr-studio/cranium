package riven.core.enums.knowledge

import com.fasterxml.jackson.annotation.JsonProperty

enum class DefinitionCategory {
    @JsonProperty("METRIC") METRIC,
    @JsonProperty("SEGMENT") SEGMENT,
    @JsonProperty("STATUS") STATUS,
    @JsonProperty("LIFECYCLE_STAGE") LIFECYCLE_STAGE,
    @JsonProperty("CUSTOM") CUSTOM,
}
