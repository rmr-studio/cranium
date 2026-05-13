package cranium.core.service.connector.postgres

import cranium.core.enums.common.validation.SchemaType

/**
 * Maps a raw Postgres data-type literal (as returned by `INFORMATION_SCHEMA`
 * or `pg_attribute.format_type`) to Cranium's [SchemaType] enum.
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

        val lowered = pgType.lowercase().trim()

        // Array detection runs on the raw-lowered literal BEFORE stripping
        // typmods so `varchar(255)[]` and `_int4` are both classified as array.
        if (lowered.startsWith("_") || lowered.endsWith("[]")) {
            return SchemaType.OBJECT
        }

        val normalized = lowered.substringBefore("(").trim()

        return when (normalized) {
            // TEXT family
            "text", "varchar", "char", "citext",
            "character varying", "character" -> SchemaType.TEXT

            // NUMBER family
            "int2", "int4", "int8", "int", "integer", "bigint", "smallint",
            "numeric", "decimal", "real",
            "float4", "float8", "double precision", "money" -> SchemaType.NUMBER

            // CHECKBOX
            "bool", "boolean" -> SchemaType.CHECKBOX

            // DATE
            "date" -> SchemaType.DATE

            // DATETIME family — includes format_type() full names
            "timestamp", "timestamptz",
            "timestamp with time zone", "timestamp without time zone",
            "time", "timetz",
            "time without time zone", "time with time zone" -> SchemaType.DATETIME

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
