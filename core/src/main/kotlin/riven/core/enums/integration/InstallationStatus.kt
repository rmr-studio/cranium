package riven.core.enums.integration

import com.fasterxml.jackson.annotation.JsonProperty

enum class InstallationStatus {
    @JsonProperty("PENDING_CONNECTION")
    PENDING_CONNECTION,

    @JsonProperty("ACTIVE")
    ACTIVE,

    @JsonProperty("FAILED")
    FAILED;

    fun canTransitionTo(newStatus: InstallationStatus): Boolean {
        return when (this) {
            PENDING_CONNECTION -> newStatus in setOf(PENDING_CONNECTION, ACTIVE, FAILED)
            ACTIVE -> newStatus in setOf(ACTIVE, FAILED)
            FAILED -> newStatus in setOf(FAILED, PENDING_CONNECTION)
        }
    }
}
