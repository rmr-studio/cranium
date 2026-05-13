package cranium.core.repository.block

import org.springframework.data.jpa.repository.JpaRepository
import cranium.core.entity.block.BlockTypeEntity
import java.util.*

interface BlockTypeRepository : JpaRepository<BlockTypeEntity, UUID> {
    /**
     * Retrieve a BlockTypeEntity by its key.
     *
     * @param key The unique key identifying the block type.
     * @return An Optional containing the matching BlockTypeEntity if found, or an empty Optional otherwise.
     */
    fun findByKey(key: String): Optional<BlockTypeEntity>

    /**
     * Retrieve block types that belong to the specified workspace or are marked as system-wide.
     *
     * @param workspaceId Workspace UUID to filter by; pass `null` to omit workspace-specific filtering.
     * @param system When `true`, include block types that are marked as system-wide.
     * @return `List<BlockTypeEntity>` containing entities that match the workspace ID or have the system flag, possibly empty.
     */
    fun findByworkspaceIdOrSystem(workspaceId: UUID?, system: Boolean = true): List<BlockTypeEntity>

    /**
     * Fetches the latest BlockTypeEntity for the given workspace and key.
     *
     * @param workspaceId UUID of the workspace to match.
     * @param key Identifier of the block type.
     * @return The BlockTypeEntity with the highest version for the workspace and key, or `null` if none exists.
     */
    fun findTopByworkspaceIdAndKeyOrderByVersionDesc(workspaceId: UUID, key: String): Optional<BlockTypeEntity>

    /**
     * Finds a block type matching the specified workspace, key, and version.
     *
     * @param workspaceId The UUID of the workspace to which the block type belongs.
     * @param key The block type's unique key.
     * @param version The exact version number to match.
     * @return An Optional containing the matching BlockTypeEntity if found, or empty otherwise.
     */
    fun findByworkspaceIdAndKeyAndVersion(workspaceId: UUID, key: String, version: Int): Optional<BlockTypeEntity>

    /**
     * Retrieve the latest system-wide BlockTypeEntity for the given key.
     *
     * @param key The block type key to match.
     * @return The most recent system-wide BlockTypeEntity with the given key, or `null` if none exists.
     */
    fun findTopBySystemTrueAndKeyOrderByVersionDesc(key: String): Optional<BlockTypeEntity>

    /**
     * Retrieves a system-wide BlockTypeEntity that matches the specified key and version.
     *
     * @param key The block type key to match.
     * @param version The exact version to match.
     * @return An Optional containing the matching BlockTypeEntity if found, empty otherwise.
     */
    fun findBySystemTrueAndKeyAndVersion(key: String, version: Int): Optional<BlockTypeEntity>
}