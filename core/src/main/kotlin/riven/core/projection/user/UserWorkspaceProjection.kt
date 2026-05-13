package cranium.core.projection.user

import cranium.core.entity.user.UserEntity

interface UserWorkspaceProjection {
    fun getUserEntity(): UserEntity
    fun getWorkspaceMemberships(): Set<UserWorkspaceMembershipProjection>
}