package cranium.core.service.util.factory

import cranium.core.entity.connector.DataConnectorTableMappingEntity
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup
import java.time.ZonedDateTime
import java.util.UUID

/** Test factory for [DataConnectorTableMappingEntity] (Phase 3). */
object DataConnectorTableMappingEntityFactory {

    fun create(
        workspaceId: UUID = UUID.randomUUID(),
        connectionId: UUID = UUID.randomUUID(),
        tableName: String = "customers",
        lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,
        semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,
        entityTypeId: UUID? = null,
        schemaHash: String = "hash-${UUID.randomUUID()}",
        lastIntrospectedAt: ZonedDateTime = ZonedDateTime.now(),
        published: Boolean = false,
    ): DataConnectorTableMappingEntity = DataConnectorTableMappingEntity(
        workspaceId = workspaceId,
        connectionId = connectionId,
        tableName = tableName,
        lifecycleDomain = lifecycleDomain,
        semanticGroup = semanticGroup,
        entityTypeId = entityTypeId,
        schemaHash = schemaHash,
        lastIntrospectedAt = lastIntrospectedAt,
        published = published,
    )
}
