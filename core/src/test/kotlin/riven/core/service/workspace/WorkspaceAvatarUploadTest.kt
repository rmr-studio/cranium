package cranium.core.service.workspace

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.multipart.MultipartFile
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.workspace.WorkspaceEntity
import cranium.core.entity.workspace.WorkspaceMemberEntity
import cranium.core.enums.workspace.WorkspacePlan
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.models.request.workspace.SaveWorkspaceRequest
import cranium.core.models.user.User
import cranium.core.repository.workspace.WorkspaceMemberRepository
import cranium.core.repository.workspace.WorkspaceRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.storage.StorageService
import cranium.core.service.user.UserService
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        WorkspaceAvatarUploadTest.TestConfig::class,
        WorkspaceService::class
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.OWNER
        )
    ]
)
class WorkspaceAvatarUploadTest {

    @Configuration
    class TestConfig

    private val userId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")

    @MockitoBean
    private lateinit var workspaceRepository: WorkspaceRepository

    @MockitoBean
    private lateinit var workspaceMemberRepository: WorkspaceMemberRepository

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var logger: KLogger

    @MockitoBean
    private lateinit var authTokenService: AuthTokenService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockitoBean
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var workspaceService: WorkspaceService

    @Test
    fun `saveWorkspace uploads avatar and sets storageKey on entity`() {
        val storageKey = "$workspaceId/avatar/test-avatar.png"
        val savedEntity = WorkspaceEntity(
            id = workspaceId,
            name = "Test Workspace",
            plan = WorkspacePlan.FREE,
        )

        val mockFile = mock<MultipartFile>()

        whenever(authTokenService.getUserId()).thenReturn(userId)
        whenever(workspaceRepository.save(any<WorkspaceEntity>())).thenReturn(savedEntity)
        whenever(workspaceMemberRepository.save(any<WorkspaceMemberEntity>())).thenReturn(mock())
        whenever(storageService.updateWorkspaceAvatar(eq(workspaceId), eq(mockFile), isNull()))
            .thenReturn(storageKey)
        whenever(userService.getUserWithWorkspacesFromSession()).thenReturn(
            User(id = userId, email = "test@test.com", name = "Test User")
        )

        val request = SaveWorkspaceRequest(
            name = "Test Workspace",
            plan = WorkspacePlan.FREE,
            defaultCurrency = "AUD"
        )

        workspaceService.saveWorkspace(request, avatar = mockFile)

        verify(storageService).updateWorkspaceAvatar(eq(workspaceId), eq(mockFile), isNull())
        // Workspace is saved twice: once for initial create, once after avatar upload
        verify(workspaceRepository, times(2)).save(any<WorkspaceEntity>())
    }

    @Test
    fun `saveWorkspace does not upload when no avatar provided`() {
        val savedEntity = WorkspaceEntity(
            id = workspaceId,
            name = "Test Workspace",
            plan = WorkspacePlan.FREE,
        )

        whenever(authTokenService.getUserId()).thenReturn(userId)
        whenever(workspaceRepository.save(any<WorkspaceEntity>())).thenReturn(savedEntity)
        whenever(workspaceMemberRepository.save(any<WorkspaceMemberEntity>())).thenReturn(mock())
        whenever(userService.getUserWithWorkspacesFromSession()).thenReturn(
            User(id = userId, email = "test@test.com", name = "Test User")
        )

        val request = SaveWorkspaceRequest(
            name = "Test Workspace",
            plan = WorkspacePlan.FREE,
            defaultCurrency = "AUD"
        )

        workspaceService.saveWorkspace(request, avatar = null)

        verify(storageService, never()).updateWorkspaceAvatar(any(), any(), anyOrNull())
        // Workspace saved only once for initial create
        verify(workspaceRepository, times(1)).save(any<WorkspaceEntity>())
    }

    @Test
    fun `saveWorkspace on update uploads avatar and sets storageKey`() {
        val storageKey = "$workspaceId/avatar/updated-avatar.png"
        val existingEntity = WorkspaceEntity(
            id = workspaceId,
            name = "Old Name",
            plan = WorkspacePlan.FREE,
        )

        val mockFile = mock<MultipartFile>()

        whenever(authTokenService.getUserId()).thenReturn(userId)
        whenever(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(existingEntity))
        whenever(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(mock<WorkspaceMemberEntity>()))
        whenever(workspaceRepository.save(any<WorkspaceEntity>())).thenReturn(existingEntity)
        whenever(storageService.updateWorkspaceAvatar(eq(workspaceId), eq(mockFile), isNull()))
            .thenReturn(storageKey)
        whenever(userService.getUserWithWorkspacesFromSession()).thenReturn(
            User(id = userId, email = "test@test.com", name = "Test User", memberships = listOf())
        )

        val request = SaveWorkspaceRequest(
            id = workspaceId,
            name = "Updated Workspace",
            plan = WorkspacePlan.FREE,
            defaultCurrency = "AUD"
        )

        workspaceService.saveWorkspace(request, avatar = mockFile)

        verify(storageService).updateWorkspaceAvatar(eq(workspaceId), eq(mockFile), isNull())
        // Workspace saved twice: once for update, once after avatar upload
        verify(workspaceRepository, times(2)).save(any<WorkspaceEntity>())
    }
}
