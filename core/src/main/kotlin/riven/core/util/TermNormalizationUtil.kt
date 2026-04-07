package riven.core.util

/**
 * Normalizes business definition terms for uniqueness comparison.
 *
 * Applies: lowercase, trim whitespace, strip trailing 's' (simple plural stripping).
 * This prevents "Active Customer" and "active customers" from coexisting in the same workspace.
 */
object TermNormalizationUtil {

    private val SINGULAR_SUFFIXES = setOf("us", "is", "os", "ss")

    fun normalize(term: String): String {
        return term
            .trim()
            .lowercase()
            .split("\\s+".toRegex())
            .joinToString(" ") { word ->
                if (word.length > 3 && word.endsWith("s") && SINGULAR_SUFFIXES.none { word.endsWith(it) }) {
                    word.dropLast(1)
                } else {
                    word
                }
            }
    }
}
