package cranium.core.models.entity

import java.util.*


data class SemanticMetadataBundle(
    val entityType: EntityTypeSemanticMetadata?,
    val attributes: Map<UUID, EntityTypeSemanticMetadata>,
    val relationships: Map<UUID, EntityTypeSemanticMetadata>,
)
