package cranium.core.service.entity.query

/**
 * Immutable container for a parameterized SQL fragment.
 *
 * SqlFragment holds a SQL string with named parameter placeholders (e.g., `:param_0`)
 * and a map of parameter values to bind when executing the query. Fragments can be
 * composed via [and], [or], and [wrap] methods, which return new immutable instances
 * preserving the original fragments.
 *
 * This pattern enables safe SQL generation by:
 * - Preventing SQL injection through parameterized queries
 * - Avoiding parameter name collisions via unique naming
 * - Supporting arbitrary composition without mutation
 *
 * Example usage:
 * ```kotlin
 * val statusFilter = SqlFragment("e.payload->>'status' = :status_0", mapOf("status_0" to "Active"))
 * val tierFilter = SqlFragment("e.payload->>'tier' = :tier_0", mapOf("tier_0" to "Premium"))
 * val combined = statusFilter.and(tierFilter)
 * // combined.sql = "(e.payload->>'status' = :status_0) AND (e.payload->>'tier' = :tier_0)"
 * ```
 *
 * @property sql SQL fragment with named parameter placeholders
 * @property parameters Map of parameter names to values for binding
 */
data class SqlFragment(
    val sql: String,
    val parameters: Map<String, Any?>
) {
    /**
     * Combines this fragment with another using AND logic.
     *
     * Both conditions must match for the combined fragment to match.
     * Parameters from both fragments are merged.
     *
     * @param other The fragment to combine with
     * @return New fragment with `(this.sql) AND (other.sql)` and merged parameters
     */
    fun and(other: SqlFragment): SqlFragment = SqlFragment(
        sql = "(${this.sql}) AND (${other.sql})",
        parameters = this.parameters + other.parameters
    )

    /**
     * Combines this fragment with another using OR logic.
     *
     * Either condition matching will cause the combined fragment to match.
     * Parameters from both fragments are merged.
     *
     * @param other The fragment to combine with
     * @return New fragment with `(this.sql) OR (other.sql)` and merged parameters
     */
    fun or(other: SqlFragment): SqlFragment = SqlFragment(
        sql = "(${this.sql}) OR (${other.sql})",
        parameters = this.parameters + other.parameters
    )

    /**
     * Wraps this fragment's SQL with a prefix and suffix.
     *
     * Useful for wrapping conditions in EXISTS subqueries or other SQL constructs.
     * Parameters remain unchanged.
     *
     * @param prefix String to prepend to the SQL
     * @param suffix String to append to the SQL
     * @return New fragment with wrapped SQL and same parameters
     */
    fun wrap(prefix: String, suffix: String): SqlFragment = SqlFragment(
        sql = "$prefix${this.sql}$suffix",
        parameters = this.parameters
    )

    companion object {
        /**
         * Always-true condition for empty AND combinations.
         *
         * When combining an empty list of conditions with AND logic,
         * this returns a fragment that always evaluates to true.
         */
        val ALWAYS_TRUE = SqlFragment("1=1", emptyMap())

        /**
         * Always-false condition for empty OR combinations.
         *
         * When combining an empty list of conditions with OR logic,
         * this returns a fragment that always evaluates to false.
         */
        val ALWAYS_FALSE = SqlFragment("1=0", emptyMap())
    }
}
