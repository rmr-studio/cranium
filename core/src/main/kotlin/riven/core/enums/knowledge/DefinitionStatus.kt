package riven.core.enums.knowledge

import com.fasterxml.jackson.annotation.JsonProperty

enum class DefinitionStatus {
    @JsonProperty("ACTIVE") ACTIVE,
    @JsonProperty("SUGGESTED") SUGGESTED,
}
