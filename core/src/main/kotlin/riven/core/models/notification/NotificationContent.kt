package riven.core.models.notification

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.databind.annotation.JsonTypeIdResolver
import riven.core.configuration.util.CaseInsensitiveTypeIdResolver
import riven.core.enums.notification.ReviewPriority
import riven.core.enums.notification.SystemSeverity

@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "type")
@JsonTypeIdResolver(CaseInsensitiveTypeIdResolver::class)
@JsonSubTypes(
    JsonSubTypes.Type(NotificationContent.Information::class, name = "INFORMATION"),
    JsonSubTypes.Type(NotificationContent.ReviewRequest::class, name = "REVIEW_REQUEST"),
    JsonSubTypes.Type(NotificationContent.System::class, name = "SYSTEM"),
)
sealed class NotificationContent {
    abstract val title: String
    abstract val message: String

    data class Information(
        override val title: String,
        override val message: String,
        val sourceLabel: String? = null,
    ) : NotificationContent()

    data class ReviewRequest(
        override val title: String,
        override val message: String,
        val contextSummary: String? = null,
        val priority: ReviewPriority = ReviewPriority.NORMAL,
    ) : NotificationContent()

    data class System(
        override val title: String,
        override val message: String,
        val severity: SystemSeverity = SystemSeverity.INFO,
    ) : NotificationContent()
}
