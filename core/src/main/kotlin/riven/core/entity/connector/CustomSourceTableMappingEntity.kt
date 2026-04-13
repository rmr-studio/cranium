package riven.core.entity.connector

import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Wave-0 shell for the Phase 3 custom-source table-mapping entity.
 *
 * Populated with full JPA annotations, SQL DDL, repository, and `toModel()`
 * by plan 03-01. Shape matches the field list declared in plan 03-00's
 * `<interfaces>` block so the corresponding test factory compiles today and
 * downstream plans can flesh out persistence without touching call sites.
 */
class CustomSourceTableMappingEntity(
    val id: UUID? = null,
    val workspaceId: UUID,
    val connectionId: UUID,
    val tableName: String,
    val lifecycleDomain: LifecycleDomain,
    val semanticGroup: SemanticGroup,
    val entityTypeId: UUID? = null,
    val schemaHash: String,
    val lastIntrospectedAt: ZonedDateTime,
    val published: Boolean = false,
)
