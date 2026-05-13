package cranium.core.service.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Regression: canonicalize used `Number.toDouble()`, which loses precision for integers above 2^53
 * and produced identical hashes for distinct numeric values. The fix routes all numbers through
 * `BigDecimal(value.toString()).stripTrailingZeros().toPlainString()` so large longs keep precision
 * while still collapsing Int / Long / Double variance for integral values that survive Jackson
 * deserialization (e.g. `1`, `1L`, `1.0` all hash equal).
 */
class SchemaHashUtilTest {

    @Test
    fun `large longs above 2^53 hash distinctly`() {
        val a = SchemaHashUtil.computeSchemaHash(mapOf("limit" to 9_007_199_254_740_993L))
        val b = SchemaHashUtil.computeSchemaHash(mapOf("limit" to 9_007_199_254_740_992L))

        assertNotEquals(a, b, "distinct longs > 2^53 must not collide under canonicalization")
    }

    @Test
    fun `int and long with same integral value hash equal`() {
        val asInt = SchemaHashUtil.computeSchemaHash(mapOf("limit" to 1))
        val asLong = SchemaHashUtil.computeSchemaHash(mapOf("limit" to 1L))

        assertEquals(asInt, asLong)
    }

    @Test
    fun `integral double and int hash equal`() {
        val asDouble = SchemaHashUtil.computeSchemaHash(mapOf("limit" to 1.0))
        val asInt = SchemaHashUtil.computeSchemaHash(mapOf("limit" to 1))

        assertEquals(asDouble, asInt, "1.0 and 1 should canonicalize to the same decimal string")
    }

    @Test
    fun `nested numeric values are canonicalized recursively`() {
        val a = SchemaHashUtil.computeSchemaHash(
            mapOf("nested" to mapOf("min" to 1, "values" to listOf(1L, 2L)))
        )
        val b = SchemaHashUtil.computeSchemaHash(
            mapOf("nested" to mapOf("min" to 1.0, "values" to listOf(1, 2)))
        )

        assertEquals(a, b)
    }
}
