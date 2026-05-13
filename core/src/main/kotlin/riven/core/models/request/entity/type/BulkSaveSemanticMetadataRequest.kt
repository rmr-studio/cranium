package cranium.core.models.request.entity.type

import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.identity.MatchSignalType
import java.util.UUID

/**
 * A single entry in a bulk attribute-metadata upsert request.
 *
 * Each entry targets a specific attribute UUID within the entity type.
 * PUT semantics apply per entry: all fields are fully replaced.
 *
 * [signalType] is optional. When provided, it takes priority over any existing or derived
 * signal type. When omitted on an update, the existing signal type is preserved; on a
 * create, it falls back to [deriveSignalType].
 */
data class BulkSaveSemanticMetadataRequest(
    val targetId: UUID,
    val definition: String? = null,
    val classification: SemanticAttributeClassification? = null,
    val signalType: MatchSignalType? = null,
    val tags: List<String> = emptyList(),
)
