package riven.core.models.connector.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup

/**
 * Request body for POST /api/v1/custom-sources/connections/{id}/schema/tables/{tableName}/mapping.
 *
 * Carries the table-level configuration ([lifecycleDomain] + [semanticGroup])
 * and the full per-column mapping list. Columns missing from the request but
 * present in the live introspection are marked as `isMapped=false` implicitly
 * (they don't contribute to the generated EntityType).
 *
 * @property lifecycleDomain User-selected domain for the downstream EntityType.
 * @property semanticGroup User-selected semantic group for the downstream EntityType.
 * @property columns Per-column mapping decisions; the nested `@field:Valid`
 *   ensures bean validation recurses into each row.
 */
data class SaveDataConnectorMappingRequest(
    @field:NotNull
    val lifecycleDomain: LifecycleDomain,

    @field:NotNull
    val semanticGroup: SemanticGroup,

    @field:Valid
    @field:NotEmpty(message = "columns must not be empty")
    val columns: List<SaveDataConnectorFieldMappingRequest>,
)

data class SaveDataConnectorFieldMappingRequest(
    @field:NotBlank
    val columnName: String,

    val attributeName: String?,

    @field:NotNull
    val schemaType: SchemaType,

    val isIdentifier: Boolean = false,
    val isSyncCursor: Boolean = false,
    val isMapped: Boolean = false,
)
