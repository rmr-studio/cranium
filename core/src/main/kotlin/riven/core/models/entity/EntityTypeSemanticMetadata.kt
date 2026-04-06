package riven.core.models.entity

import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.identity.MatchSignalType
import java.time.ZonedDateTime
import java.util.*

/**
 * Domain model for semantic metadata attached to an entity type, one of its attributes,
 * or one of its relationship definitions.
 */
data class EntityTypeSemanticMetadata(
    val id: UUID,
    val workspaceId: UUID,
    val entityTypeId: UUID,
    val targetType: SemanticMetadataTargetType,
    val targetId: UUID,
    val definition: String?,
    val classification: SemanticAttributeClassification?,
    val signalType: MatchSignalType? = null,
    val tags: List<String>,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
    val createdBy: UUID?,
    val updatedBy: UUID?
)
