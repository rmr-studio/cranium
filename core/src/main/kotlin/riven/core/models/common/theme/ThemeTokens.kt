package cranium.core.models.common.theme

/**
 * Theme Tokens used across the application for consistent styling
 */
data class ThemeTokens(
    val variant: String? = null,                 // "default" | "muted" | "emphasis"
    val colorRole: String? = null,               // "info" | "warning" | "success" | ...
    val tone: String? = null                     // "light" | "dark" | "auto"
    )