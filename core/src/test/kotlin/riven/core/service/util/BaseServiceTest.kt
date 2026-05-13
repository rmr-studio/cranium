package cranium.core.service.util

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.enums.workspace.WorkspaceRoles
import java.util.*

@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@example.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.ADMIN
        )
    ]
)
abstract class BaseServiceTest {

    companion object {
        val TEST_USER_ID: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
        val TEST_WORKSPACE_ID: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")
    }

    @MockitoBean
    protected lateinit var logger: KLogger

    protected val workspaceId: UUID get() = TEST_WORKSPACE_ID
    protected val userId: UUID get() = TEST_USER_ID
}
