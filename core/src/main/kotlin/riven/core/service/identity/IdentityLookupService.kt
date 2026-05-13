package cranium.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import cranium.core.entity.entity.EntityEntity
import cranium.core.repository.entity.EntityAttributeRepository
import cranium.core.repository.entity.EntityRepository
import java.util.UUID

/**
 * Provides entity lookup operations for the identity resolution domain.
 *
 * Two lookup strategies:
 * - By source external ID: finds entities via the `source_external_id` column on the entities table.
 * - By identifier attribute value: finds entities whose identifier attribute JSONB value matches a given string.
 *
 * Both strategies are workspace-scoped and secured via @PreAuthorize.
 */
@Service
class IdentityLookupService(
    private val entityRepository: EntityRepository,
    private val entityAttributeRepository: EntityAttributeRepository,
    private val logger: KLogger,
) {

    // ------ Public read operations ------

    /**
     * Finds entities by their integration-sourced external ID within a workspace.
     *
     * @param workspaceId The workspace to search in.
     * @param sourceExternalId The external ID from the integration source system.
     * @return List of matching entities (may be empty if no match).
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun findBySourceExternalId(workspaceId: UUID, sourceExternalId: String): List<EntityEntity> {
        logger.debug { "Looking up entities by sourceExternalId=$sourceExternalId in workspace=$workspaceId" }
        return entityRepository.findByWorkspaceIdAndSourceExternalId(workspaceId, sourceExternalId)
    }

    /**
     * Finds entity IDs by matching an identifier attribute's text value within a workspace.
     *
     * Queries the entity_attributes table using a native JSONB text extraction to find rows
     * where the attribute's value matches the given string exactly.
     *
     * @param workspaceId The workspace to search in.
     * @param attributeId The attribute definition ID (identifier key) to match against.
     * @param value The text value to search for.
     * @return List of entity IDs that have a matching identifier attribute value.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun findByIdentifierValue(workspaceId: UUID, attributeId: UUID, value: String): List<UUID> {
        logger.debug { "Looking up entities by identifierValue=$value, attributeId=$attributeId in workspace=$workspaceId" }
        return entityAttributeRepository.findByWorkspaceIdAndAttributeIdAndTextValue(workspaceId, attributeId, value)
            .map { it.entityId }
            .distinct()
    }
}
