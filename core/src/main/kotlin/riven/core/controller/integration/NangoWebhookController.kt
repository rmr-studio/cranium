package riven.core.controller.integration

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import riven.core.models.integration.NangoWebhookPayload
import riven.core.service.integration.NangoWebhookService

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "webhooks")
class NangoWebhookController(
    private val webhookService: NangoWebhookService
) {

    @PostMapping("/nango")
    @Operation(summary = "Handle Nango webhook events (auth and sync)")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Webhook processed"),
        ApiResponse(responseCode = "401", description = "Invalid or missing HMAC signature")
    )
    fun handleNangoWebhook(
        @RequestBody payload: NangoWebhookPayload
    ): ResponseEntity<Void> {
        webhookService.handleWebhook(payload)
        return ResponseEntity.ok().build()
    }
}
