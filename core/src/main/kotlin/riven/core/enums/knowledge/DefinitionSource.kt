package cranium.core.enums.knowledge

import com.fasterxml.jackson.annotation.JsonProperty

enum class DefinitionSource {
    @JsonProperty("ONBOARDING") ONBOARDING,
    @JsonProperty("DISCOVERY") DISCOVERY,
    @JsonProperty("QUERY_PROMPT") QUERY_PROMPT,
    @JsonProperty("MANUAL") MANUAL,
}
