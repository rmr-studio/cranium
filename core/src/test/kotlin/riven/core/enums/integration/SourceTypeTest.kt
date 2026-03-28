package riven.core.enums.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Unit tests for SourceType enum (INFRA-03).
 *
 * Verifies that IDENTITY_MATCH is a valid SourceType value, ensuring that entity attributes
 * sourced from identity resolution can be stamped with the correct source type without
 * runtime errors.
 *
 * No Spring context required — pure enum value checks.
 */
class SourceTypeTest {

    /**
     * INFRA-03: Verifies IDENTITY_MATCH is a valid SourceType enum value.
     *
     * Original scaffold was disabled pending plan 01-01. Now that IDENTITY_MATCH has been
     * added to the SourceType enum, this test confirms it is reachable via valueOf() and
     * present in the entries list.
     */
    @Test
    fun `IDENTITY_MATCH is a valid SourceType value`() {
        assertDoesNotThrow { SourceType.valueOf("IDENTITY_MATCH") }
        assertTrue(
            SourceType.entries.map { it.name }.contains("IDENTITY_MATCH"),
            "SourceType.entries must contain IDENTITY_MATCH"
        )
    }

    /**
     * Verifies PROJECTED is a valid SourceType enum value.
     *
     * PROJECTED marks entities and attributes that are produced by smart projection
     * (aggregation columns, domain-based routing) rather than direct user or integration input.
     */
    @Test
    fun `PROJECTED is a valid SourceType value`() {
        assertDoesNotThrow { SourceType.valueOf("PROJECTED") }
        assertTrue(
            SourceType.entries.map { it.name }.contains("PROJECTED"),
            "SourceType.entries must contain PROJECTED"
        )
    }
}
