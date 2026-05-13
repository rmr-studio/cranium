package cranium.core.controller.user

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import cranium.core.models.request.user.SaveUserRequest
import cranium.core.models.user.User
import cranium.core.service.user.UserService
import java.util.*

@RestController
@RequestMapping("/api/v1/user")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "user")
class UserController(
    private val profileService: UserService
) {

    @GetMapping("/")
    @Operation(
        summary = "Get current user's profile",
        description = "Retrieves the profile of the authenticated user based on the current session."
    )

    @ApiResponses(
        ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    fun getCurrentUser(): ResponseEntity<User> {
        val user: User = profileService.getUserWithWorkspacesFromSession()
        return ResponseEntity.ok(user)
    }

    @GetMapping("/{userId}")
    @Operation(
        summary = "Get a user by ID",
        description = "Retrieves a specific user's profile by their ID, if the user has access."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "403", description = "User does not have permission to access this profile"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    fun getUserById(@PathVariable userId: UUID): ResponseEntity<User> {
        val currentUserId = profileService.getUserFromSession().id
        if (currentUserId != userId) {
            return ResponseEntity.status(403).build()
        }
        val userProfile: User = profileService.getUserById(userId).toModel()
        return ResponseEntity.ok(userProfile)
    }

    @PutMapping("/", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Update current user's profile",
        description = "Updates the profile of the authenticated user based on the provided data. Optionally accepts an avatar file upload."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "User profile updated successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "400", description = "Invalid request data")
    )
    @SecurityRequirement(name = "bearerAuth")
    fun updateUserProfile(
        @RequestPart("user") request: SaveUserRequest,
        @RequestPart("avatar", required = false) avatar: MultipartFile? = null
    ): ResponseEntity<User> {
        val updatedUserProfile = profileService.updateUserDetails(request, avatar)
        return ResponseEntity.ok(updatedUserProfile)
    }

    @DeleteMapping("/{userId}")
    @Operation(
        summary = "Delete a user profile by ID",
        description = "Deletes a user profile with the specified ID, if the user has access."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "User profile deleted successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "403", description = "User does not have permission to delete this profile"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    fun deleteUserProfileById(@PathVariable userId: UUID): ResponseEntity<Void> {
        val currentUserId = profileService.getUserFromSession().id
        if (currentUserId != userId) {
            return ResponseEntity.status(403).build()
        }
        profileService.deleteUserProfile(userId)
        return ResponseEntity.noContent().build()
    }
}