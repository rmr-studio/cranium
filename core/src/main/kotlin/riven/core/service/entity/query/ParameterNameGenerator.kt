package cranium.core.service.entity.query

/**
 * Generates unique parameter names for SQL query construction.
 *
 * A single generator instance should be used per query tree to ensure
 * parameter names are unique across all fragments. The generator maintains
 * an internal counter that increments with each call to [next].
 *
 * **Usage pattern:**
 * Create one instance per query, pass it through all fragment generation calls.
 *
 * ```kotlin
 * val paramGen = ParameterNameGenerator()
 * val eqParam = paramGen.next("eq")     // "eq_0"
 * val gtParam = paramGen.next("gt")     // "gt_1"
 * val eqParam2 = paramGen.next("eq")    // "eq_2"
 * ```
 *
 * **Thread safety:** This class is not thread-safe. Use a separate instance
 * per query construction context.
 */
class ParameterNameGenerator {
    private var counter: Int = 0

    /**
     * Generates a unique parameter name with the given prefix.
     *
     * Each call returns a unique name by appending an incrementing counter
     * to the prefix. The format is `{prefix}_{counter}`.
     *
     * @param prefix Descriptive prefix for the parameter (e.g., "eq", "gt", "contains")
     * @return Unique parameter name like "eq_0", "gt_1", etc.
     */
    fun next(prefix: String): String = "${prefix}_${counter++}"
}
