package cranium.core.enums.note

import com.fasterxml.jackson.annotation.JsonProperty

enum class NoteSourceType {
    @JsonProperty("USER")
    USER,

    @JsonProperty("INTEGRATION")
    INTEGRATION,
}
