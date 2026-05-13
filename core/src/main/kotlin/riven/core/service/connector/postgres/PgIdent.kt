package cranium.core.service.connector.postgres

/**
 * Safe double-quoting of Postgres SQL identifiers (schema, table, column names).
 *
 * Identifiers come from metadata queries (pg_catalog) but callers also pass
 * them via request payloads. Bind parameters cannot carry identifiers, so
 * identifiers must be interpolated — [quote] enforces a strict allowlist and
 * defensively escapes embedded quotes.
 */
internal object PgIdent {

    private val PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_$]*$")

    /**
     * Returns [name] wrapped in double-quotes with any embedded `"` doubled up,
     * per SQL-standard identifier escaping. Throws [IllegalArgumentException]
     * if [name] does not match `[A-Za-z_][A-Za-z0-9_$]*`.
     */
    fun quote(name: String): String {
        require(PATTERN.matches(name)) {
            "Invalid Postgres identifier: '$name' (must match [A-Za-z_][A-Za-z0-9_$]*)"
        }
        return "\"" + name.replace("\"", "\"\"") + "\""
    }
}
