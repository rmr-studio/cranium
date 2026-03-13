package riven.core.service.workspace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.workspace.WorkspaceInviteEntity
import riven.core.entity.workspace.WorkspaceMemberEntity
import riven.core.enums.workspace.WorkspaceInviteStatus
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.repository.workspace.WorkspaceInviteRepository
import riven.core.repository.workspace.WorkspaceMemberRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.UserFactory
import riven.core.service.util.factory.WorkspaceFactory
import java.util.*

@SpringBootTest(
    classes = [
        WorkspaceSecurity::class,
        AuthTokenService::class,
        SecurityTestConfig::class,
        WorkspaceInviteService::class,
    ]
)
class WorkspaceInviteServiceTest : BaseServiceTest() {

    private val workspaceId2: UUID = UUID.fromString("e9b1c2d3-4e5f-6789-abcd-ef9876543210")

    @Autowired
    private lateinit var workspaceInviteService: WorkspaceInviteService

    @MockitoBean
    private lateinit var workspaceMemberRepository: WorkspaceMemberRepository

    @MockitoBean
    private lateinit var workspaceInviteRepository: WorkspaceInviteRepository

    @MockitoBean
    private lateinit var workspaceService: WorkspaceService

    @MockitoBean
    private lateinit var activityService: ActivityService

    // ------ createWorkspaceInvitation Tests ------

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.OWNER
            ),
            WorkspaceRole(
                workspaceId = "e9b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.MEMBER
            )
        ]
    )
    fun `createWorkspaceInvitation succeeds with valid permissions`() {
        val targetEmail = "invited@example.com"

        whenever(workspaceMemberRepository.findByWorkspaceId(workspaceId))
            .thenReturn(emptyList())
        whenever(workspaceInviteRepository.findByworkspaceIdAndEmailAndInviteStatus(workspaceId, targetEmail, WorkspaceInviteStatus.PENDING))
            .thenReturn(emptyList())

        val inviteEntity = WorkspaceFactory.createWorkspaceInvite(
            email = targetEmail,
            workspaceId = workspaceId,
            role = WorkspaceRoles.MEMBER,
            invitedBy = userId,
        )
        whenever(workspaceInviteRepository.save(any<WorkspaceInviteEntity>())).thenReturn(inviteEntity)

        workspaceInviteService.createWorkspaceInvitation(workspaceId, targetEmail, WorkspaceRoles.MEMBER)

        verify(workspaceInviteRepository).save(any<WorkspaceInviteEntity>())
        verify(activityService).logActivity(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "e9b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.MEMBER
            )
        ]
    )
    fun `createWorkspaceInvitation fails with insufficient permissions`() {
        assertThrows<AccessDeniedException> {
            workspaceInviteService.createWorkspaceInvitation(workspaceId2, "invited@example.com", WorkspaceRoles.MEMBER)
        }
    }

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    fun `createWorkspaceInvitation rejects OWNER role`() {
        assertThrows<IllegalArgumentException> {
            workspaceInviteService.createWorkspaceInvitation(workspaceId, "invited@example.com", WorkspaceRoles.OWNER)
        }
    }

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    fun `createWorkspaceInvitation rejects if email is already a member`() {
        val targetEmail = "existing@example.com"
        val existingUser = UserFactory.createUser(email = targetEmail)
        val member = WorkspaceFactory.createWorkspaceMember(
            user = existingUser,
            workspaceId = workspaceId,
        )

        whenever(workspaceMemberRepository.findByWorkspaceId(workspaceId))
            .thenReturn(listOf(member))

        assertThrows<ConflictException> {
            workspaceInviteService.createWorkspaceInvitation(workspaceId, targetEmail, WorkspaceRoles.MEMBER)
        }
    }

    // ------ handleInvitationResponse Tests ------

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = []
    )
    fun `handleInvitationResponse accepts invitation successfully`() {
        val token = WorkspaceInviteEntity.generateSecureToken()
        val inviteEntity = WorkspaceFactory.createWorkspaceInvite(
            email = "email@email.com",
            workspaceId = workspaceId,
            role = WorkspaceRoles.MEMBER,
            token = token,
        )

        whenever(workspaceInviteRepository.findByToken(token)).thenReturn(Optional.of(inviteEntity))
        whenever(workspaceInviteRepository.save(any<WorkspaceInviteEntity>())).thenReturn(inviteEntity)

        workspaceInviteService.handleInvitationResponse(token, accepted = true)

        verify(workspaceInviteRepository).save(any<WorkspaceInviteEntity>())
        verify(workspaceService).addMemberToWorkspace(workspaceId, userId, WorkspaceRoles.MEMBER)
    }

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = []
    )
    fun `handleInvitationResponse declines invitation successfully`() {
        val token = WorkspaceInviteEntity.generateSecureToken()
        val inviteEntity = WorkspaceFactory.createWorkspaceInvite(
            email = "email@email.com",
            workspaceId = workspaceId,
            role = WorkspaceRoles.MEMBER,
            token = token,
        )

        whenever(workspaceInviteRepository.findByToken(token)).thenReturn(Optional.of(inviteEntity))
        whenever(workspaceInviteRepository.save(any<WorkspaceInviteEntity>())).thenReturn(inviteEntity)

        workspaceInviteService.handleInvitationResponse(token, accepted = false)

        verify(workspaceInviteRepository).save(any<WorkspaceInviteEntity>())
        verify(workspaceService, never()).addMemberToWorkspace(any(), any(), any())
    }

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = []
    )
    fun `handleInvitationResponse rejects when email does not match`() {
        val token = WorkspaceInviteEntity.generateSecureToken()
        val inviteEntity = WorkspaceFactory.createWorkspaceInvite(
            email = "other@example.com",
            workspaceId = workspaceId,
            role = WorkspaceRoles.MEMBER,
            token = token,
        )

        whenever(workspaceInviteRepository.findByToken(token)).thenReturn(Optional.of(inviteEntity))

        assertThrows<AccessDeniedException> {
            workspaceInviteService.handleInvitationResponse(token, accepted = true)
        }
    }

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = []
    )
    fun `handleInvitationResponse rejects non-pending invitation`() {
        val token = WorkspaceInviteEntity.generateSecureToken()
        val inviteEntity = WorkspaceFactory.createWorkspaceInvite(
            email = "email@email.com",
            workspaceId = workspaceId,
            role = WorkspaceRoles.MEMBER,
            token = token,
            status = WorkspaceInviteStatus.EXPIRED,
        )

        whenever(workspaceInviteRepository.findByToken(token)).thenReturn(Optional.of(inviteEntity))

        assertThrows<IllegalArgumentException> {
            workspaceInviteService.handleInvitationResponse(token, accepted = true)
        }
    }

    // ------ revokeWorkspaceInvite Tests ------

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    fun `revokeWorkspaceInvite rejects non-pending invitation`() {
        val inviteEntity = WorkspaceFactory.createWorkspaceInvite(
            email = "invited@example.com",
            workspaceId = workspaceId,
            role = WorkspaceRoles.MEMBER,
            status = WorkspaceInviteStatus.ACCEPTED,
        )
        val inviteId = requireNotNull(inviteEntity.id)

        whenever(workspaceInviteRepository.findById(inviteId)).thenReturn(Optional.of(inviteEntity))

        assertThrows<IllegalArgumentException> {
            workspaceInviteService.revokeWorkspaceInvite(workspaceId, inviteId)
        }
    }

    @Test
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "email@email.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.MEMBER
            )
        ]
    )
    fun `revokeWorkspaceInvite rejects with insufficient permissions`() {
        val inviteEntity = WorkspaceFactory.createWorkspaceInvite(
            email = "invited@example.com",
            workspaceId = workspaceId,
            role = WorkspaceRoles.MEMBER,
            status = WorkspaceInviteStatus.PENDING,
        )
        val inviteId = requireNotNull(inviteEntity.id)

        whenever(workspaceInviteRepository.findById(inviteId)).thenReturn(Optional.of(inviteEntity))

        assertThrows<AccessDeniedException> {
            workspaceInviteService.revokeWorkspaceInvite(workspaceId, inviteId)
        }
    }

    // ------ createWorkspaceInvitationInternal Tests ------

    @Test
    fun `createWorkspaceInvitationInternal creates invite without PreAuthorize`() {
        val targetEmail = "internal@example.com"
        val invitedBy = UUID.randomUUID()

        whenever(workspaceMemberRepository.findByWorkspaceId(workspaceId))
            .thenReturn(emptyList())
        whenever(workspaceInviteRepository.findByworkspaceIdAndEmailAndInviteStatus(workspaceId, targetEmail, WorkspaceInviteStatus.PENDING))
            .thenReturn(emptyList())

        val inviteEntity = WorkspaceFactory.createWorkspaceInvite(
            email = targetEmail,
            workspaceId = workspaceId,
            role = WorkspaceRoles.MEMBER,
            invitedBy = invitedBy,
        )
        whenever(workspaceInviteRepository.save(any<WorkspaceInviteEntity>())).thenReturn(inviteEntity)

        val result = workspaceInviteService.createWorkspaceInvitationInternal(workspaceId, targetEmail, WorkspaceRoles.MEMBER, invitedBy)

        assertEquals(targetEmail, result.email)
        verify(workspaceInviteRepository).save(any<WorkspaceInviteEntity>())
    }
}
