package cranium.core.service.workspace

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.repository.workspace.WorkspaceMemberRepository
import cranium.core.repository.workspace.WorkspaceRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.storage.StorageService
import cranium.core.service.user.UserService
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import cranium.core.service.util.factory.WorkspaceFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        WorkspaceServiceConnotationFlagTest.TestConfig::class,
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
class WorkspaceServiceConnotationFlagTest {

    @Configuration
    class TestConfig

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
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockitoBean
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var workspaceService: WorkspaceService

    @Test
    fun `isConnotationEnabled returns true when column is true`() {
        val workspace = WorkspaceFactory.createWorkspace(
            id = workspaceId,
            connotationEnabled = true,
        )
        whenever(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace))

        assertTrue(workspaceService.isConnotationEnabled(workspaceId))
    }

    @Test
    fun `isConnotationEnabled returns false when column is false`() {
        val workspace = WorkspaceFactory.createWorkspace(
            id = workspaceId,
            connotationEnabled = false,
        )
        whenever(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace))

        assertFalse(workspaceService.isConnotationEnabled(workspaceId))
    }
}
