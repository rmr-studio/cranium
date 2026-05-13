package cranium.core.util

import java.time.ZonedDateTime
import java.util.Base64
import java.util.UUID

/**
 * Shared cursor pagination utilities for seek-based (keyset) pagination.
 *
 * Cursor format: Base64-encoded `{createdAt}|{id}` — compound key ensures
 * stable ordering even when multiple records share the same timestamp.
 *
 * ```
 *   Page 1 request:  cursor = null  → defaults to (now, max-UUID)
 *   Page 1 response: nextCursor = encodeCursor(lastItem)
 *   Page 2 request:  cursor = nextCursor from page 1
 *   ...
 *   Last page:       nextCursor = null (fewer items than limit)
 * ```
 */
object CursorPagination {

    /**
     * Encodes a cursor from a timestamp and ID.
     */
    fun encodeCursor(createdAt: ZonedDateTime, id: UUID): String =
        Base64.getUrlEncoder().encodeToString("$createdAt|$id".toByteArray())

    /**
     * Decodes a cursor string into a (createdAt, id) pair.
     *
     * When [cursor] is null (first page request), returns a pair that sorts
     * after every possible record: (now, max-UUID).
     *
     * @throws IllegalArgumentException if the cursor format is invalid
     */
    fun decodeCursor(cursor: String?): Pair<ZonedDateTime, UUID> {
        if (cursor == null) {
            return ZonedDateTime.now() to UUID(Long.MAX_VALUE, Long.MAX_VALUE)
        }
        val decoded = String(Base64.getUrlDecoder().decode(cursor))
        val parts = decoded.split("|", limit = 2)
        require(parts.size == 2) { "Invalid cursor format" }
        return ZonedDateTime.parse(parts[0]) to UUID.fromString(parts[1])
    }
}

/**
 * Generic response wrapper for cursor-paginated endpoints.
 *
 * @param T the type of items in the page
 */
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val totalCount: Long? = null,
)
