package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.enums.identity.MatchSignalType
import java.text.Normalizer

/**
 * Signal-type-aware value normalization service.
 *
 * Dispatches to phone, email, or text normalization strategies based on [MatchSignalType],
 * ensuring that values are reduced to a canonical form before matching. This is the
 * foundation for accurate candidate retrieval and scoring.
 */
@Service
class IdentityNormalizationService(
    private val logger: KLogger,
) {

    companion object {
        /**
         * Honorifics, generational suffixes, and professional credentials stripped from
         * the start and end of NAME values.
         */
        val NAME_STOPWORDS: Set<String> = setOf(
            "mr", "mrs", "ms", "miss", "dr", "prof", "rev",
            "sr", "jr", "ii", "iii", "iv",
            "phd", "md", "dds", "cpa", "esq",
        )

        /**
         * Legal entity suffixes stripped from the end of COMPANY values.
         */
        val COMPANY_STOPWORDS: Set<String> = setOf(
            "inc", "llc", "corp", "corporation", "ltd", "limited",
            "gmbh", "co", "company", "lp", "llp", "plc",
        )
    }

    // ------ Public API ------

    /**
     * Normalizes [value] using the strategy appropriate for [signalType].
     *
     * - PHONE: strips non-digit characters and country codes
     * - EMAIL: strips plus-address tags and lowercases
     * - NAME: strips diacritics, honorifics at start, and suffixes at end
     * - COMPANY: strips diacritics and legal entity suffixes at end only
     * - CUSTOM_IDENTIFIER: trim and lowercase only
     */
    fun normalize(value: String, signalType: MatchSignalType): String = when (signalType) {
        MatchSignalType.PHONE -> normalizePhone(value)
        MatchSignalType.EMAIL -> normalizeEmail(value)
        MatchSignalType.NAME -> normalizeText(value, NAME_STOPWORDS, stripFromStart = true)
        MatchSignalType.COMPANY -> normalizeText(value, COMPANY_STOPWORDS, stripFromStart = false)
        MatchSignalType.CUSTOM_IDENTIFIER -> value.trim().lowercase()
    }

    // ------ Private normalization strategies ------

    /**
     * Strips all non-digit characters from [value], then removes a leading "1" country
     * code if the result is exactly 11 digits and starts with "1" (NANP US/Canada numbers only).
     */
    private fun normalizePhone(value: String): String {
        val digits = value.replace(Regex("[^0-9]"), "")
        return if (digits.length == 11 && digits.startsWith("1")) {
            digits.substring(1)
        } else {
            digits
        }
    }

    /**
     * Lowercases and trims [value], then strips any plus-address tag from the local part
     * (the segment before '@'). If no '@' is present the value is returned as-is after
     * trimming and lowercasing.
     */
    private fun normalizeEmail(value: String): String {
        val cleaned = value.trim().lowercase()
        val atIndex = cleaned.indexOf('@')
        if (atIndex == -1) return cleaned

        val local = cleaned.substring(0, atIndex)
        val domain = cleaned.substring(atIndex + 1)
        val strippedLocal = local.substringBefore('+')
        return "$strippedLocal@$domain"
    }

    /**
     * Normalizes a text value by:
     * 1. Decomposing diacritics via NFKD and stripping combining marks
     * 2. Lowercasing
     * 3. Optionally stripping stopwords from the start of the token list
     * 4. Stripping stopwords from the end of the token list
     * 5. Joining remaining tokens with a single space
     *
     * @param stripFromStart Whether to remove leading stopword tokens (true for NAME, false for COMPANY)
     */
    private fun normalizeText(value: String, stopwords: Set<String>, stripFromStart: Boolean): String {
        val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
            .replace(Regex("\\p{Mn}"), "")
            .lowercase()
            .replace(Regex("[,;:!?]"), " ")

        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()

        if (stripFromStart) {
            while (tokens.isNotEmpty() && tokens.first().trimEnd('.') in stopwords) {
                tokens.removeAt(0)
            }
        }

        while (tokens.isNotEmpty() && tokens.last().trimEnd('.') in stopwords) {
            tokens.removeAt(tokens.lastIndex)
        }

        return tokens.joinToString(" ")
    }
}
