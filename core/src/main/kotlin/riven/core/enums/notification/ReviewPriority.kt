package cranium.core.enums.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "ReviewPriority",
    description = "Priority level for review request notifications.",
    enumAsRef = true,
)
enum class ReviewPriority {
    @JsonProperty("LOW")
    LOW,

    @JsonProperty("NORMAL")
    NORMAL,

    @JsonProperty("HIGH")
    HIGH,

    @JsonProperty("URGENT")
    URGENT,
}
