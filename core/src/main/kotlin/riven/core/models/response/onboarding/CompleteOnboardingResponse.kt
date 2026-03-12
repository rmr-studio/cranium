package riven.core.models.response.onboarding

import riven.core.models.user.User
import riven.core.models.workspace.Workspace

data class CompleteOnboardingResponse(
    val workspace: Workspace,
    val user: User,
    val templateResults: List<TemplateInstallResult> = emptyList(),
    val inviteResults: List<InviteResult> = emptyList(),
)

data class TemplateInstallResult(
    val key: String,
    val success: Boolean,
    val error: String? = null,
    val entityTypesCreated: Int = 0,
)

data class InviteResult(
    val email: String,
    val success: Boolean,
    val error: String? = null,
)
