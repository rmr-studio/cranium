package riven.core.models.catalog

import com.fasterxml.jackson.annotation.JsonProperty
import riven.core.enums.entity.validation.EntityTypeChangeType
import java.util.*

/**
 * Describes a single difference between a catalog schema and its workspace counterpart.
 */
data class SchemaChange(
    val type: EntityTypeChangeType,
    val attributeKey: String,
    val description: String,
    val breaking: Boolean,
    val workspaceAttributeId: UUID? = null
)

// ------ Health Response Models ------

data class SchemaHealthResponse(
    val entityTypes: List<EntityTypeHealthStatus>,
    val summary: SchemaHealthSummary
)

data class EntityTypeHealthStatus(
    val entityTypeId: UUID,
    val entityTypeKey: String,
    val displayName: String,
    val status: SchemaHealthStatusType,
    val sourceSchemaHash: String?,
    val catalogSchemaHash: String?,
    val pendingChanges: List<PendingSchemaChange>
)

enum class SchemaHealthStatusType {
    @JsonProperty("UP_TO_DATE") UP_TO_DATE,
    @JsonProperty("PENDING_NON_BREAKING") PENDING_NON_BREAKING,
    @JsonProperty("PENDING_BREAKING") PENDING_BREAKING,
    @JsonProperty("UNKNOWN") UNKNOWN
}

data class PendingSchemaChange(
    val type: EntityTypeChangeType,
    val attributeKey: String,
    val description: String,
    val breaking: Boolean,
    val affectedEntityCount: Long
)

data class SchemaHealthSummary(
    val total: Int,
    val upToDate: Int,
    val pendingNonBreaking: Int,
    val pendingBreaking: Int,
    val unknown: Int
)

// ------ Reconciliation Result Models ------

data class ReconciliationResult(
    val reconciled: List<ReconciledEntityType>,
    val errors: List<String>
)

data class ReconciledEntityType(
    val entityTypeId: UUID,
    val changesApplied: Int,
    val breakingChangesApplied: Int
)

data class ReconciliationImpact(
    val impacts: Map<UUID, EntityTypeImpact>
)

data class EntityTypeImpact(
    /**
     * Total attribute writes/deletes that breaking changes will touch on this entity type — summed across
     * every breaking change. May exceed the entity row count when a single row carries multiple attributes
     * affected by the reconcile, by design: this counts admin-visible destructive impact, not distinct rows.
     */
    val affectedEntities: Long,
    val fieldsRemoved: List<String>,
    val dataLoss: Boolean
)
