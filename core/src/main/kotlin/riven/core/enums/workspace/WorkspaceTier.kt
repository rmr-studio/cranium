package cranium.core.enums.workspace

/**
 * Workspace tier with associated resource limits.
 *
 * Defines maximum concurrent workflow executions per workspace based on tier.
 * Tiers map directly from WorkspacePlan - each plan has exactly one tier.
 *
 * @property maxConcurrentWorkflows Maximum simultaneous workflow executions
 * @property displayName Human-readable tier name
 */
enum class WorkspaceTier(
    val maxConcurrentWorkflows: Int,
    val displayName: String
) {
    FREE(1, "Free"),
    STARTUP(3, "Startup"),
    SCALE(5, "Scale"),
    ENTERPRISE(10, "Enterprise");

    companion object {
        /**
         * Get tier for a workspace plan.
         *
         * @param plan WorkspacePlan to convert
         * @return Corresponding WorkspaceTier
         */
        fun fromPlan(plan: WorkspacePlan): WorkspaceTier = when (plan) {
            WorkspacePlan.FREE -> FREE
            WorkspacePlan.STARTUP -> STARTUP
            WorkspacePlan.SCALE -> SCALE
            WorkspacePlan.ENTERPRISE -> ENTERPRISE
        }

        val DEFAULT = FREE
    }
}
