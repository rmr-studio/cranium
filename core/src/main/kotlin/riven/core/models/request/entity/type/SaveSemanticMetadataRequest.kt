package cranium.core.models.request.entity.type

import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.identity.MatchSignalType

/**
 * Request body for saving (upserting) semantic metadata on a single target.
 *
 * Most fields follow PUT semantics: definition, classification, and tags are fully replaced
 * on every call — omitting a field clears it.
 *
 * Classification is nullable — users may set definition/tags first and classify later.
 * Unknown classification values are rejected by Jackson deserialization (400 Bad Request),
 * since ACCEPT_CASE_INSENSITIVE_ENUMS is not enabled.
 *
 * [signalType] is the exception to strict PUT semantics and follows priority rules:
 * - When provided explicitly, it overrides any existing or derived value.
 * - When omitted on an **update**, the existing signal type is preserved (not cleared).
 * - When omitted on a **create**, the service derives a default from the classification
 *   (IDENTIFIER → CUSTOM_IDENTIFIER; all other classifications → null).
 */
data class SaveSemanticMetadataRequest(
    val definition: String? = null,
    val classification: SemanticAttributeClassification? = null,
    val signalType: MatchSignalType? = null,
    val tags: List<String> = emptyList(),
)
