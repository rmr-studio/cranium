package riven.core.service.connector.postgres

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import riven.core.models.ingestion.adapter.ColumnSchema
import java.security.MessageDigest

/**
 * Deterministic, column-order-independent hash of a table's shape.
 *
 * Contract:
 * - Canonical JSON: `{"c":[{"n":"<col>","p":"<pgType>","u":<nullable>},...],"t":"<tableName>"}`
 *   (keys alphabetically sorted via [MapperFeature.SORT_PROPERTIES_ALPHABETICALLY]).
 * - Columns sorted by [ColumnSchema.name] BEFORE serialization so physical
 *   declaration order does not affect the hash.
 * - Hash = SHA-256 over UTF-8 bytes, returned as lowercase hex (64 chars).
 *
 * Used by Phase 3 to detect schema drift between `/schema` calls and to
 * cache LLM suggestions per (connection, table, schemaHash).
 */
object SchemaHasher {

    private val mapper: ObjectMapper = jacksonObjectMapper()
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)

    fun compute(tableName: String, columns: List<ColumnSchema>): String {
        // Sort columns alphabetically by name so declaration-order changes
        // do not affect the hash (03-CONTEXT drift-detection contract).
        val canonicalColumns = columns
            .sortedBy { it.name }
            .map { CanonicalColumn(n = it.name, p = it.typeLiteral, u = it.nullable) }

        val canonical = CanonicalTable(t = tableName, c = canonicalColumns)
        val bytes = mapper.writeValueAsBytes(canonical)

        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * Canonical, field-alphabetical projection of a table. Short field names
     * (`t`, `c`) keep the serialized form compact and stable against JVM
     * field-name reflection quirks.
     */
    private data class CanonicalTable(
        val c: List<CanonicalColumn>,
        val t: String,
    )

    private data class CanonicalColumn(
        val n: String,
        val p: String,
        val u: Boolean,
    )
}
