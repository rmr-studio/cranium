package riven.core.enums.integration

import com.fasterxml.jackson.annotation.JsonProperty

enum class SyncScope {
    @JsonProperty("all")
    ALL,

    @JsonProperty("recent")
    RECENT
}
