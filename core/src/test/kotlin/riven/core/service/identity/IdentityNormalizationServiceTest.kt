package cranium.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import cranium.core.enums.identity.MatchSignalType

class IdentityNormalizationServiceTest {

    private val service = IdentityNormalizationService(mock<KLogger>())

    // ------ Phone normalization ------

    @Nested
    inner class PhoneNormalization {

        @Test
        fun `strips all formatting from US phone with country code`() {
            assertEquals("5551234567", service.normalize("+1 (555) 123-4567", MatchSignalType.PHONE))
        }

        @Test
        fun `already clean 10-digit phone is unchanged`() {
            assertEquals("5551234567", service.normalize("5551234567", MatchSignalType.PHONE))
        }

        @Test
        fun `11-digit number starting with 1 strips leading 1`() {
            assertEquals("5551234567", service.normalize("15551234567", MatchSignalType.PHONE))
        }

        @Test
        fun `non-US 12-digit number not starting with 1 keeps all digits`() {
            // "44 20 7946 0958" -> "442079460958" — 12 digits, starts with 4, no strip
            assertEquals("442079460958", service.normalize("44 20 7946 0958", MatchSignalType.PHONE))
        }

        @Test
        fun `non-US 11-digit number not starting with 1 keeps all digits`() {
            // "44 207 946 095" -> "44207946095" — 11 digits, starts with 4, no strip
            assertEquals("44207946095", service.normalize("44 207 946 095", MatchSignalType.PHONE))
        }

        @Test
        fun `12-digit number starting with 1 is NOT stripped`() {
            // "180055512345" — 12 digits, starts with 1, but not exactly 11 digits so no strip
            assertEquals("180055512345", service.normalize("180055512345", MatchSignalType.PHONE))
        }

        @Test
        fun `10-digit number with dashes is stripped to digits only`() {
            assertEquals("5551234567", service.normalize("555-123-4567", MatchSignalType.PHONE))
        }
    }

    // ------ Email normalization ------

    @Nested
    inner class EmailNormalization {

        @Test
        fun `strips plus-address tag from local part`() {
            assertEquals("john@example.com", service.normalize("john+tag@example.com", MatchSignalType.EMAIL))
        }

        @Test
        fun `lowercases email`() {
            assertEquals("john@example.com", service.normalize("JOHN@EXAMPLE.COM", MatchSignalType.EMAIL))
        }

        @Test
        fun `email without plus address is unchanged except lowercase`() {
            assertEquals("john@example.com", service.normalize("john@example.com", MatchSignalType.EMAIL))
        }

        @Test
        fun `invalid email without at symbol returns lowercase trimmed value`() {
            assertEquals("invalidemail", service.normalize("invalidemail", MatchSignalType.EMAIL))
        }

        @Test
        fun `trims whitespace from email`() {
            assertEquals("john@example.com", service.normalize("  john@example.com  ", MatchSignalType.EMAIL))
        }
    }

    // ------ Name normalization ------

    @Nested
    inner class NameNormalization {

        @Test
        fun `strips honorific title from start and suffix from end`() {
            assertEquals("jose garcia", service.normalize("Dr. Jose Garcia Jr.", MatchSignalType.NAME))
        }

        @Test
        fun `lowercases plain name`() {
            assertEquals("jose garcia", service.normalize("Jose Garcia", MatchSignalType.NAME))
        }

        @Test
        fun `strips diacritics from accented characters`() {
            // José García with combining diacritics -> jose garcia
            val accented = "Jos\u00e9 Garc\u00eda"
            assertEquals("jose garcia", service.normalize(accented, MatchSignalType.NAME))
        }

        @Test
        fun `strips title from start and suffix from end for multi-word name`() {
            assertEquals("martin luther king", service.normalize("Dr. Martin Luther King Jr.", MatchSignalType.NAME))
        }

        @Test
        fun `does not strip stopword from mid-string position`() {
            // "John Dr. Smith" — "Dr." is mid-string, should not be stripped
            assertEquals("john dr. smith", service.normalize("John Dr. Smith", MatchSignalType.NAME))
        }

        @Test
        fun `strips PhD suffix from end`() {
            assertEquals("alice johnson", service.normalize("Alice Johnson PhD", MatchSignalType.NAME))
        }

        @Test
        fun `strips period-terminated stopword like Dr dot`() {
            assertEquals("jane doe", service.normalize("Dr. Jane Doe", MatchSignalType.NAME))
        }

        @Test
        fun `strips comma from reversed name format`() {
            // "Smith, John Sr" — comma becomes space, Sr stripped from end
            assertEquals("smith john", service.normalize("Smith, John Sr", MatchSignalType.NAME))
        }
    }

    // ------ Company normalization ------

    @Nested
    inner class CompanyNormalization {

        @Test
        fun `strips legal suffix from end of company name`() {
            assertEquals("acme", service.normalize("Acme Corp.", MatchSignalType.COMPANY))
        }

        @Test
        fun `does NOT strip from start for company — only from end`() {
            // "Dr. Acme Corp." — "Dr." is not a company stopword, "Corp." is, strip only from end
            assertEquals("dr. acme", service.normalize("Dr. Acme Corp.", MatchSignalType.COMPANY))
        }

        @Test
        fun `strips LLC suffix`() {
            assertEquals("widget makers", service.normalize("Widget Makers LLC", MatchSignalType.COMPANY))
        }

        @Test
        fun `strips Inc suffix with period`() {
            assertEquals("global tech", service.normalize("Global Tech Inc.", MatchSignalType.COMPANY))
        }

        @Test
        fun `strips comma and legal suffix from company name`() {
            // "Acme, Inc." — comma becomes space, Inc stripped from end
            assertEquals("acme", service.normalize("Acme, Inc.", MatchSignalType.COMPANY))
        }

        @Test
        fun `lowercases company name`() {
            assertEquals("acme", service.normalize("ACME CORP", MatchSignalType.COMPANY))
        }
    }

    // ------ Custom identifier normalization ------

    @Nested
    inner class CustomIdentifierNormalization {

        @Test
        fun `trims and lowercases custom identifier`() {
            assertEquals("somevalue", service.normalize("  SomeValue  ", MatchSignalType.CUSTOM_IDENTIFIER))
        }

        @Test
        fun `preserves hyphens and other characters`() {
            assertEquals("custom-id-123", service.normalize("CUSTOM-ID-123", MatchSignalType.CUSTOM_IDENTIFIER))
        }
    }

    // ------ Signal type dispatch ------

    @Nested
    inner class SignalTypeDispatch {

        @Test
        fun `EMAIL signal type dispatches to email normalization`() {
            // Plus-address stripping is email-specific behavior
            assertEquals("john@example.com", service.normalize("john+tag@example.com", MatchSignalType.EMAIL))
        }

        @Test
        fun `PHONE signal type dispatches to phone normalization`() {
            assertEquals("5551234567", service.normalize("+1 (555) 123-4567", MatchSignalType.PHONE))
        }

        @Test
        fun `NAME signal type dispatches to text normalization with start-strip enabled`() {
            assertEquals("jose garcia", service.normalize("Dr. Jose Garcia", MatchSignalType.NAME))
        }

        @Test
        fun `COMPANY signal type dispatches to text normalization with start-strip disabled`() {
            // "Inc Acme Corp" — "Inc" at start should NOT be stripped for COMPANY
            assertEquals("inc acme", service.normalize("Inc Acme Corp", MatchSignalType.COMPANY))
        }

        @Test
        fun `CUSTOM_IDENTIFIER dispatches to trim and lowercase only`() {
            assertEquals("john+tag@example.com", service.normalize("  john+tag@example.com  ", MatchSignalType.CUSTOM_IDENTIFIER))
        }
    }
}
