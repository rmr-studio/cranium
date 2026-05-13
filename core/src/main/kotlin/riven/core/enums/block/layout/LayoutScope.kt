package cranium.core.enums.block.layout

/**
 * Defines the scope/ownership of a block layout
 */
enum class LayoutScope {
    /**
     * Default layout for the entire workspace.
     * Used as fallback when no user-specific layout exists.
     */
    WORKSPACE,

    /**
     * User-specific personalized layout.
     * Overrides workspace default for that specific user.
     */
    USER,

    /**
     * Shared team layout.
     * Can be applied to specific teams/groups.
     */
    TEAM
}