package riven.core.enums.integration

import com.fasterxml.jackson.annotation.JsonProperty

enum class SyncStatus {
    @JsonProperty("PENDING")
    PENDING,

    @JsonProperty("SUCCESS")
    SUCCESS,

    @JsonProperty("FAILED")
    FAILED
}
