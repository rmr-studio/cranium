package cranium.core.models.response.onboarding

import cranium.core.models.user.UserDisplay
import cranium.core.models.workspace.Workspace

data class CompleteOnboardingResponse(
    val workspace: Workspace,
    val user: UserDisplay,
    val templateResult: TemplateInstallResult,
    val inviteResults: List<InviteResult> = emptyList(),
    val definitionResults: List<BusinessDefinitionResult> = emptyList(),
)

data class TemplateInstallResult(
    val key: String,
    val entityTypesCreated: Int = 0,
    val relationshipsCreated: Int = 0,
)

data class InviteResult(
    val email: String,
    val success: Boolean,
    val error: String? = null,
)

data class BusinessDefinitionResult(
    val term: String,
    val success: Boolean,
    val error: String? = null,
)
