package riven.core.service.identity

/**
 * Overlap coefficient computation on word token sets.
 *
 * The overlap coefficient measures how much two token sets intersect relative to the
 * smaller set. Unlike Jaccard similarity, it returns 1.0 for subset containment —
 * meaning "John" vs "John Smith" scores 1.0, making it ideal for partial name matching.
 *
 * Formula: `|A ∩ B| / min(|A|, |B|)`
 *
 * This is a pure stateless Kotlin object — not a Spring bean.
 */
object TokenSimilarity {

    // ------ Public API ------

    /**
     * Computes the overlap coefficient between [a] and [b] treated as word token sets.
     *
     * Tokenization splits on whitespace, lowercases, and filters empty tokens.
     * Returns 0.0 if either input produces an empty token set.
     * Returns 1.0 for identical token sets, reverse-ordered token sets, and subset containment.
     *
     * @param a First string to compare.
     * @param b Second string to compare.
     * @return Overlap coefficient in range [0.0, 1.0].
     */
    fun overlap(a: String, b: String): Double {
        val tokensA = tokenize(a)
        val tokensB = tokenize(b)

        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).size
        val minSize = minOf(tokensA.size, tokensB.size)

        return intersection.toDouble() / minSize.toDouble()
    }

    // ------ Private helpers ------

    /**
     * Splits [value] on whitespace, lowercases each token, and filters empty tokens.
     * Returns an empty set if no tokens remain after filtering.
     */
    private fun tokenize(value: String): Set<String> {
        return value.trim()
            .split(Regex("\\s+"))
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
