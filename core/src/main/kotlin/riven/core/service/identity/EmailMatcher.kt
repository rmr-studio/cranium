package riven.core.service.identity

/**
 * Email parsing and comparison utility for identity matching.
 *
 * Provides email decomposition (local part + domain extraction), local-part tokenization,
 * overlap-based similarity scoring for local parts, and free email domain detection.
 *
 * This is a pure stateless Kotlin object — not a Spring bean. Follows the same pattern as
 * [NicknameExpander] and [TokenSimilarity] from Phase 3.
 *
 * Usage:
 * - `extractDomain("john@acme.com")` returns `"acme.com"`
 * - `extractLocal("john.smith@acme.com")` returns `"john.smith"`
 * - `tokenizeLocal("john.smith")` returns `["john", "smith"]`
 * - `localPartSimilarity("john.smith", "smith.john")` returns `1.0`
 * - `isFreeEmailDomain("gmail.com")` returns `true`
 */
object EmailMatcher {

    // ------ Free email domain set ------

    /**
     * Set of well-known free/consumer email providers. All entries are lowercase.
     * Used by [isFreeEmailDomain] to suppress domain-aware candidate queries for these providers.
     * Approximately 34 entries covering global majors, Microsoft variants, Yahoo variants,
     * GMX/mail.com family, regional providers, US ISP/legacy, and other common free providers.
     */
    private val FREE_EMAIL_DOMAINS: Set<String> = setOf(
        // Global majors
        "gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "aol.com", "icloud.com",
        "protonmail.com", "proton.me",
        // Microsoft variants
        "live.com", "msn.com",
        // Yahoo variants
        "yahoo.co.uk", "yahoo.fr", "yahoo.de", "yahoo.es", "yahoo.it", "ymail.com",
        // GMX / mail.com family
        "mail.com", "gmx.com", "gmx.de", "gmx.net", "web.de",
        // Regional
        "mail.ru", "qq.com", "yandex.com", "yandex.ru",
        // ISP / legacy US
        "comcast.net", "att.net", "verizon.net", "sbcglobal.net",
        // Other free providers
        "me.com", "mac.com", "zoho.com", "fastmail.com", "tutanota.com",
    )

    // ------ Public API ------

    /**
     * Extracts the domain part from an email address (the part after the last `@`), lowercased.
     *
     * Returns `null` if no `@` is present, or if either the local part or domain is empty
     * (e.g. `"@acme.com"` or `"john@"`).
     *
     * @param email The email address to parse.
     * @return The domain string (e.g. `"acme.com"`), or `null` if the address is malformed.
     */
    fun extractDomain(email: String): String? {
        val idx = email.lastIndexOf('@')
        if (idx <= 0 || idx == email.lastIndex) return null
        return email.substring(idx + 1).lowercase()
    }

    /**
     * Extracts the local part from an email address (the part before the last `@`).
     *
     * Returns `null` if no `@` is present, or if either the local part or domain is empty
     * (e.g. `"@acme.com"` or `"john@"`).
     *
     * @param email The email address to parse.
     * @return The local part string (e.g. `"john.smith"`), or `null` if the address is malformed.
     */
    fun extractLocal(email: String): String? {
        val idx = email.lastIndexOf('@')
        if (idx <= 0 || idx == email.lastIndex) return null
        return email.substring(0, idx)
    }

    /**
     * Tokenizes an email local part into a list of lowercase tokens.
     *
     * Splits on `.`, `_`, and `-` delimiters. Empty tokens produced by consecutive delimiters
     * are filtered out. No heuristic splitting of non-delimited strings — `"jsmith"` stays as
     * the single token `["jsmith"]`.
     *
     * @param local The local part of an email address (e.g. `"john.smith"`, `"john_smith"`).
     * @return List of lowercase tokens (e.g. `["john", "smith"]`).
     */
    fun tokenizeLocal(local: String): List<String> =
        local.lowercase()
            .split(Regex("[._-]"))
            .filter { it.isNotEmpty() }

    /**
     * Computes the overlap coefficient between two email local parts.
     *
     * Tokenizes both local parts via [tokenizeLocal], joins tokens with spaces, and delegates
     * to [TokenSimilarity.overlap] for the actual overlap computation. Returns 1.0 for reversed
     * token orders (e.g. `john.smith` vs `smith.john`), and 0.0 for completely disjoint token sets.
     *
     * @param a First local part to compare (e.g. `"john.smith"`).
     * @param b Second local part to compare (e.g. `"smith.john"`).
     * @return Overlap coefficient in range [0.0, 1.0].
     */
    fun localPartSimilarity(a: String, b: String): Double {
        val tokensA = tokenizeLocal(a).joinToString(" ")
        val tokensB = tokenizeLocal(b).joinToString(" ")
        return TokenSimilarity.overlap(tokensA, tokensB)
    }

    /**
     * Returns `true` if the given domain belongs to a well-known free/consumer email provider.
     *
     * The check is case-insensitive. Corporate and custom domains return `false`.
     *
     * @param domain The email domain to check (e.g. `"gmail.com"`, `"acme.com"`).
     * @return `true` if the domain is in the [FREE_EMAIL_DOMAINS] set, `false` otherwise.
     */
    fun isFreeEmailDomain(domain: String): Boolean = domain.lowercase() in FREE_EMAIL_DOMAINS
}
