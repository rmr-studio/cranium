package cranium.core.service.block

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.block.BlockTypeEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.block.node.SystemBlockTypes
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.models.block.BlockType
import cranium.core.models.request.block.CreateBlockTypeRequest
import cranium.core.repository.block.BlockTypeRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.activity.log
import cranium.core.service.auth.AuthTokenService
import cranium.core.util.ServiceUtil.findManyResults
import cranium.core.util.ServiceUtil.findOrThrow
import java.util.*

/**
 * Service layer for handling the management of block types and templates within the application.
 */
@Service
class BlockTypeService(
    private val blockTypeRepository: BlockTypeRepository,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val workspaceSecurity: WorkspaceSecurity,
) {
    /**
     * Creates and publishes a new block type from the provided request and records an audit activity.
     *
     * @param request Data for the new block type.
     * @return The saved BlockType model representing the published block type.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#request.workspaceId)")
    fun publishBlockType(request: CreateBlockTypeRequest): BlockType {
        authTokenService.getUserId().let { userId ->
            val entity = BlockTypeEntity.fromRequest(request)
            blockTypeRepository.save(entity).run {
                activityService.log(
                    activity = Activity.BLOCK_TYPE,
                    operation = OperationType.CREATE,
                    userId = userId,
                    workspaceId = request.workspaceId,
                    entityType = ApplicationEntityType.BLOCK_TYPE,
                    entityId = this.id,
                    "type" to this.key,
                    "version" to this.version
                )

                return this.toModel()
            }
        }
    }

    /**
     * Create a new versioned BlockType row derived from the provided BlockType and record a creation activity.
     *
     * This performs append-only versioning: a new entity is saved with an incremented version, sourceId set to the original entity's id, and deleted set to false. Assumes a unique constraint on (workspace_id, key, version).
     *
     * @param type The BlockType containing the new values and the id of the existing version to fork from.
     */

    fun updateBlockType(type: BlockType) {
        val userId = authTokenService.getUserId()
        val existing = findOrThrow { blockTypeRepository.findById(type.id) }

        // Ensure a user is only updating a non-system workspace block type
        val orgId = requireNotNull(existing.workspaceId) { "Cannot update system block type" }

        // Assert that they have access to said workspace
        if (!workspaceSecurity.hasWorkspace(orgId)) {
            throw AccessDeniedException("Unauthorized to update block type for workspace $orgId")
        }

        // compute next version number (could also query max)
        val nextVersion = existing.version + 1

        val newRow = BlockTypeEntity(
            id = null,
            key = existing.key,
            displayName = type.name,
            description = type.description,
            workspaceId = existing.workspaceId,
            system = existing.system,
            version = nextVersion,
            strictness = type.strictness,
            schema = type.schema,
            displayStructure = type.display,
            sourceId = existing.id
        )

        blockTypeRepository.save(newRow).run {
            activityService.log(
                activity = Activity.BLOCK_TYPE,
                operation = OperationType.CREATE,
                userId = userId,
                workspaceId = orgId,
                entityType = ApplicationEntityType.BLOCK_TYPE,
                entityId = this.id,
                "type" to this.key,
                "version" to this.version,
                "sourceVersion" to existing.version
            )
        }
    }

    fun getSystemBlockType(type: SystemBlockTypes): BlockTypeEntity {
        return findOrThrow {
            blockTypeRepository.findByKey(type.key)
        }
    }

    /**
     * Retrieves the block type entity with the specified unique key.
     *
     * Throws if no block type with the given key exists.
     *
     * @param key The unique key identifying the block type.
     * @return The matching BlockTypeEntity.
     */
    fun getEntityByKey(key: String): BlockTypeEntity {
        return findOrThrow { blockTypeRepository.findByKey(key) }
    }

    /**
     * Fetches block types for the given workspace, optionally including system block types.
     *
     * @param includeSystemResults When `true`, include pre-defined system block types in the result.
     * @return A list of block types for the workspace; includes system block types when `includeSystemResults` is `true`.
     */
    fun getBlockTypes(workspaceId: UUID, includeSystemResults: Boolean = true): List<BlockType> {
        return findManyResults {
            blockTypeRepository.findByworkspaceIdOrSystem(
                workspaceId,
                includeSystemResults
            )
        }.map { it.toModel() }
    }

    /**
     * Retrieve a block type entity by key, preferring an workspace-scoped version and falling back to a system-scoped version.
     *
     * @param key The unique key of the block type.
     * @param workspaceId The workspace UUID to prefer when searching; if null or no workspace-scoped match is found, a system-scoped block type is used.
     * @param version Optional specific version to fetch; when null the latest version is returned.
     * @return The matching BlockTypeEntity.
     * @throws NoSuchElementException If no matching block type is found for the given parameters.
     */
    fun getByKey(key: String, workspaceId: UUID?, version: Int?): BlockTypeEntity {
        // Find from Workspace
        if (workspaceId != null) {
            if (version != null) {
                findOrThrow {
                    blockTypeRepository.findByworkspaceIdAndKeyAndVersion(
                        workspaceId,
                        key,
                        version
                    )
                }.let { return it }
            } else {
                blockTypeRepository.findTopByworkspaceIdAndKeyOrderByVersionDesc(
                    workspaceId,
                    key
                ).orElse(null)?.let { return it }
            }
        }
        // Fetch from system
        return if (version != null) {
            findOrThrow {
                blockTypeRepository.findBySystemTrueAndKeyAndVersion(
                    key,
                    version
                )
            }
        } else {
            blockTypeRepository.findTopBySystemTrueAndKeyOrderByVersionDesc(key)
                .orElseThrow { NoSuchElementException("No BlockType found for key '$key'") }

        }
    }

    /**
     * Retrieve a block type entity by its id.
     *
     * @param id The UUID of the block type to retrieve.
     * @return The `BlockTypeEntity` matching the given id.
     */
    fun getById(id: UUID): BlockTypeEntity {
        return findOrThrow { blockTypeRepository.findById(id) }
    }

}