package cranium.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EmailMatcherTest {

    private val delta = 0.001

    // ------ extractDomain ------

    @Nested
    inner class ExtractDomain {

        @Test
        fun `standard email returns domain`() {
            assertEquals("acme.com", EmailMatcher.extractDomain("john@acme.com"))
        }

        @Test
        fun `domain is lowercased`() {
            assertEquals("acme.com", EmailMatcher.extractDomain("john@ACME.COM"))
        }

        @Test
        fun `email with dot in local part returns correct domain`() {
            assertEquals("acme.com", EmailMatcher.extractDomain("john.smith@acme.com"))
        }

        @Test
        fun `email with no at sign returns null`() {
            assertNull(EmailMatcher.extractDomain("john"))
        }

        @Test
        fun `empty string returns null`() {
            assertNull(EmailMatcher.extractDomain(""))
        }

        @Test
        fun `malformed email with empty domain returns null`() {
            assertNull(EmailMatcher.extractDomain("john@"))
        }

        @Test
        fun `bare at sign returns null for domain`() {
            assertNull(EmailMatcher.extractDomain("@"))
        }

        /**
         * Regression: extractDomain previously returned a domain for addresses with an empty local
         * part (e.g. "@acme.com") because the guard only checked `idx < 0`. Fixed by rejecting
         * `idx <= 0`, which catches both missing '@' and '@' at position 0.
         */
        @Test
        fun `malformed email with empty local part returns null`() {
            assertNull(EmailMatcher.extractDomain("@acme.com"))
        }
    }

    // ------ extractLocal ------

    @Nested
    inner class ExtractLocal {

        @Test
        fun `simple email returns local part`() {
            assertEquals("john", EmailMatcher.extractLocal("john@acme.com"))
        }

        @Test
        fun `email with dot in local part preserves full local`() {
            assertEquals("john.smith", EmailMatcher.extractLocal("john.smith@acme.com"))
        }

        @Test
        fun `email with no at sign returns null`() {
            assertNull(EmailMatcher.extractLocal("noatsign"))
        }

        @Test
        fun `malformed email with empty local part returns null`() {
            assertNull(EmailMatcher.extractLocal("@acme.com"))
        }

        /**
         * Regression: extractLocal previously returned a local part for addresses with an empty
         * domain (e.g. "john@") because the guard only checked `idx < 0`. Fixed by rejecting
         * `idx == email.lastIndex`, which catches trailing '@' with no domain.
         */
        @Test
        fun `malformed email with empty domain returns null`() {
            assertNull(EmailMatcher.extractLocal("john@"))
        }
    }

    // ------ tokenizeLocal ------

    @Nested
    inner class TokenizeLocal {

        @Test
        fun `dot delimiter splits tokens`() {
            assertEquals(listOf("john", "smith"), EmailMatcher.tokenizeLocal("john.smith"))
        }

        @Test
        fun `underscore delimiter splits tokens`() {
            assertEquals(listOf("john", "smith"), EmailMatcher.tokenizeLocal("john_smith"))
        }

        @Test
        fun `hyphen delimiter splits tokens`() {
            assertEquals(listOf("john", "smith"), EmailMatcher.tokenizeLocal("john-smith"))
        }

        @Test
        fun `non-delimited string stays as single token`() {
            assertEquals(listOf("jsmith"), EmailMatcher.tokenizeLocal("jsmith"))
        }

        @Test
        fun `consecutive delimiters produce empty tokens which are filtered`() {
            assertEquals(listOf("john", "smith"), EmailMatcher.tokenizeLocal("john..smith"))
        }

        @Test
        fun `tokens are lowercased`() {
            assertEquals(listOf("john", "smith"), EmailMatcher.tokenizeLocal("JOHN.SMITH"))
        }

        @Test
        fun `empty string returns empty list`() {
            assertEquals(emptyList<String>(), EmailMatcher.tokenizeLocal(""))
        }
    }

    // ------ localPartSimilarity ------

    @Nested
    inner class LocalPartSimilarity {

        @Test
        fun `reversed token order scores 1 0`() {
            assertEquals(1.0, EmailMatcher.localPartSimilarity("john.smith", "smith.john"), delta)
        }

        @Test
        fun `partial overlap scores correctly`() {
            assertEquals(0.5, EmailMatcher.localPartSimilarity("john.smith", "j.smith"), delta)
        }

        @Test
        fun `no shared tokens scores 0 0`() {
            assertEquals(0.0, EmailMatcher.localPartSimilarity("john.smith", "bob.jones"), delta)
        }

        @Test
        fun `identical local parts score 1 0`() {
            assertEquals(1.0, EmailMatcher.localPartSimilarity("john.smith", "john.smith"), delta)
        }

        @Test
        fun `single token subset scores 1 0`() {
            assertEquals(1.0, EmailMatcher.localPartSimilarity("john", "john.smith"), delta)
        }
    }

    // ------ isFreeEmailDomain ------

    @Nested
    inner class IsFreeEmailDomain {

        @Test
        fun `gmail dot com is free`() {
            assertTrue(EmailMatcher.isFreeEmailDomain("gmail.com"))
        }

        @Test
        fun `yahoo dot com is free`() {
            assertTrue(EmailMatcher.isFreeEmailDomain("yahoo.com"))
        }

        @Test
        fun `check is case insensitive`() {
            assertTrue(EmailMatcher.isFreeEmailDomain("GMAIL.COM"))
        }

        @Test
        fun `corporate domain is not free`() {
            assertFalse(EmailMatcher.isFreeEmailDomain("acme.com"))
        }

        @Test
        fun `outlook dot com is free`() {
            assertTrue(EmailMatcher.isFreeEmailDomain("outlook.com"))
        }

        @Test
        fun `protonmail dot com is free`() {
            assertTrue(EmailMatcher.isFreeEmailDomain("protonmail.com"))
        }
    }
}
