package riven.core.models.request.onboarding

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.user.AcquisitionChannel
import riven.core.enums.workspace.BusinessType
import riven.core.enums.workspace.WorkspacePlan
import riven.core.enums.workspace.WorkspaceRoles

data class CompleteOnboardingRequest(
    @field:Valid
    val workspace: OnboardingWorkspace,
    @field:Valid
    val profile: OnboardingProfile,
    @field:NotNull
    val businessType: BusinessType,
    @field:Valid
    val invites: List<OnboardingInvite>? = null,
    @field:Valid
    val businessDefinitions: List<OnboardingBusinessDefinition>? = null,
    val acquisitionChannels: List<AcquisitionChannel>? = null,
)

data class OnboardingWorkspace(
    @field:NotBlank
    val name: String,
    val plan: WorkspacePlan,
    @field:NotBlank
    val defaultCurrency: String,
)

data class OnboardingProfile(
    @field:NotBlank
    @field:Size(min = 3)
    val name: String,
    val phone: String? = null,
)

data class OnboardingInvite(
    @field:NotBlank
    @field:Email
    val email: String,
    val role: WorkspaceRoles,
)

data class OnboardingBusinessDefinition(
    @field:NotBlank
    val term: String,
    @field:NotBlank
    val definition: String,
    @field:NotNull
    val category: DefinitionCategory,
    val isCustomized: Boolean = false,
)
