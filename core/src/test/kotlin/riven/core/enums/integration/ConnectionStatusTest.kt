package riven.core.enums.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for ConnectionStatus state machine.
 *
 * Verifies all valid transitions succeed and key invalid transitions
 * are rejected by the canTransitionTo() method.
 *
 * The 8-state FSM: CONNECTED, SYNCING, HEALTHY, DEGRADED, STALE,
 * DISCONNECTING, DISCONNECTED, FAILED.
 * PENDING_AUTHORIZATION and AUTHORIZING were removed in Phase 2 — connections
 * are created directly as CONNECTED after successful Nango OAuth webhook delivery.
 */
class ConnectionStatusTest {

    // ========== Enum Count ==========

    @Test
    fun `ConnectionStatus has exactly 8 states`() {
        assertEquals(8, ConnectionStatus.entries.size)
    }

    // ========== CONNECTED Transitions ==========

    @Test
    fun `CONNECTED can transition to SYNCING`() {
        assertTrue(ConnectionStatus.CONNECTED.canTransitionTo(ConnectionStatus.SYNCING))
    }

    @Test
    fun `CONNECTED can transition to HEALTHY`() {
        assertTrue(ConnectionStatus.CONNECTED.canTransitionTo(ConnectionStatus.HEALTHY))
    }

    @Test
    fun `CONNECTED can transition to DISCONNECTING`() {
        assertTrue(ConnectionStatus.CONNECTED.canTransitionTo(ConnectionStatus.DISCONNECTING))
    }

    @Test
    fun `CONNECTED can transition to FAILED`() {
        assertTrue(ConnectionStatus.CONNECTED.canTransitionTo(ConnectionStatus.FAILED))
    }

    @Test
    fun `CONNECTED cannot transition to DEGRADED`() {
        assertFalse(ConnectionStatus.CONNECTED.canTransitionTo(ConnectionStatus.DEGRADED))
    }

    // ========== SYNCING Transitions ==========

    @Test
    fun `SYNCING can transition to HEALTHY`() {
        assertTrue(ConnectionStatus.SYNCING.canTransitionTo(ConnectionStatus.HEALTHY))
    }

    @Test
    fun `SYNCING can transition to DEGRADED`() {
        assertTrue(ConnectionStatus.SYNCING.canTransitionTo(ConnectionStatus.DEGRADED))
    }

    @Test
    fun `SYNCING can transition to FAILED`() {
        assertTrue(ConnectionStatus.SYNCING.canTransitionTo(ConnectionStatus.FAILED))
    }

    @Test
    fun `SYNCING cannot transition to DISCONNECTING`() {
        assertFalse(ConnectionStatus.SYNCING.canTransitionTo(ConnectionStatus.DISCONNECTING))
    }

    // ========== HEALTHY Transitions ==========

    @Test
    fun `HEALTHY can transition to SYNCING`() {
        assertTrue(ConnectionStatus.HEALTHY.canTransitionTo(ConnectionStatus.SYNCING))
    }

    @Test
    fun `HEALTHY can transition to STALE`() {
        assertTrue(ConnectionStatus.HEALTHY.canTransitionTo(ConnectionStatus.STALE))
    }

    @Test
    fun `HEALTHY can transition to DEGRADED`() {
        assertTrue(ConnectionStatus.HEALTHY.canTransitionTo(ConnectionStatus.DEGRADED))
    }

    @Test
    fun `HEALTHY can transition to DISCONNECTING`() {
        assertTrue(ConnectionStatus.HEALTHY.canTransitionTo(ConnectionStatus.DISCONNECTING))
    }

    @Test
    fun `HEALTHY can transition to FAILED`() {
        assertTrue(ConnectionStatus.HEALTHY.canTransitionTo(ConnectionStatus.FAILED))
    }

    @Test
    fun `HEALTHY cannot transition to CONNECTED`() {
        assertFalse(ConnectionStatus.HEALTHY.canTransitionTo(ConnectionStatus.CONNECTED))
    }

    // ========== DEGRADED Transitions ==========

    @Test
    fun `DEGRADED can transition to HEALTHY`() {
        assertTrue(ConnectionStatus.DEGRADED.canTransitionTo(ConnectionStatus.HEALTHY))
    }

    @Test
    fun `DEGRADED can transition to STALE`() {
        assertTrue(ConnectionStatus.DEGRADED.canTransitionTo(ConnectionStatus.STALE))
    }

    @Test
    fun `DEGRADED can transition to FAILED`() {
        assertTrue(ConnectionStatus.DEGRADED.canTransitionTo(ConnectionStatus.FAILED))
    }

    @Test
    fun `DEGRADED can transition to DISCONNECTING`() {
        assertTrue(ConnectionStatus.DEGRADED.canTransitionTo(ConnectionStatus.DISCONNECTING))
    }

    @Test
    fun `DEGRADED can transition to SYNCING`() {
        // Recovery path: a DEGRADED connection re-enters SYNCING when the next sync cycle begins.
        // Health evaluation will then set the correct result status (HEALTHY/DEGRADED/FAILED).
        assertTrue(ConnectionStatus.DEGRADED.canTransitionTo(ConnectionStatus.SYNCING))
    }

    // ========== STALE Transitions ==========

    @Test
    fun `STALE can transition to SYNCING`() {
        assertTrue(ConnectionStatus.STALE.canTransitionTo(ConnectionStatus.SYNCING))
    }

    @Test
    fun `STALE can transition to DISCONNECTING`() {
        assertTrue(ConnectionStatus.STALE.canTransitionTo(ConnectionStatus.DISCONNECTING))
    }

    @Test
    fun `STALE can transition to FAILED`() {
        assertTrue(ConnectionStatus.STALE.canTransitionTo(ConnectionStatus.FAILED))
    }

    @Test
    fun `STALE cannot transition to HEALTHY`() {
        assertFalse(ConnectionStatus.STALE.canTransitionTo(ConnectionStatus.HEALTHY))
    }

    // ========== DISCONNECTING Transitions ==========

    @Test
    fun `DISCONNECTING can transition to DISCONNECTED`() {
        assertTrue(ConnectionStatus.DISCONNECTING.canTransitionTo(ConnectionStatus.DISCONNECTED))
    }

    @Test
    fun `DISCONNECTING can transition to FAILED`() {
        assertTrue(ConnectionStatus.DISCONNECTING.canTransitionTo(ConnectionStatus.FAILED))
    }

    @Test
    fun `DISCONNECTING cannot transition to CONNECTED`() {
        assertFalse(ConnectionStatus.DISCONNECTING.canTransitionTo(ConnectionStatus.CONNECTED))
    }

    // ========== DISCONNECTED Transitions ==========

    @Test
    fun `DISCONNECTED can transition to CONNECTED`() {
        // In the webhook-driven model, re-connecting a DISCONNECTED integration
        // comes directly from DISCONNECTED -> CONNECTED (Nango webhook confirms OAuth success).
        assertTrue(ConnectionStatus.DISCONNECTED.canTransitionTo(ConnectionStatus.CONNECTED))
    }

    @Test
    fun `DISCONNECTED cannot transition to SYNCING`() {
        assertFalse(ConnectionStatus.DISCONNECTED.canTransitionTo(ConnectionStatus.SYNCING))
    }

    @Test
    fun `DISCONNECTED cannot transition to HEALTHY`() {
        assertFalse(ConnectionStatus.DISCONNECTED.canTransitionTo(ConnectionStatus.HEALTHY))
    }

    @Test
    fun `DISCONNECTED cannot transition to FAILED`() {
        assertFalse(ConnectionStatus.DISCONNECTED.canTransitionTo(ConnectionStatus.FAILED))
    }

    // ========== FAILED Transitions ==========

    @Test
    fun `FAILED can transition to CONNECTED`() {
        // Recovery from FAILED goes directly to CONNECTED via webhook-driven reconnect.
        assertTrue(ConnectionStatus.FAILED.canTransitionTo(ConnectionStatus.CONNECTED))
    }

    @Test
    fun `FAILED can transition to DISCONNECTED`() {
        assertTrue(ConnectionStatus.FAILED.canTransitionTo(ConnectionStatus.DISCONNECTED))
    }

    @Test
    fun `FAILED can transition to SYNCING`() {
        // Recovery path: a FAILED connection can re-enter SYNCING when a new sync cycle begins.
        // This allows failed connections to self-heal once the underlying issue is resolved.
        assertTrue(ConnectionStatus.FAILED.canTransitionTo(ConnectionStatus.SYNCING))
    }

    @Test
    fun `FAILED cannot transition to HEALTHY`() {
        assertFalse(ConnectionStatus.FAILED.canTransitionTo(ConnectionStatus.HEALTHY))
    }
}
