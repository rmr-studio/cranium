package riven.core.service.user

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import riven.core.entity.user.UserEntity
import riven.core.enums.storage.StorageDomain
import riven.core.exceptions.NotFoundException
import riven.core.models.user.User
import riven.core.projection.user.toWorkspaceMember
import riven.core.repository.user.UserRepository
import riven.core.repository.workspace.WorkspaceRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.storage.StorageService
import riven.core.util.ServiceUtil.findOrThrow
import java.util.*

@Service
class UserService(
    private val repository: UserRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val authTokenService: AuthTokenService,
    private val storageService: StorageService,
    private val logger: KLogger
) {

    /**
     * Retrieve the UserEntity for the currently authenticated session.
     *
     * @return The UserEntity corresponding to the current session's user ID.
     * @throws NotFoundException if no user exists for the session user ID.
     * @throws IllegalArgumentException if an underlying call (repository or auth token service) rejects the input.
     */
    @Throws(NotFoundException::class, IllegalArgumentException::class)
    fun getUserFromSession(): UserEntity {
        return authTokenService.getUserId().let {
            findOrThrow { repository.findById(it) }.apply {
                logger.info { "Retrieved user profile for ID: $it" }
            }
        }
    }

    /**
     * Retrieve the current session user with all workspace memberships in a single optimized query.
     *
     * Uses native query with JOIN to fetch user and workspace memberships,
     * avoiding N+1 query problem that would occur with lazy loading.
     *
     * @return The User model with all workspace memberships populated.
     * @throws NotFoundException if no user exists for the session user ID.
     */
    @Throws(NotFoundException::class)
    fun getUserWithWorkspacesFromSession(): User {
        val userId = authTokenService.getUserId()
        val userEntity = findOrThrow { repository.findById(userId) }

        // Fetch all workspace memberships in a single query with JOIN
        val memberships = repository.findWorkspaceMembershipsByUserId(userId)
            .map { it.toWorkspaceMember() }

        logger.info { "Retrieved user profile with ${memberships.size} workspace memberships for ID: $userId" }

        return userEntity.toModel(memberships)
    }

    /**
     * Retrieve a user by ID with all workspace memberships in a single optimized query.
     *
     * Uses native query with JOIN to fetch user and workspace memberships,
     * avoiding N+1 query problem that would occur with lazy loading.
     *
     * @param userId The UUID of the user to retrieve.
     * @return The User model with all workspace memberships populated.
     * @throws NotFoundException if no user exists with the given ID.
     */
    @Throws(NotFoundException::class)
    fun getUserWithWorkspacesById(userId: UUID): User {
        val userEntity = findOrThrow { repository.findById(userId) }

        // Fetch all workspace memberships in a single query with JOIN
        val memberships = repository.findWorkspaceMembershipsByUserId(userId)
            .map { it.toWorkspaceMember() }

        logger.info { "Retrieved user profile with ${memberships.size} workspace memberships for ID: $userId" }

        return userEntity.toModel(memberships)
    }

    /**
     * Retrieves the user entity for the given user ID.
     *
     * @param id The UUID of the user to retrieve.
     * @return The UserEntity with the specified ID.
     * @throws NotFoundException if no user exists with the given ID.
     */
    @Throws(NotFoundException::class)
    fun getUserById(id: UUID): UserEntity {
        return findOrThrow { repository.findById(id) }
    }

    /**
     * Update the current session user's profile with the provided user details.
     *
     * Validates that the session user ID matches `user.id`, applies the updatable fields to the persisted entity,
     * saves the entity, and returns the updated model.
     *
     * @param user The user model containing updated fields; `user.id` must match the authenticated session user ID.
     * @return The updated `User` model reflecting persisted changes.
     * @throws NotFoundException if no persisted user exists with `user.id`.
     * @throws AccessDeniedException if the session user ID does not match `user.id`.
     * @throws IllegalArgumentException for invalid arguments propagated from repository operations.
     */
    @Throws(NotFoundException::class, IllegalArgumentException::class)
    fun updateUserDetails(user: User, avatar: MultipartFile? = null): User {
        val sessionUserId = authTokenService.getUserId()
        if (sessionUserId != user.id) {
            throw AccessDeniedException("Session user ID does not match provided user ID")
        }

        val entity = findOrThrow { repository.findById(user.id) }.apply {
            name = user.name
            email = user.email
            phone = user.phone
            avatarUrl = user.avatarUrl

            // Update default workspace if provided
            defaultWorkspace = user.defaultWorkspace?.id?.let { workspaceId ->
                findOrThrow { workspaceRepository.findById(workspaceId) }
            }
        }

        avatar?.let { file ->
            entity.avatarUrl = storageService.uploadUserFile(sessionUserId, StorageDomain.AVATAR, file)
        }

        repository.save(entity)
        logger.info { "Updated user profile with ID: ${entity.id}" }
        return entity.toModel()
    }

    /**
     * Deletes the user identified by [userId] and any associated membership records.
     *
     * Ensures the user exists, then removes the user and related membership entities in a single transaction.
     *
     * @param userId The UUID of the user to delete.
     * @throws NotFoundException If no user exists with the given ID.
     */
    @Transactional
    @Throws(NotFoundException::class)
    fun deleteUserProfile(userId: UUID) {
        findOrThrow { repository.findById(userId) } // Ensure the user exists before deletion
        repository.deleteById(userId)
        logger.info { "Deleted user profile with ID: $userId" }
    }
}