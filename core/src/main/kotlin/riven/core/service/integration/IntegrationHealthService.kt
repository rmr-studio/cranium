package riven.core.service.integration

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.integration.IntegrationSyncStateEntity
import riven.core.enums.integration.ConnectionStatus
import riven.core.enums.integration.SyncStatus
import riven.core.repository.integration.IntegrationConnectionRepository
import riven.core.repository.integration.IntegrationSyncStateRepository
import java.util.UUID

/**
 * Service that derives [ConnectionStatus] from per-entity-type sync outcomes.
 *
 * Runs after each sync cycle to aggregate the health signal across all entity types for a
 * connection. The resulting status is written back to the connection if the transition is valid.
 *
 * Health aggregation rules (evaluated in priority order):
 * 1. DEGRADED — any entity type has consecutiveFailureCount >= [DEGRADED_THRESHOLD]
 * 2. FAILED   — all entity types have status == FAILED
 * 3. HEALTHY  — default when none of the above conditions hold
 *
 * This service has no [@PreAuthorize] — it is invoked from a Temporal activity, not a
 * web request, so there is no JWT security context available.
 *
 * @see riven.core.service.integration.sync.IntegrationSyncActivitiesImpl
 */
@Service
class IntegrationHealthService(
    private val syncStateRepository: IntegrationSyncStateRepository,
    private val connectionRepository: IntegrationConnectionRepository,
    private val logger: KLogger,
) {

    companion object {
        /** Number of consecutive failures required to transition a connection to DEGRADED. */
        const val DEGRADED_THRESHOLD = 3
    }

    // ------ Public API ------

    /**
     * Evaluates the health of a connection based on its current sync states and updates
     * the connection status if the resulting transition is valid.
     *
     * Early-returns without any write if:
     * - No sync states exist for the connection (nothing to evaluate)
     * - The connection entity is not found (graceful no-op)
     * - The aggregated health status cannot be applied to the current connection status
     *   (invalid state machine transition — logged as a warning)
     *
     * @param connectionId UUID of the integration connection to evaluate
     */
    @Transactional
    fun evaluateConnectionHealth(connectionId: UUID) {
        val states = syncStateRepository.findByIntegrationConnectionId(connectionId)
        if (states.isEmpty()) {
            logger.debug { "No sync states found for connection=$connectionId — skipping health evaluation" }
            return
        }

        val connection = connectionRepository.findById(connectionId).orElse(null)
        if (connection == null) {
            logger.warn { "Connection $connectionId not found during health evaluation — skipping" }
            return
        }

        val targetStatus = aggregateHealth(states)

        if (!connection.status.canTransitionTo(targetStatus)) {
            logger.warn {
                "Connection $connectionId in state ${connection.status} cannot transition to $targetStatus — skipping health update"
            }
            return
        }

        val previousStatus = connection.status
        connection.status = targetStatus
        connectionRepository.save(connection)
        logger.info { "Updated connection $connectionId health: $previousStatus -> $targetStatus" }
    }

    // ------ Private Helpers ------

    /**
     * Aggregates the health signal from a list of sync states.
     *
     * Evaluation order (priority highest to lowest):
     * 1. DEGRADED — any state has consecutiveFailureCount >= [DEGRADED_THRESHOLD]
     * 2. FAILED   — all states have status FAILED
     * 3. HEALTHY  — default
     *
     * @param states Non-empty list of sync states for a connection
     * @return The aggregated [ConnectionStatus] reflecting overall connection health
     */
    private fun aggregateHealth(states: List<IntegrationSyncStateEntity>): ConnectionStatus {
        if (states.any { it.consecutiveFailureCount >= DEGRADED_THRESHOLD }) {
            return ConnectionStatus.DEGRADED
        }
        if (states.all { it.status == SyncStatus.FAILED }) {
            return ConnectionStatus.FAILED
        }
        return ConnectionStatus.HEALTHY
    }
}
