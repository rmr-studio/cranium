package riven.core.service.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Utility for computing deterministic content hashes of entity type schemas.
 *
 * Produces a SHA-256 hex digest from a canonical JSON serialization where all map
 * keys are sorted alphabetically at every nesting level and numeric types are
 * normalized to a canonical decimal string via BigDecimal.stripTrailingZeros()
 * (preventing false mismatches from Jackson Int/Long/Double variance when
 * deserializing JSONB while preserving precision for integers > 2^53).
 */
object SchemaHashUtil {

    private val objectMapper = ObjectMapper()

    /**
     * Compute a SHA-256 hex digest of the canonical JSON representation of the given schema map.
     */
    fun computeSchemaHash(schema: Map<String, Any>): String {
        val canonical = objectMapper.writeValueAsString(canonicalize(schema))
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Recursively sort all map keys and normalize numeric types for deterministic hashing.
     * Handles nested Map, List, and Jackson numeric coercion (Int/Long/Double
     * variance from JSONB deserialization). Numbers are normalized to a canonical
     * BigDecimal string with trailing zeros stripped — `1`, `1L`, and `1.0` collapse
     * to the same `"1"` while large longs (> 2^53) keep full precision instead of
     * collapsing onto an imprecise Double representation.
     */
    @Suppress("UNCHECKED_CAST")
    private fun canonicalize(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.toSortedMap(compareBy { it.toString() })
            .mapValues { (_, v) -> canonicalize(v) }
        is List<*> -> value.map { canonicalize(it) }
        is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        else -> value
    }
}
