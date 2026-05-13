package cranium.core.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime
import java.util.UUID

class CursorPaginationTest {

    @Test
    fun `encodeCursor and decodeCursor round-trip produces same values`() {
        val createdAt = ZonedDateTime.parse("2026-03-20T10:00:00Z")
        val id = UUID.randomUUID()

        val encoded = CursorPagination.encodeCursor(createdAt, id)
        val (decodedCreatedAt, decodedId) = CursorPagination.decodeCursor(encoded)

        assertEquals(createdAt, decodedCreatedAt)
        assertEquals(id, decodedId)
    }

    @Test
    fun `decodeCursor with null returns now and max UUID`() {
        val (createdAt, id) = CursorPagination.decodeCursor(null)

        // Should be approximately now (within 5 seconds)
        assertTrue(createdAt.isAfter(ZonedDateTime.now().minusSeconds(5)))
        assertEquals(UUID(Long.MAX_VALUE, Long.MAX_VALUE), id)
    }

    @Test
    fun `decodeCursor with malformed cursor throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            CursorPagination.decodeCursor("not-valid-base64-cursor-data")
        }
    }

    @Test
    fun `decodeCursor with base64 but wrong format throws IllegalArgumentException`() {
        val badCursor = java.util.Base64.getUrlEncoder().encodeToString("no-pipe-separator".toByteArray())
        assertThrows<IllegalArgumentException> {
            CursorPagination.decodeCursor(badCursor)
        }
    }
}
