package riven.core.controller.onboarding

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import riven.core.models.request.onboarding.CompleteOnboardingRequest
import riven.core.models.response.onboarding.CompleteOnboardingResponse
import riven.core.service.onboarding.OnboardingService

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "onboarding")
class OnboardingController(
    private val onboardingService: OnboardingService,
) {

    @PostMapping("/complete", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Complete user onboarding", description = "Creates a workspace, updates user profile, installs templates, and sends invitations in a single request.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Onboarding completed successfully"),
        ApiResponse(responseCode = "409", description = "User has already completed onboarding"),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
    )
    fun completeOnboarding(
        @Valid @RequestPart("request") request: CompleteOnboardingRequest,
        @RequestPart("profileAvatar", required = false) profileAvatar: MultipartFile? = null,
        @RequestPart("workspaceAvatar", required = false) workspaceAvatar: MultipartFile? = null,
    ): ResponseEntity<CompleteOnboardingResponse> {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(onboardingService.completeOnboarding(request, profileAvatar, workspaceAvatar))
    }
}
