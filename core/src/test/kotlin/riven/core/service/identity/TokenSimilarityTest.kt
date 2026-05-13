package cranium.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TokenSimilarityTest {

    private val delta = 0.001

    // ------ Full overlap ------

    @Nested
    inner class FullOverlap {

        @Test
        fun `identical tokens in different order score 1 0`() {
            assertEquals(1.0, TokenSimilarity.overlap("John Smith", "Smith John"), delta)
        }

        @Test
        fun `single token is subset of multi-token string`() {
            assertEquals(1.0, TokenSimilarity.overlap("John", "John Smith"), delta)
        }

        @Test
        fun `multi-token contains single token as subset`() {
            assertEquals(1.0, TokenSimilarity.overlap("John Smith", "John"), delta)
        }

        @Test
        fun `identical single tokens score 1 0`() {
            assertEquals(1.0, TokenSimilarity.overlap("John", "John"), delta)
        }

        @Test
        fun `identical multi-token strings score 1 0`() {
            assertEquals(1.0, TokenSimilarity.overlap("John Smith", "John Smith"), delta)
        }
    }

    // ------ Partial overlap ------

    @Nested
    inner class PartialOverlap {

        @Test
        fun `one shared token out of two scores 0 5`() {
            assertEquals(0.5, TokenSimilarity.overlap("John Smith", "John Doe"), delta)
        }

        @Test
        fun `two shared tokens out of three scores 0 666`() {
            assertEquals(2.0 / 3.0, TokenSimilarity.overlap("John Michael Smith", "John Michael Doe"), delta)
        }
    }

    // ------ No overlap ------

    @Nested
    inner class NoOverlap {

        @Test
        fun `completely different names score 0 0`() {
            assertEquals(0.0, TokenSimilarity.overlap("John Smith", "Jane Doe"), delta)
        }

        @Test
        fun `completely different single tokens score 0 0`() {
            assertEquals(0.0, TokenSimilarity.overlap("John", "Jane"), delta)
        }
    }

    // ------ Empty input ------

    @Nested
    inner class EmptyInput {

        @Test
        fun `empty first string scores 0 0`() {
            assertEquals(0.0, TokenSimilarity.overlap("", "John"), delta)
        }

        @Test
        fun `empty second string scores 0 0`() {
            assertEquals(0.0, TokenSimilarity.overlap("John", ""), delta)
        }

        @Test
        fun `both empty strings score 0 0`() {
            assertEquals(0.0, TokenSimilarity.overlap("", ""), delta)
        }

        @Test
        fun `whitespace only string scores 0 0`() {
            assertEquals(0.0, TokenSimilarity.overlap("   ", "John"), delta)
        }
    }

    // ------ Case and whitespace handling ------

    @Nested
    inner class NormalizationHandling {

        @Test
        fun `comparison is case insensitive`() {
            assertEquals(1.0, TokenSimilarity.overlap("JOHN", "john"), delta)
        }

        @Test
        fun `mixed case strings compare correctly`() {
            assertEquals(1.0, TokenSimilarity.overlap("John Smith", "JOHN SMITH"), delta)
        }

        @Test
        fun `extra whitespace is handled`() {
            assertEquals(1.0, TokenSimilarity.overlap("  John   Smith  ", "John Smith"), delta)
        }

        @Test
        fun `tab characters treated as whitespace`() {
            assertEquals(1.0, TokenSimilarity.overlap("John\tSmith", "John Smith"), delta)
        }
    }
}
