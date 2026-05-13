package cranium.core.enums.integration

import tools.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import cranium.core.enums.integration.SyncStatus

class SyncStatusTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `enum has exactly 3 values`() {
        assertEquals(3, SyncStatus.entries.size)
    }

    @Test
    fun `enum contains PENDING, SUCCESS, and FAILED`() {
        val names = SyncStatus.entries.map { it.name }
        assertEquals(listOf("PENDING", "SUCCESS", "FAILED"), names)
    }

    // ------ Serialization ------

    @Test
    fun `PENDING serializes to PENDING`() {
        val json = mapper.writeValueAsString(SyncStatus.PENDING)
        assertEquals("\"PENDING\"", json)
    }

    @Test
    fun `SUCCESS serializes to SUCCESS`() {
        val json = mapper.writeValueAsString(SyncStatus.SUCCESS)
        assertEquals("\"SUCCESS\"", json)
    }

    @Test
    fun `FAILED serializes to FAILED`() {
        val json = mapper.writeValueAsString(SyncStatus.FAILED)
        assertEquals("\"FAILED\"", json)
    }

    // ------ Deserialization ------

    @Test
    fun `PENDING deserializes correctly`() {
        val value = mapper.readValue("\"PENDING\"", SyncStatus::class.java)
        assertEquals(SyncStatus.PENDING, value)
    }

    @Test
    fun `SUCCESS deserializes correctly`() {
        val value = mapper.readValue("\"SUCCESS\"", SyncStatus::class.java)
        assertEquals(SyncStatus.SUCCESS, value)
    }

    @Test
    fun `FAILED deserializes correctly`() {
        val value = mapper.readValue("\"FAILED\"", SyncStatus::class.java)
        assertEquals(SyncStatus.FAILED, value)
    }
}
