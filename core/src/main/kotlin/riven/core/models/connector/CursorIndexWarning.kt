package riven.core.models.connector

/**
 * Surfaced on GET /schema and POST /mapping when the chosen (or auto-detected)
 * sync-cursor column has no supporting Postgres index. Non-blocking —
 * the mapping UI renders a yellow banner; Save proceeds.
 *
 * The [suggestedDdl] string is a ready-to-run `CREATE INDEX` statement the
 * user can copy into psql.
 */
data class CursorIndexWarning(
    val column: String,
    val suggestedDdl: String,
)
