package cranium.core.enums.block.node

import com.fasterxml.jackson.annotation.JsonValue

/**
 * Enum representing system-defined block types.
 * These block types should be seeded into the database. And accessible via the static keys provided
 */
enum class SystemBlockTypes(@JsonValue val key: String) {
    LAYOUT_CONTAINER("layout_container"),
    BLOCK_LIST("block_list"),
    BLOCK_REFERENCE("block_reference"),
    ENTITY_REFERENCE("entity_reference"),
    PROJECT_OVERVIEW("project_overview"),
    NOTE("note"),
    POSTAL_ADDRESS("postal_address"),
    PROJECT_TASKS("project_tasks"),
}