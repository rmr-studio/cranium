package riven.core.enums.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Wave 0 unit test scaffold for SourceType enum (INFRA-03).
 *
 * Verifies that the IDENTITY_MATCH value is present in SourceType after plan 01-01 adds it.
 * No Spring context required — pure enum value check.
 *
 * Disabled until plan 01-01 adds IDENTITY_MATCH to the SourceType enum.
 */
class SourceTypeTest {

    /**
     * INFRA-03: Verifies IDENTITY_MATCH is a valid SourceType enum value.
     *
     * After plan 01-01: SourceType.IDENTITY_MATCH must be reachable via valueOf() and
     * present in the enum entries list. This ensures that entity attributes sourced from
     * identity resolution can be stamped with the correct source type without runtime errors.
     */
    @Test
    @Disabled("Wave 0 scaffold — enable after 01-01 implementation")
    fun `IDENTITY_MATCH is a valid SourceType value`() {
        assertDoesNotThrow { SourceType.valueOf("IDENTITY_MATCH") }
        assertTrue(
            SourceType.entries.map { it.name }.contains("IDENTITY_MATCH"),
            "SourceType.entries must contain IDENTITY_MATCH"
        )
    }
}
