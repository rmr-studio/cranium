package riven.core.service.onboarding

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.workspace.BusinessType
import riven.core.enums.workspace.WorkspacePlan
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.models.request.knowledge.CreateBusinessDefinitionRequest
import riven.core.models.request.onboarding.*
import riven.core.models.request.user.SaveUserRequest
import riven.core.models.request.workspace.SaveWorkspaceRequest
import riven.core.models.response.catalog.TemplateInstallationResponse
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.catalog.TemplateInstallationService
import riven.core.service.knowledge.WorkspaceBusinessDefinitionService
import riven.core.service.user.UserService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.models.user.User
import riven.core.repository.user.UserRepository
import riven.core.service.util.factory.UserFactory
import riven.core.service.util.factory.WorkspaceFactory
import riven.core.service.workspace.WorkspaceInviteService
import riven.core.service.workspace.WorkspaceService
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        OnboardingServiceTest.TestConfig::class,
        OnboardingService::class,
    ]
)
class OnboardingServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean private lateinit var workspaceService: WorkspaceService
    @MockitoBean private lateinit var userService: UserService
    @MockitoBean private lateinit var userRepository: UserRepository
    @MockitoBean private lateinit var templateInstallationService: TemplateInstallationService
    @MockitoBean private lateinit var workspaceInviteService: WorkspaceInviteService
    @MockitoBean private lateinit var businessDefinitionService: WorkspaceBusinessDefinitionService
    @MockitoBean private lateinit var authTokenService: AuthTokenService
    @MockitoBean private lateinit var activityService: ActivityService
    @MockitoBean private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var onboardingService: OnboardingService

    private val newWorkspaceId = UUID.randomUUID()

    private fun defaultRequest(
        invites: List<OnboardingInvite>? = null,
        businessDefinitions: List<OnboardingBusinessDefinition>? = null,
    ) = CompleteOnboardingRequest(
        workspace = OnboardingWorkspace(
            name = "My Workspace",
            plan = WorkspacePlan.FREE,
            defaultCurrency = "USD",
        ),
        profile = OnboardingProfile(
            name = "Test User",
            phone = "1234567890",
        ),
        businessType = BusinessType.B2C_SAAS,
        invites = invites,
        businessDefinitions = businessDefinitions,
    )

    @BeforeEach
    fun setUp() {
        reset(
            workspaceService, userService, userRepository, templateInstallationService,
            workspaceInviteService, businessDefinitionService,
            authTokenService, activityService, transactionTemplate,
        )

        whenever(authTokenService.getUserId()).thenReturn(userId)
        whenever(authTokenService.getUserEmail()).thenReturn("test@example.com")

        // TransactionTemplate.execute should run the callback immediately
        whenever(transactionTemplate.execute(any<TransactionCallback<*>>())).thenAnswer { invocation ->
            val callback = invocation.getArgument<TransactionCallback<*>>(0)
            callback.doInTransaction(mock())
        }

        val workspaceEntity = WorkspaceFactory.createWorkspace(id = newWorkspaceId, name = "My Workspace")
        val workspace = workspaceEntity.toModel()
        whenever(workspaceService.saveWorkspace(any<SaveWorkspaceRequest>(), anyOrNull())).thenReturn(workspace)

        val userEntity = UserFactory.createUser(id = userId, name = "Test User", email = "test@example.com")
        val userModel = User(id = userId, email = "test@example.com", name = "Test User", phone = "1234567890")
        whenever(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(userEntity))
        whenever(userService.getUserById(userId)).thenReturn(userEntity)
        whenever(userService.getUserWithWorkspacesById(userId)).thenReturn(userModel)
        whenever(userService.updateUserDetails(any<SaveUserRequest>(), anyOrNull())).thenReturn(userModel)

        whenever(templateInstallationService.installTemplateInternal(any(), any(), any())).thenReturn(
            TemplateInstallationResponse(
                templateKey = "saas",
                templateName = "SaaS Template",
                entityTypesCreated = 3,
                relationshipsCreated = 2,
                entityTypes = emptyList(),
            )
        )

        whenever(activityService.logActivity(any(), any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock())
    }

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.OWNER)]
    )
    inner class CompleteOnboarding {

        @Test
        fun `happy path creates workspace, installs template, and updates profile atomically`() {
            val request = defaultRequest()

            val response = onboardingService.completeOnboarding(request)

            assertNotNull(response.workspace)
            assertEquals(newWorkspaceId, response.workspace.id)
            assertNotNull(response.user)
            assertEquals(3, response.templateResult.entityTypesCreated)
            assertEquals(2, response.templateResult.relationshipsCreated)
            assertTrue(response.inviteResults.isEmpty())
            assertTrue(response.definitionResults.isEmpty())

            // Verify template installation uses internal (auth-bypass) method
            verify(templateInstallationService).installTemplateInternal(eq(newWorkspaceId), any(), eq(userId))
            verify(templateInstallationService, never()).installTemplate(any(), any())
        }

        @Test
        fun `rejects user who has already completed onboarding`() {
            val alreadyOnboardedUser = UserFactory.createOnboardedUser(id = userId)
            whenever(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(alreadyOnboardedUser))

            val request = defaultRequest()

            assertThrows(ConflictException::class.java) {
                onboardingService.completeOnboarding(request)
            }

            verify(workspaceService, never()).saveWorkspace(any<SaveWorkspaceRequest>(), anyOrNull())
        }

        @Test
        fun `template installation failure propagates exception — workspace is not silently created without schema`() {
            whenever(templateInstallationService.installTemplateInternal(any(), any(), any()))
                .thenThrow(RuntimeException("Template manifest not found"))

            val request = defaultRequest()

            assertThrows(RuntimeException::class.java) {
                onboardingService.completeOnboarding(request)
            }
        }
    }

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.OWNER)]
    )
    inner class InviteEdgeCases {

        @Test
        fun `self-invite returns failure result without throwing`() {
            val request = defaultRequest(
                invites = listOf(OnboardingInvite(email = "test@example.com", role = WorkspaceRoles.MEMBER))
            )

            val response = onboardingService.completeOnboarding(request)

            assertEquals(1, response.inviteResults.size)
            assertFalse(response.inviteResults[0].success)
            assertEquals("Cannot invite yourself to your own workspace", response.inviteResults[0].error)
            verify(workspaceInviteService, never()).createWorkspaceInvitationInternal(any(), any(), any(), any())
        }

        @Test
        fun `owner role invite returns failure result without throwing`() {
            val request = defaultRequest(
                invites = listOf(OnboardingInvite(email = "other@example.com", role = WorkspaceRoles.OWNER))
            )

            val response = onboardingService.completeOnboarding(request)

            assertEquals(1, response.inviteResults.size)
            assertFalse(response.inviteResults[0].success)
            assertEquals("Cannot invite with OWNER role", response.inviteResults[0].error)
        }

        @Test
        fun `valid invite calls internal method and returns success`() {
            val request = defaultRequest(
                invites = listOf(OnboardingInvite(email = "colleague@example.com", role = WorkspaceRoles.MEMBER))
            )

            whenever(workspaceInviteService.createWorkspaceInvitationInternal(any(), any(), any(), any()))
                .thenReturn(mock())

            val response = onboardingService.completeOnboarding(request)

            assertEquals(1, response.inviteResults.size)
            assertTrue(response.inviteResults[0].success)
            verify(workspaceInviteService).createWorkspaceInvitationInternal(
                eq(newWorkspaceId), eq("colleague@example.com"), eq(WorkspaceRoles.MEMBER), eq(userId)
            )
        }

        @Test
        fun `invite service failure returns error result without failing onboarding`() {
            val request = defaultRequest(
                invites = listOf(OnboardingInvite(email = "colleague@example.com", role = WorkspaceRoles.MEMBER))
            )

            whenever(workspaceInviteService.createWorkspaceInvitationInternal(any(), any(), any(), any()))
                .thenThrow(ConflictException("Invitation already exists"))

            val response = onboardingService.completeOnboarding(request)

            assertEquals(1, response.inviteResults.size)
            assertFalse(response.inviteResults[0].success)
            assertEquals("invite_conflict", response.inviteResults[0].error)
            // Onboarding still completes — workspace and template are intact
            assertNotNull(response.workspace)
        }
    }

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.OWNER)]
    )
    inner class BusinessDefinitions {

        @Test
        fun `valid definitions call internal method and return success`() {
            val request = defaultRequest(
                businessDefinitions = listOf(
                    OnboardingBusinessDefinition(term = "MRR", definition = "Monthly Recurring Revenue", category = DefinitionCategory.METRIC),
                )
            )

            whenever(businessDefinitionService.createDefinitionInternal(any(), any(), any<CreateBusinessDefinitionRequest>()))
                .thenReturn(mock())

            val response = onboardingService.completeOnboarding(request)

            assertEquals(1, response.definitionResults.size)
            assertTrue(response.definitionResults[0].success)
            assertEquals("MRR", response.definitionResults[0].term)
            verify(businessDefinitionService).createDefinitionInternal(eq(newWorkspaceId), eq(userId), any())
        }

        @Test
        fun `passes isCustomized flag to definition service`() {
            val request = defaultRequest(
                businessDefinitions = listOf(
                    OnboardingBusinessDefinition(term = "Churn", definition = "Custom churn def", category = DefinitionCategory.METRIC, isCustomized = true),
                )
            )

            whenever(businessDefinitionService.createDefinitionInternal(any(), any(), any<CreateBusinessDefinitionRequest>()))
                .thenReturn(mock())

            onboardingService.completeOnboarding(request)

            verify(businessDefinitionService).createDefinitionInternal(
                eq(newWorkspaceId),
                eq(userId),
                argThat<CreateBusinessDefinitionRequest> { isCustomized }
            )
        }

        @Test
        fun `definition service failure returns error result without failing onboarding`() {
            val request = defaultRequest(
                businessDefinitions = listOf(
                    OnboardingBusinessDefinition(term = "MRR", definition = "Monthly Recurring Revenue", category = DefinitionCategory.METRIC),
                )
            )

            whenever(businessDefinitionService.createDefinitionInternal(any(), any(), any<CreateBusinessDefinitionRequest>()))
                .thenThrow(ConflictException("Duplicate term"))

            val response = onboardingService.completeOnboarding(request)

            assertEquals(1, response.definitionResults.size)
            assertFalse(response.definitionResults[0].success)
            assertEquals("definition_conflict", response.definitionResults[0].error)
            assertNotNull(response.workspace)
        }
    }
}
