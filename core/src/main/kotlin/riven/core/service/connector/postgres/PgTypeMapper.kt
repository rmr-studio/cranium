package riven.core.service.connector.postgres

import riven.core.enums.common.validation.SchemaType

/**
 * Maps a raw Postgres data-type literal (as returned by `INFORMATION_SCHEMA`
 * or `pg_attribute.format_type`) to Riven's [SchemaType] enum.
 *
 * Stateless utility — no Spring bean required. Callers invoke directly.
 *
 * The mapping table is canonical per 03-CONTEXT.md:
 * - `text`/`varchar`/`char`/`citext` → [SchemaType.TEXT]
 * - integer + floating-point numerics + `money` → [SchemaType.NUMBER]
 * - `bool`/`boolean` → [SchemaType.CHECKBOX]
 * - `date` → [SchemaType.DATE]
 * - timestamp / time family → [SchemaType.DATETIME]
 * - `uuid` + `isPrimaryKey=true` → [SchemaType.ID]; otherwise → [SchemaType.TEXT]
 * - non-null [enumOptions] (caller detected a pg user-defined enum) → [SchemaType.SELECT]
 * - `jsonb`/`json` → [SchemaType.OBJECT]
 * - array types (`_<name>` or `<name>[]`) → [SchemaType.OBJECT]
 * - `bytea` → [SchemaType.OBJECT]
 * - PostGIS `geometry`/`geography` → [SchemaType.LOCATION]
 * - fallback → [SchemaType.OBJECT]
 *
 * Email/phone/url heuristic upgrades are NOT performed here — those belong to
 * the LLM/name-heuristic layer in plan 03-04.
 */
object PgTypeMapper {

    fun toSchemaType(
        pgType: String,
        isPrimaryKey: Boolean = false,
        enumOptions: List<String>? = null,
    ): SchemaType {
        // User-defined enums short-circuit: the caller has already resolved
        // pg_enum rows, so the presence of `enumOptions` is the signal.
        if (enumOptions != null) return SchemaType.SELECT

        val normalized = pgType.lowercase().substringBefore("(").trim()

        // Array detection runs before the exhaustive `when` so any underlying
        // element type (including recognized scalar types) becomes OBJECT when
        // presented as an array.
        if (normalized.startsWith("_") || normalized.endsWith("[]")) {
            return SchemaType.OBJECT
        }

        return when (normalized) {
            // TEXT family
            "text", "varchar", "char", "citext" -> SchemaType.TEXT

            // NUMBER family
            "int2", "int4", "int8", "int", "integer", "bigint", "smallint",
            "numeric", "decimal", "real",
            "float4", "float8", "double precision", "money" -> SchemaType.NUMBER

            // CHECKBOX
            "bool", "boolean" -> SchemaType.CHECKBOX

            // DATE
            "date" -> SchemaType.DATE

            // DATETIME family
            "timestamp", "timestamptz",
            "timestamp with time zone", "timestamp without time zone",
            "time", "timetz" -> SchemaType.DATETIME

            // UUID — PK becomes ID, non-PK demotes to TEXT
            "uuid" -> if (isPrimaryKey) SchemaType.ID else SchemaType.TEXT

            // OBJECT: json, bytea
            "jsonb", "json", "bytea" -> SchemaType.OBJECT

            // LOCATION: PostGIS
            "geometry", "geography" -> SchemaType.LOCATION

            // Fallback — any unrecognized custom/composite/domain type.
            else -> SchemaType.OBJECT
        }
    }
}
