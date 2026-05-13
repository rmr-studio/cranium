package cranium.core.enums.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import cranium.core.enums.integration.InstallationStatus

class InstallationStatusTest {

    @Test
    fun `enum has exactly 3 values`() {
        assertEquals(3, InstallationStatus.entries.size)
    }

    // ------ Valid transitions ------

    @Test
    fun `PENDING_CONNECTION can transition to ACTIVE`() {
        assertTrue(InstallationStatus.PENDING_CONNECTION.canTransitionTo(InstallationStatus.ACTIVE))
    }

    @Test
    fun `PENDING_CONNECTION can transition to FAILED`() {
        assertTrue(InstallationStatus.PENDING_CONNECTION.canTransitionTo(InstallationStatus.FAILED))
    }

    @Test
    fun `ACTIVE can transition to FAILED`() {
        assertTrue(InstallationStatus.ACTIVE.canTransitionTo(InstallationStatus.FAILED))
    }

    @Test
    fun `FAILED can transition to PENDING_CONNECTION`() {
        assertTrue(InstallationStatus.FAILED.canTransitionTo(InstallationStatus.PENDING_CONNECTION))
    }

    // ------ Self-transitions (idempotent for webhook retries) ------

    @Test
    fun `PENDING_CONNECTION self-transition is allowed for idempotent webhook retries`() {
        assertTrue(InstallationStatus.PENDING_CONNECTION.canTransitionTo(InstallationStatus.PENDING_CONNECTION))
    }

    @Test
    fun `ACTIVE self-transition is allowed for idempotent webhook retries`() {
        assertTrue(InstallationStatus.ACTIVE.canTransitionTo(InstallationStatus.ACTIVE))
    }

    @Test
    fun `FAILED self-transition is allowed for idempotent webhook retries`() {
        assertTrue(InstallationStatus.FAILED.canTransitionTo(InstallationStatus.FAILED))
    }

    // ------ Invalid transitions ------

    @Test
    fun `ACTIVE cannot transition to PENDING_CONNECTION`() {
        assertFalse(InstallationStatus.ACTIVE.canTransitionTo(InstallationStatus.PENDING_CONNECTION))
    }

    @Test
    fun `FAILED cannot transition to ACTIVE`() {
        assertFalse(InstallationStatus.FAILED.canTransitionTo(InstallationStatus.ACTIVE))
    }
}
