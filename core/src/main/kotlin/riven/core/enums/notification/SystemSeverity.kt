package cranium.core.enums.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "SystemSeverity",
    description = "Severity level for system notifications.",
    enumAsRef = true,
)
enum class SystemSeverity {
    @JsonProperty("INFO")
    INFO,

    @JsonProperty("WARNING")
    WARNING,

    @JsonProperty("ERROR")
    ERROR,
}
