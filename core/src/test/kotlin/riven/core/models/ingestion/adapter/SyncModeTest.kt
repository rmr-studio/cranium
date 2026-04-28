package riven.core.models.ingestion.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies the [SyncMode] enum exposes exactly four values in declaration order:
 * POLL, CDC, PUSH, ONE_SHOT. These are the four adapter capability modes defined
 * by the Phase 1 foundation contract.
 */
class SyncModeTest {

    @Test
    fun `SyncMode has exactly four values`() {
        assertThat(SyncMode.values()).hasSize(4)
    }

    @Test
    fun `SyncMode declaration order is POLL, CDC, PUSH, ONE_SHOT`() {
        assertThat(SyncMode.values().map { it.name }).containsExactly(
            "POLL", "CDC", "PUSH", "ONE_SHOT",
        )
    }

    @Test
    fun `SyncMode names match the expected set`() {
        assertThat(SyncMode.values().map { it.name }.toSet())
            .isEqualTo(setOf("POLL", "CDC", "PUSH", "ONE_SHOT"))
    }

    @Test
    fun `SyncMode valueOf resolves each declared value`() {
        assertThat(SyncMode.valueOf("POLL")).isEqualTo(SyncMode.POLL)
        assertThat(SyncMode.valueOf("CDC")).isEqualTo(SyncMode.CDC)
        assertThat(SyncMode.valueOf("PUSH")).isEqualTo(SyncMode.PUSH)
        assertThat(SyncMode.valueOf("ONE_SHOT")).isEqualTo(SyncMode.ONE_SHOT)
    }
}
