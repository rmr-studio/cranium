package cranium.core.service.workflow.state

import org.springframework.stereotype.Service

/**
 * Service for parsing template strings with {{ path.to.data }} syntax.
 *
 * ## Template Syntax
 *
 * Templates use double curly braces to mark data references:
 * - `{{ steps.fetch_leads.output }}` - Access output from previous step
 * - `{{ steps.fetch_leads.output.email }}` - Access nested property
 * - `{{ loop.loop_name.item.field }}` - Loop iteration data (Phase 5+)
 *
 * ## Supported Patterns
 *
 * **Valid templates:**
 * - `{{ steps.node_name.output }}` - Simple property access
 * - `{{ steps.node_name.output.field }}` - Nested property access
 * - `{{  steps.name  }}` - Whitespace around path is trimmed
 * - `"Welcome to {{ steps.user.name }}"` - Embedded template within string
 * - `"Hello {{ steps.user.name }}, you have {{ steps.inbox.count }} messages"` - Multiple embedded templates
 * - Path segments can contain: letters, numbers, underscores
 * - Arbitrary nesting depth is supported
 *
 * **Invalid templates:**
 * - `{{ }}` - Empty template (error)
 * - `{{ steps..output }}` - Empty path segment (error)
 * - `{{ steps-name }}` - Invalid characters (only alphanumeric and underscore allowed)
 *
 * ## Error Handling
 *
 * Parsing errors throw IllegalArgumentException with clear messages:
 * - "Empty template" - Template has no path
 * - "Empty path segment" - Path contains consecutive dots
 * - "Invalid path segment" - Segment contains invalid characters
 *
 * ## Usage Example
 *
 * ```kotlin
 * val parser = TemplateParserService()
 *
 * // Parse exact template
 * val result = parser.parse("{{ steps.fetch_leads.output.email }}")
 * // result.isTemplate == true
 * // result.isEmbeddedTemplate == false
 * // result.path == ["steps", "fetch_leads", "output", "email"]
 *
 * // Parse embedded template
 * val result2 = parser.parse("Welcome to {{ steps.user.name }}")
 * // result2.isTemplate == true
 * // result2.isEmbeddedTemplate == true
 * // result2.embeddedTemplates[0].path == ["steps", "user", "name"]
 * // result2.templateString == "Welcome to {{ steps.user.name }}"
 *
 * // Parse static string
 * val result3 = parser.parse("static value")
 * // result3.isTemplate == false
 * // result3.rawValue == "static value"
 * ```
 *
 * @see WorkflowNodeInputResolverService for template resolution against data registry
 */
@Service
class WorkflowNodeTemplateParserService {

    /**
     * Result of parsing a template string.
     *
     * @property isTemplate true if the string contains a template ({{ }})
     * @property path Path segments if template (e.g., ["steps", "fetch_leads", "output", "email"])
     * @property rawValue Original string if not a template
     * @property isEmbeddedTemplate true if templates are embedded within a larger string
     * @property embeddedTemplates List of templates found in the string (for embedded templates)
     * @property templateString Original string containing embedded templates
     */
    data class ParsedTemplate(
        val isTemplate: Boolean,
        val path: List<String>?,
        val rawValue: String?,
        val isEmbeddedTemplate: Boolean = false,
        val embeddedTemplates: List<EmbeddedTemplateInfo>? = null,
        val templateString: String? = null
    )

    /**
     * Information about an embedded template within a larger string.
     *
     * @property path Path segments of the template
     * @property placeholder The full template string including braces (e.g., "{{ steps.node.output }}")
     */
    data class EmbeddedTemplateInfo(
        val path: List<String>,
        val placeholder: String
    )

    /**
     * Regex pattern to match template syntax: {{ path.to.data }}
     *
     * Pattern breakdown:
     * - `\{\{` - Opening braces (escaped)
     * - `\s*` - Optional whitespace
     * - `([a-zA-Z0-9_.]+)` - Capture group: path (alphanumeric, underscore, dots)
     * - `\s*` - Optional whitespace
     * - `\}\}` - Closing braces (escaped)
     */
    private val templatePattern = Regex("""\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}""")

    /**
     * Regex pattern to detect malformed templates: {{ ... }} with any content
     * Used to provide better error messages for invalid template syntax
     */
    private val malformedTemplatePattern = Regex("""\{\{[^}]*\}\}""")

    /**
     * Parse a string that may contain a template.
     *
     * @param input String to parse (may be template or static value)
     * @return ParsedTemplate with isTemplate, path, or rawValue
     * @throws IllegalArgumentException if template syntax is invalid
     */
    fun parse(input: String): ParsedTemplate {
        val matches = templatePattern.findAll(input).toList()

        // No valid template found - check for malformed templates
        if (matches.isEmpty()) {
            // Check if there are malformed templates ({{ }} syntax but invalid content)
            val malformedMatches = malformedTemplatePattern.findAll(input).toList()
            if (malformedMatches.isNotEmpty()) {
                // Extract what's inside the braces for better error message
                val malformed = malformedMatches.first().value
                val content = malformed.removePrefix("{{").removeSuffix("}}").trim()

                if (content.isEmpty()) {
                    throw IllegalArgumentException("Empty template: $input")
                }

                // Check for invalid characters
                if (content.contains("..")) {
                    throw IllegalArgumentException("Empty path segment in template: $input")
                }

                // If it contains invalid characters, provide specific error
                throw IllegalArgumentException(
                    "Invalid template syntax: $malformed. Only alphanumeric characters, underscores, and dots are allowed in template paths."
                )
            }

            // No template syntax at all - return as static value
            return ParsedTemplate(
                isTemplate = false,
                path = null,
                rawValue = input
            )
        }

        // Check if template is embedded in larger string (single or multiple templates)
        val isSingleExactTemplate = matches.size == 1 && matches.first().value == input.trim()

        if (!isSingleExactTemplate) {
            // Handle embedded templates - one or more templates within a string
            val embeddedTemplates = matches.map { match ->
                val pathString = match.groupValues[1].trim()

                // Validate path
                if (pathString.isEmpty()) {
                    throw IllegalArgumentException("Empty template in: $input")
                }

                val pathSegments = pathString.split(".")

                // Validate path segments
                for (segment in pathSegments) {
                    if (segment.isEmpty()) {
                        throw IllegalArgumentException("Empty path segment in template: ${match.value}")
                    }
                    if (!segment.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                        throw IllegalArgumentException(
                            "Invalid path segment '$segment'. Only alphanumeric and underscore allowed: ${match.value}"
                        )
                    }
                }

                EmbeddedTemplateInfo(
                    path = pathSegments,
                    placeholder = match.value
                )
            }

            return ParsedTemplate(
                isTemplate = true,
                path = null,
                rawValue = null,
                isEmbeddedTemplate = true,
                embeddedTemplates = embeddedTemplates,
                templateString = input
            )
        }

        // Single exact template match - extract path
        val match = matches.first()
        val pathString = match.groupValues[1].trim()

        // Validate path is not empty
        if (pathString.isEmpty()) {
            throw IllegalArgumentException("Empty template: $input")
        }

        // Split path on dots
        val pathSegments = pathString.split(".")

        // Validate path segments
        for (segment in pathSegments) {
            if (segment.isEmpty()) {
                throw IllegalArgumentException("Empty path segment in template: $input")
            }
            if (!segment.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                throw IllegalArgumentException(
                    "Invalid path segment '$segment'. Only alphanumeric and underscore allowed: $input"
                )
            }
        }

        return ParsedTemplate(
            isTemplate = true,
            path = pathSegments,
            rawValue = null
        )
    }

    /**
     * Quick check if a string contains a template.
     *
     * This is faster than full parsing when you only need to know
     * if the string is a template without extracting the path.
     *
     * @param input String to check
     * @return true if string contains {{ }} template syntax
     */
    fun isTemplate(input: String): Boolean {
        return templatePattern.containsMatchIn(input)
    }
}