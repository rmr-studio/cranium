package riven.core.service.connector.mapping

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Phase 3 Wave-0 placeholder for NlMappingSuggestionService
 * (owned by plan 03-04, covers MAP-08 NL-assisted suggestions).
 * Downstream plan removes @Disabled and replaces placeholder() with real assertions.
 */
@Disabled("populated by plan 03-04")
class NlMappingSuggestionServiceTest {

    @Test fun suggestionsCachedByConnectionAndSchemaHash() = placeholder()
    @Test fun cacheHitDoesNotCallLlm() = placeholder()
    @Test fun cacheMissCallsLlmAndPersistsResult() = placeholder()
    @Test fun llmTimeoutReturnsEmptySuggestionsWithoutThrowing() = placeholder()
    @Test fun llmRateLimitReturnsEmptySuggestionsWithoutThrowing() = placeholder()
    @Test fun inputContainsOnlyTableAndColumnSchemaNeverSampleRows() = placeholder()

    private fun placeholder() {}
}
