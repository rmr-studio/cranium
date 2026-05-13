package cranium.core.enums.workspace

enum class WorkspaceRoles(val authority: Int) {
    OWNER(3),
    ADMIN(2),
    MEMBER(1);

    companion object {
        fun fromString(role: String): WorkspaceRoles {
            return entries.find { it.name.equals(role, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid role: $role")
        }

        fun fromAuthority(authority: Int): WorkspaceRoles? {
            return entries.find { it.authority == authority }
        }
    }

    fun hasHigherAuthorityThan(other: WorkspaceRoles, inclusive: Boolean = false): Boolean {
        inclusive.let {
            if (it) {
                return this.authority >= other.authority
            }

            return this.authority > other.authority
        }
    }
}

