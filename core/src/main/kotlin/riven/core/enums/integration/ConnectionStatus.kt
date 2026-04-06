package riven.core.enums.integration

enum class ConnectionStatus {
    CONNECTED,
    SYNCING,
    HEALTHY,
    DEGRADED,
    STALE,
    DISCONNECTING,
    DISCONNECTED,
    FAILED;

    fun canTransitionTo(newStatus: ConnectionStatus): Boolean {
        return when (this) {
            CONNECTED     -> newStatus in setOf(SYNCING, HEALTHY, DISCONNECTING, FAILED)
            SYNCING       -> newStatus in setOf(HEALTHY, DEGRADED, FAILED)
            HEALTHY       -> newStatus in setOf(SYNCING, STALE, DEGRADED, DISCONNECTING, FAILED)
            DEGRADED      -> newStatus in setOf(SYNCING, HEALTHY, STALE, FAILED, DISCONNECTING)
            STALE         -> newStatus in setOf(SYNCING, DISCONNECTING, FAILED)
            DISCONNECTING -> newStatus in setOf(DISCONNECTED, FAILED)
            DISCONNECTED  -> newStatus in setOf(CONNECTED)
            FAILED        -> newStatus in setOf(SYNCING, CONNECTED, DISCONNECTED)
        }
    }
}
