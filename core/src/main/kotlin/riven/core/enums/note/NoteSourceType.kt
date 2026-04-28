package riven.core.enums.note

import com.fasterxml.jackson.annotation.JsonProperty

enum class NoteSourceType {
    @JsonProperty("USER")
    USER,

    @JsonProperty("INTEGRATION")
    INTEGRATION,
}
