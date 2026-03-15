package riven.core.enums.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "NotificationType",
    description = "Category of notification — drives frontend rendering template.",
    enumAsRef = true,
)
enum class NotificationType {
    @JsonProperty("INFORMATION")
    INFORMATION,

    @JsonProperty("REVIEW_REQUEST")
    REVIEW_REQUEST,

    @JsonProperty("SYSTEM")
    SYSTEM,
}
