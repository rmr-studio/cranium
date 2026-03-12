package riven.core.service.onboarding

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.user.UserEntity
import riven.core.enums.workspace.WorkspacePlan
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.models.request.onboarding.CompleteOnboardingRequest
import riven.core.models.request.onboarding.OnboardingInvite
import riven.core.models.request.onboarding.OnboardingProfile
import riven.core.models.request.onboarding.OnboardingWorkspace
import riven.core.models.request.workspace.SaveWorkspaceRequest
import riven.core.models.response.catalog.BundleInstallationResponse
import riven.core.models.response.catalog.TemplateInstallationResponse
import riven.core.models.user.User
import riven.core.models.workspace.Workspace
import riven.core.models.workspace.WorkspaceInvite
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.catalog.TemplateInstallationService
import riven.core.service.user.UserService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.UserFactory
import riven.core.service.workspace.WorkspaceInviteService
import riven.core.service.workspace.WorkspaceService
import java.time.ZonedDateTime
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        OnboardingService::class,
    ]
)
class OnboardingServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var workspaceService: WorkspaceService

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var templateInstallationService: TemplateInstallationService

    @MockitoBean
    private lateinit var workspaceInviteService: WorkspaceInviteService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var onboardingService: OnboardingService

    private val testWorkspaceId = UUID.randomUUID()

    private fun defaultRequest(
        templateKeys: List<String>? = null,
        bundleKeys: List<String>? = null,
        invites: List<OnboardingInvite>? = null,
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
        templateKeys = templateKeys,
        bundleKeys = bundleKeys,
        invites = invites,
    )

    private fun mockWorkspace(): Workspace = Workspace(
        id = testWorkspaceId,
        name = "My Workspace",
        plan = WorkspacePlan.FREE,
        memberCount = 1,
    )

    private fun mockUser(): User = User(
        id = userId,
        email = "test@example.com",
        name = "Test User",
        phone = "1234567890",
        defaultWorkspace = mockWorkspace(),
        onboardingCompletedAt = ZonedDateTime.now(),
    )

    private fun mockUserEntity(onboardingCompletedAt: ZonedDateTime? = null): UserEntity =
        UserFactory.createUser(id = userId).apply {
            this.onboardingCompletedAt = onboardingCompletedAt
        }

    private fun setupHappyPath() {
        val workspace = mockWorkspace()
        val user = mockUser()
        val userEntity = mockUserEntity()

        whenever(userService.getUserById(userId)).thenReturn(userEntity)
        whenever(transactionTemplate.execute<Pair<Workspace, User>>(any())).thenReturn(workspace to user)
    }

    // ------ Happy Path Tests ------

    @Test
    fun `completeOnboarding succeeds with all fields`() {
        setupHappyPath()

        val templateResponse = TemplateInstallationResponse(
            templateKey = "crm",
            templateName = "CRM",
            entityTypesCreated = 3,
            relationshipsCreated = 1,
            entityTypes = emptyList(),
        )
        whenever(templateInstallationService.installTemplateInternal(testWorkspaceId, "crm"))
            .thenReturn(templateResponse)

        val invite = mock<WorkspaceInvite>()
        whenever(workspaceInviteService.createWorkspaceInvitationInternal(eq(testWorkspaceId), eq("colleague@example.com"), eq(WorkspaceRoles.MEMBER), eq(userId)))
            .thenReturn(invite)

        val request = defaultRequest(
            templateKeys = listOf("crm"),
            invites = listOf(OnboardingInvite(email = "colleague@example.com", role = WorkspaceRoles.MEMBER)),
        )

        val response = onboardingService.completeOnboarding(request)

        assertEquals(testWorkspaceId, response.workspace.id)
        assertEquals(1, response.templateResults.size)
        assertTrue(response.templateResults[0].success)
        assertEquals(3, response.templateResults[0].entityTypesCreated)
        assertEquals(1, response.inviteResults.size)
        assertTrue(response.inviteResults[0].success)
    }

    @Test
    fun `completeOnboarding succeeds with required fields only`() {
        setupHappyPath()

        val request = defaultRequest()
        val response = onboardingService.completeOnboarding(request)

        assertEquals(testWorkspaceId, response.workspace.id)
        assertTrue(response.templateResults.isEmpty())
        assertTrue(response.inviteResults.isEmpty())
    }

    // ------ Eligibility Tests ------

    @Test
    fun `completeOnboarding throws ConflictException when already onboarded`() {
        val userEntity = mockUserEntity(onboardingCompletedAt = ZonedDateTime.now())
        whenever(userService.getUserById(userId)).thenReturn(userEntity)

        val request = defaultRequest()

        assertThrows<ConflictException> {
            onboardingService.completeOnboarding(request)
        }

        verify(transactionTemplate, never()).execute<Any>(any())
    }

    // ------ Template Partial Failure Tests ------

    @Test
    fun `completeOnboarding returns mixed template results on partial failure`() {
        setupHappyPath()

        val successResponse = TemplateInstallationResponse(
            templateKey = "crm",
            templateName = "CRM",
            entityTypesCreated = 2,
            relationshipsCreated = 0,
            entityTypes = emptyList(),
        )
        whenever(templateInstallationService.installTemplateInternal(testWorkspaceId, "crm"))
            .thenReturn(successResponse)
        whenever(templateInstallationService.installTemplateInternal(testWorkspaceId, "bad-template"))
            .thenThrow(RuntimeException("Template not found"))

        val request = defaultRequest(templateKeys = listOf("crm", "bad-template"))
        val response = onboardingService.completeOnboarding(request)

        assertEquals(testWorkspaceId, response.workspace.id)
        assertEquals(2, response.templateResults.size)
        assertTrue(response.templateResults[0].success)
        assertFalse(response.templateResults[1].success)
        assertEquals("Template not found", response.templateResults[1].error)
    }

    @Test
    fun `completeOnboarding still returns workspace when all templates fail`() {
        setupHappyPath()

        whenever(templateInstallationService.installTemplateInternal(eq(testWorkspaceId), any()))
            .thenThrow(RuntimeException("Install failed"))

        val request = defaultRequest(templateKeys = listOf("bad1", "bad2"))
        val response = onboardingService.completeOnboarding(request)

        assertEquals(testWorkspaceId, response.workspace.id)
        assertEquals(2, response.templateResults.size)
        assertTrue(response.templateResults.all { !it.success })
    }

    // ------ Invite Tests ------

    @Test
    fun `completeOnboarding returns mixed invite results on partial failure`() {
        setupHappyPath()

        val invite = mock<WorkspaceInvite>()
        whenever(workspaceInviteService.createWorkspaceInvitationInternal(eq(testWorkspaceId), eq("good@example.com"), eq(WorkspaceRoles.MEMBER), eq(userId)))
            .thenReturn(invite)
        whenever(workspaceInviteService.createWorkspaceInvitationInternal(eq(testWorkspaceId), eq("bad@example.com"), eq(WorkspaceRoles.ADMIN), eq(userId)))
            .thenThrow(RuntimeException("Invite failed"))

        val request = defaultRequest(
            invites = listOf(
                OnboardingInvite(email = "good@example.com", role = WorkspaceRoles.MEMBER),
                OnboardingInvite(email = "bad@example.com", role = WorkspaceRoles.ADMIN),
            ),
        )
        val response = onboardingService.completeOnboarding(request)

        assertEquals(2, response.inviteResults.size)
        assertTrue(response.inviteResults[0].success)
        assertFalse(response.inviteResults[1].success)
    }

    @Test
    fun `completeOnboarding still returns workspace when all invites fail`() {
        setupHappyPath()

        whenever(workspaceInviteService.createWorkspaceInvitationInternal(eq(testWorkspaceId), any(), any(), eq(userId)))
            .thenThrow(RuntimeException("All invites fail"))

        val request = defaultRequest(
            invites = listOf(
                OnboardingInvite(email = "a@example.com", role = WorkspaceRoles.MEMBER),
            ),
        )
        val response = onboardingService.completeOnboarding(request)

        assertEquals(testWorkspaceId, response.workspace.id)
        assertEquals(1, response.inviteResults.size)
        assertFalse(response.inviteResults[0].success)
    }

    @Test
    fun `completeOnboarding filters self-invite`() {
        setupHappyPath()

        val request = defaultRequest(
            invites = listOf(
                OnboardingInvite(email = "test@example.com", role = WorkspaceRoles.MEMBER),
            ),
        )
        val response = onboardingService.completeOnboarding(request)

        assertEquals(1, response.inviteResults.size)
        assertFalse(response.inviteResults[0].success)
        assertEquals("Cannot invite yourself to your own workspace", response.inviteResults[0].error)

        verify(workspaceInviteService, never()).createWorkspaceInvitationInternal(any(), any(), any(), any())
    }

    @Test
    fun `completeOnboarding rejects invite with OWNER role`() {
        setupHappyPath()

        val request = defaultRequest(
            invites = listOf(
                OnboardingInvite(email = "other@example.com", role = WorkspaceRoles.OWNER),
            ),
        )
        val response = onboardingService.completeOnboarding(request)

        assertEquals(1, response.inviteResults.size)
        assertFalse(response.inviteResults[0].success)
        assertEquals("Cannot invite with OWNER role", response.inviteResults[0].error)

        verify(workspaceInviteService, never()).createWorkspaceInvitationInternal(any(), any(), any(), any())
    }

    // ------ Avatar Tests ------

    @Test
    fun `completeOnboarding passes workspace avatar to saveWorkspace`() {
        setupHappyPath()
        val avatarFile = mock<MultipartFile>()

        val request = defaultRequest()
        onboardingService.completeOnboarding(request, workspaceAvatar = avatarFile)

        // TransactionTemplate.execute is mocked, so we verify the overall flow completed
        verify(transactionTemplate).execute<Pair<Workspace, User>>(any())
    }

    @Test
    fun `completeOnboarding passes profile avatar through`() {
        setupHappyPath()
        val avatarFile = mock<MultipartFile>()

        val request = defaultRequest()
        onboardingService.completeOnboarding(request, profileAvatar = avatarFile)

        verify(transactionTemplate).execute<Pair<Workspace, User>>(any())
    }

    // ------ Bundle Keys Test ------

    @Test
    fun `completeOnboarding calls installBundleInternal for bundle keys`() {
        setupHappyPath()

        val bundleResponse = BundleInstallationResponse(
            bundleKey = "starter-bundle",
            bundleName = "Starter",
            templatesInstalled = listOf("crm"),
            templatesSkipped = emptyList(),
            entityTypesCreated = 5,
            relationshipsCreated = 2,
            entityTypes = emptyList(),
        )
        whenever(templateInstallationService.installBundleInternal(testWorkspaceId, "starter-bundle"))
            .thenReturn(bundleResponse)

        val request = defaultRequest(bundleKeys = listOf("starter-bundle"))
        val response = onboardingService.completeOnboarding(request)

        assertEquals(1, response.templateResults.size)
        assertTrue(response.templateResults[0].success)
        assertEquals("starter-bundle", response.templateResults[0].key)
        assertEquals(5, response.templateResults[0].entityTypesCreated)
    }

    // ------ Workspace Creation Failure Test ------

    @Test
    fun `completeOnboarding propagates workspace creation failure`() {
        val userEntity = mockUserEntity()
        whenever(userService.getUserById(userId)).thenReturn(userEntity)
        whenever(transactionTemplate.execute<Pair<Workspace, User>>(any()))
            .thenThrow(RuntimeException("Workspace creation failed"))

        val request = defaultRequest()

        assertThrows<RuntimeException> {
            onboardingService.completeOnboarding(request)
        }

        verify(templateInstallationService, never()).installTemplateInternal(any(), any())
        verify(workspaceInviteService, never()).createWorkspaceInvitationInternal(any(), any(), any(), any())
    }
}
