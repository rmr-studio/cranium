package cranium.core.models.ingestion

import java.util.UUID

/**
 * Result of identity resolution for a single integration entity.
 * Either matched to an existing core entity or flagged as new.
 */
sealed class ResolutionResult {

    /**
     * Matched to an existing core entity.
     */
    data class ExistingEntity(
        val entityId: UUID,
        val matchType: MatchType,
    ) : ResolutionResult()

    /**
     * No match found — eligible for auto-creation.
     */
    data class NewEntity(
        val warnings: List<String> = emptyList(),
    ) : ResolutionResult()
}

enum class MatchType {
    EXTERNAL_ID,
    IDENTIFIER_KEY,
}
