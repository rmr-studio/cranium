//package cranium.core.service.workspace
//
//import io.github.oshai.kotlinlogging.KLogger
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.assertThrows
//import org.mockito.Mockito
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.boot.test.context.SpringBootTest
//import org.springframework.context.annotation.Configuration
//import org.springframework.context.annotation.Import
//import org.springframework.security.access.AccessDeniedException
//import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
//import org.springframework.test.context.bean.override.mockito.MockitoBean
//import cranium.core.configuration.auth.WorkspaceSecurity
//import cranium.core.entity.user.UserEntity
//import cranium.core.entity.workspace.WorkspaceEntity
//import cranium.core.entity.workspace.WorkspaceMemberEntity
//import cranium.core.entity.workspace.toModel
//import cranium.core.enums.workspace.WorkspaceRoles
//import cranium.core.models.workspace.Workspace
//import cranium.core.models.workspace.WorkspaceMember
//import cranium.core.repository.workspace.WorkspaceMemberRepository
//import cranium.core.repository.workspace.WorkspaceRepository
//import cranium.core.service.activity.ActivityService
//import cranium.core.service.auth.AuthTokenService
//import cranium.core.service.user.UserService
//import cranium.core.service.util.WithUserPersona
//import cranium.core.service.util.WorkspaceRole
//import cranium.core.service.util.factory.UserFactory
//import cranium.core.service.util.factory.WorkspaceFactory
//import java.util.*
//
//@SpringBootTest(classes = [AuthTokenService::class, WorkspaceSecurity::class, WorkspaceServiceTest.TestConfig::class, WorkspaceService::class])
//@WithUserPersona(
//    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
//    email = "email@email.com",
//    displayName = "Jared Tucker",
//    roles = [
//        WorkspaceRole(
//            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
//            role = WorkspaceRoles.OWNER
//        ),
//        WorkspaceRole(
//            workspaceId = "e9b1c2d3-4e5f-6789-abcd-ef9876543210",
//            role = WorkspaceRoles.MEMBER
//        )
//    ]
//)
//class WorkspaceServiceTest {
//
//    @Configuration
//    @EnableMethodSecurity(prePostEnabled = true)
//    @Import(WorkspaceSecurity::class)
//    class TestConfig
//
//
//    private val userId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
//
//    // Two Workspace Ids that belong to the user
//    private val workspaceId1: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")
//    private val workspaceId2: UUID = UUID.fromString("e9b1c2d3-4e5f-6789-abcd-ef9876543210")
//
//    // Workspace Id to test access control with an org a user is not apart of
//    private val workspaceId3 = UUID.fromString("d8b1c2d3-4e5f-6789-abcd-ef9876543210")
//
//    @MockitoBean
//    private lateinit var workspaceRepository: WorkspaceRepository
//
//    @MockitoBean
//    private lateinit var workspaceMemberRepository: WorkspaceMemberRepository
//
//    @MockitoBean
//    private lateinit var userService: UserService
//
//    @MockitoBean
//    private lateinit var logger: KLogger
//
//    @MockitoBean
//    private lateinit var activityService: ActivityService
//
//    @Autowired
//    private lateinit var workspaceService: WorkspaceService
//
//    @Test
//    fun `handle workspace fetch with appropriate permissions`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            id = workspaceId1,
//            name = "Test Workspace",
//        )
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId1)).thenReturn(Optional.of(entity))
//        val workspace = workspaceService.getWorkspaceById(workspaceId1)
//        assert(workspace.id == workspaceId1)
//
//    }
//
//    @Test
//    fun `handle workspace fetch without required workspace`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            // This is the workspace the user does not have access to
//            id = workspaceId3,
//            name = "Test Workspace 3",
//        )
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId3)).thenReturn(Optional.of(entity))
//
//        assertThrows<AccessDeniedException> {
//            workspaceService.getWorkspaceById(workspaceId3)
//        }
//    }
//
//    @Test
//    fun `handle workspace invocation without required permission`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            // This is the workspace the user is not the owner of
//            id = workspaceId2,
//            name = "Test Workspace 2",
//        )
//
//        val updatedEntityRequest: Workspace = entity.let {
//            it.copy().apply {
//                name = "Updated Workspace Name"
//            }
//        }.toModel()
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId2)).thenReturn(Optional.of(entity))
//        // Assert user can fetch the workspace given org roles
//        workspaceService.getWorkspaceById(workspaceId2).run {
//            assert(id == workspaceId2) { "Workspace ID does not match expected ID" }
//            assert(name == "Test Workspace 2") { "Workspace name does not match expected name" }
//        }
//
//        // Assert user cannot update workspace given lack of `Admin` privileges
//        assertThrows<AccessDeniedException> {
//            workspaceService.updateWorkspace(updatedEntityRequest)
//        }
//
//        // Assert user cannot delete workspace given lack of `Owner` privileges
//        assertThrows<AccessDeniedException> {
//            workspaceService.deleteWorkspace(workspaceId2)
//        }
//    }
//
//    @Test
//    fun `handle workspace invocation with required permissions`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            // This is the workspace the user is the owner of
//            id = workspaceId1,
//            name = "Test Workspace 1",
//        )
//
//        val updatedEntityRequest: Workspace = entity.let {
//            it.copy().apply {
//                name = "Updated Workspace Name"
//            }
//        }.toModel()
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId1)).thenReturn(Optional.of(entity))
//        Mockito.`when`(workspaceRepository.save(Mockito.any(WorkspaceEntity::class.java)))
//            .thenReturn(entity)
//
//        Mockito.doNothing()
//            .`when`(workspaceMemberRepository)
//            .deleteById(Mockito.any())
//        // Assert user can fetch the workspace given org roles
//        workspaceService.getWorkspaceById(workspaceId1).run {
//            assert(id == workspaceId1) { "Workspace ID does not match expected ID" }
//            assert(name == "Test Workspace 1") { "Workspace name does not match expected name" }
//        }
//
//        // Assert user can update workspace given `Admin` privileges
//        workspaceService.updateWorkspace(updatedEntityRequest).run {
//            assert(id == workspaceId1) { "Updated Workspace ID does not match expected ID" }
//            assert(name == "Updated Workspace Name") { "Updated Workspace name does not match expected name" }
//        }
//
//        // Assert user can delete workspace given `Owner` privileges
//        workspaceService.deleteWorkspace(workspaceId1)
//        // Verify the delete was called
//        Mockito.verify(workspaceRepository).delete(Mockito.any<WorkspaceEntity>())
//    }
//
//    @Test
//    @WithUserPersona(
//        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
//        email = "email@email.com",
//        displayName = "Jared Tucker",
//        roles = [
//            WorkspaceRole(
//                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
//                role = WorkspaceRoles.ADMIN
//            )
//        ]
//    )
//    fun `handle self removal from workspace`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            id = workspaceId1,
//            name = "Test Workspace",
//        )
//
//        val user: UserEntity = UserFactory.createUser(
//            id = userId,
//        )
//
//        val key = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = userId
//        )
//
//        val member: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user,
//            role = WorkspaceRoles.ADMIN
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key)).thenReturn(Optional.of(this))
//            }
//
//            it.toModel()
//        }
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId1)).thenReturn(Optional.of(entity))
//
//
//        // Assert user is able to remove themselves from the workspace, given the user id in the body matches the JWT
//        workspaceService.removeMemberFromWorkspace(workspaceId1, member).run {
//            assert(true) { "Self-removal from workspace should not throw an exception" }
//        }
//
//        // Verify that the member repository was called to remove the user
//        Mockito.verify(workspaceMemberRepository).deleteById(Mockito.any())
//    }
//
//    @Test
//    fun `handle rejecting removal of member who has ownership permissions`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            id = workspaceId1,
//            name = "Test Workspace",
//        )
//
//        val user: UserEntity = UserFactory.createUser(
//            id = userId,
//        )
//
//        val key = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = userId
//        )
//
//        val member: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user,
//            role = WorkspaceRoles.OWNER
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key)).thenReturn(Optional.of(this))
//            }
//
//            it.toModel()
//        }
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId1)).thenReturn(Optional.of(entity))
//        assertThrows<IllegalArgumentException> {
//            workspaceService.removeMemberFromWorkspace(workspaceId1, member)
//        }.apply {
//            assert(message == "Cannot remove the owner of the workspace. Please transfer ownership first.") {
//                "Exception message does not match expected message"
//            }
//        }
//    }
//
//    @Test
//    fun `handle member removal invocation with correct permissions`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            id = workspaceId1,
//            name = "Test Workspace",
//        )
//
//        val targetUserId = UUID.randomUUID()
//
//        val user: UserEntity = UserFactory.createUser(
//            // Different user ID to test member removal
//            id = targetUserId,
//        )
//
//        val key = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = targetUserId
//        )
//
//        val member: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user,
//            role = WorkspaceRoles.MEMBER
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key)).thenReturn(Optional.of(this))
//            }
//
//            it.toModel()
//        }
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId1)).thenReturn(Optional.of(entity))
//
//        // Assert user is able to remove a member from the workspace, given the user has `Admin` privileges
//        workspaceService.removeMemberFromWorkspace(workspaceId1, member).run {
//            assert(true) { "Member removal from workspace should not throw an exception" }
//        }
//
//        // Verify that the member repository was called to remove the user
//        Mockito.verify(workspaceMemberRepository).deleteById(Mockito.any())
//    }
//
//    @Test
//    @WithUserPersona(
//        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
//        email = "email@email.com",
//        displayName = "Jared Tucker",
//        roles = [
//            WorkspaceRole(
//                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
//                role = WorkspaceRoles.ADMIN
//            )
//        ]
//    )
//    fun `handle member removal invocation with incorrect permissions`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            id = workspaceId1,
//            name = "Test Workspace",
//        )
//
//        val targetUserId = UUID.randomUUID()
//
//        val user: UserEntity = UserFactory.createUser(
//            // Different user ID to test member removal
//            id = targetUserId,
//        )
//
//        val key = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = targetUserId
//        )
//
//        val member: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user,
//            role = WorkspaceRoles.ADMIN
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key)).thenReturn(Optional.of(this))
//            }
//            it.toModel()
//        }
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId1)).thenReturn(Optional.of(entity))
//
//        assertThrows<AccessDeniedException> {
//            // Assert user is not able to remove a member from the workspace, given the user does not have `Admin` privileges
//            workspaceService.removeMemberFromWorkspace(workspaceId1, member)
//        }
//    }
//
//    @Test
//    @WithUserPersona(
//        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
//        email = "email@email.com",
//        displayName = "Jared Tucker",
//        roles = [
//            WorkspaceRole(
//                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
//                role = WorkspaceRoles.MEMBER
//            )
//        ]
//    )
//    fun `handle member role update with incorrect positions`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            id = workspaceId1,
//            name = "Test Workspace",
//        )
//
//        val targetUserId = UUID.randomUUID()
//
//        val user: UserEntity = UserFactory.createUser(
//            // Different user ID to test member removal
//            id = targetUserId,
//        )
//
//        val key = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = targetUserId
//        )
//
//        val member: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user,
//            role = WorkspaceRoles.MEMBER
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key)).thenReturn(Optional.of(this))
//            }
//
//            it.toModel()
//        }
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId1)).thenReturn(Optional.of(entity))
//
//        assertThrows<AccessDeniedException> {
//            // Assert user is not able to remove a member from the workspace, given the user does not have `Admin` privileges
//            workspaceService.updateMemberRole(workspaceId1, member, WorkspaceRoles.OWNER)
//        }
//    }
//
//    @Test
//    @WithUserPersona(
//        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
//        email = "email@email.com",
//        displayName = "Jared Tucker",
//        roles = [
//            WorkspaceRole(
//                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
//                role = WorkspaceRoles.ADMIN
//            )
//        ]
//    )
//    fun `handle member role update as an admin`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            id = workspaceId1,
//            name = "Test Workspace",
//        )
//
//        val targetUserId = UUID.randomUUID()
//        val targetUser2Id = UUID.randomUUID()
//        val targetUser3Id = UUID.randomUUID()
//
//        val user1: UserEntity = UserFactory.createUser(
//            // Different user ID to test member removal
//            id = targetUserId,
//        )
//
//        val user2: UserEntity = UserFactory.createUser(
//            // Different user ID to test member removal
//            id = targetUser2Id,
//        )
//
//        val user3: UserEntity = UserFactory.createUser(
//            // Different user ID to test member removal
//            id = targetUser3Id,
//        )
//
//        val key1 = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = targetUserId
//        )
//
//        val key2 = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = targetUser2Id
//        )
//
//        val key3 = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = targetUser3Id
//        )
//
//        val memberDeveloper: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user1,
//            role = WorkspaceRoles.MEMBER
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key1)).thenReturn(Optional.of(this))
//            }
//
//            it.toModel()
//        }
//
//        val memberAdmin: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user2,
//            role = WorkspaceRoles.ADMIN
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key2)).thenReturn(Optional.of(this))
//            }
//
//            it.toModel()
//        }
//
//
//        val memberOwner: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user3,
//            role = WorkspaceRoles.OWNER
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key3)).thenReturn(Optional.of(this))
//            }
//
//            it.toModel()
//        }
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId1)).thenReturn(Optional.of(entity))
//
//        // Assert user is able to update a members role
//        workspaceService.updateMemberRole(workspaceId1, memberDeveloper, WorkspaceRoles.ADMIN).run {
//            assert(true) { "Member removal from workspace should not throw an exception" }
//
//            // Verify that the member repository was called to remove the user
//            Mockito.verify(workspaceMemberRepository).save(Mockito.any())
//        }
//
//        // Assert user is unable to update the role of another admin
//        assertThrows<AccessDeniedException> {
//            workspaceService.updateMemberRole(workspaceId1, memberAdmin, WorkspaceRoles.OWNER)
//        }
//
//        // Assert user is unable to update the role of an owner
//        assertThrows<AccessDeniedException> {
//            workspaceService.updateMemberRole(workspaceId1, memberOwner, WorkspaceRoles.ADMIN)
//        }
//
//        // Assert user is not able to update the role of any member to OWNER
//        assertThrows<IllegalArgumentException> {
//            workspaceService.updateMemberRole(workspaceId1, memberDeveloper, WorkspaceRoles.OWNER)
//        }
//    }
//
//    @Test
//    @WithUserPersona(
//        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
//        email = "email@email.com",
//        displayName = "Jared Tucker",
//        roles = [
//            WorkspaceRole(
//                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
//                role = WorkspaceRoles.OWNER
//            )
//        ]
//    )
//    fun `handle member role update as an owner`() {
//        val entity: WorkspaceEntity = WorkspaceFactory.createWorkspace(
//            id = workspaceId1,
//            name = "Test Workspace",
//        )
//
//        val targetUserId = UUID.randomUUID()
//        val targetUser2Id = UUID.randomUUID()
//
//        val user1: UserEntity = UserFactory.createUser(
//            // Different user ID to test member removal
//            id = targetUserId,
//        )
//
//        val user2: UserEntity = UserFactory.createUser(
//            // Different user ID to test member removal
//            id = targetUser2Id,
//        )
//
//        val key1 = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = targetUserId
//        )
//
//        val key2 = WorkspaceMemberEntity.WorkspaceMemberKey(
//            workspaceId = workspaceId1,
//            userId = targetUser2Id
//        )
//
//        val memberDeveloper: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user1,
//            role = WorkspaceRoles.MEMBER
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key1)).thenReturn(Optional.of(this))
//            }
//
//            it.toModel()
//        }
//
//        val memberAdmin: WorkspaceMember = WorkspaceFactory.createWorkspaceMember(
//            workspaceId = workspaceId1,
//            user = user2,
//            role = WorkspaceRoles.ADMIN
//        ).let {
//            it.run {
//                Mockito.`when`(workspaceMemberRepository.findById(key2)).thenReturn(Optional.of(this))
//            }
//
//            it.toModel()
//        }
//
//        Mockito.`when`(workspaceRepository.findById(workspaceId1)).thenReturn(Optional.of(entity))
//
//        // Assert user is able to update a members role
//        workspaceService.updateMemberRole(workspaceId1, memberDeveloper, WorkspaceRoles.ADMIN).run {
//            assert(true) { "Member role update should not throw an exception" }
//            // Verify the save function was invoked to update the role of the user
//            Mockito.verify(workspaceMemberRepository).save(Mockito.any())
//        }
//
//        // Assert user is able to update an admins role
//        workspaceService.updateMemberRole(workspaceId1, memberAdmin, WorkspaceRoles.MEMBER).run {
//            assert(true) { "Admin role update should not throw an exception" }
//            // Verify the save function was invoked for a second time to update the role of the user
//            Mockito.verify(workspaceMemberRepository, Mockito.times(2)).save(Mockito.any())
//        }
//
//        // Assert user is not able to update the role of any member to OWNER
//        assertThrows<IllegalArgumentException> {
//            workspaceService.updateMemberRole(workspaceId1, memberDeveloper, WorkspaceRoles.OWNER)
//        }
//    }
//}