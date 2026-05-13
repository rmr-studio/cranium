package cranium.core.service.workflow.state

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.models.workflow.engine.state.WorkflowDataStore

/**
 * Service for resolving template references against the workflow datastore.
 *
 * ## Resolution Algorithm
 *
 * 1. **Template Detection:** Check if value is a String containing {{ }}
 * 2. **Root Segment Routing:** Route to appropriate resolver based on first segment
 * 3. **Property Traversal:** Navigate nested maps using remaining path segments
 * 4. **Graceful Degradation:** Return null for missing data, logger warnings
 *
 * ## Supported Root Prefixes
 *
 * - `steps` - Access completed node outputs (e.g., {{ steps.nodeName.output.field }})
 * - `trigger` - Access trigger context (e.g., {{ trigger.entity.fieldName }})
 * - `variables` - Access user variables (e.g., {{ variables.counter }})
 * - `loops` - Access loop iteration context (e.g., {{ loops.loopId.currentItem }})
 *
 * ## Template Types
 *
 * **Exact Templates:** Entire string is a template
 * - Input: `"{{ steps.fetch_leads.output.email }}"`
 * - Returns resolved value as-is (any type)
 *
 * **Embedded Templates:** Templates within a larger string
 * - Input: `"Welcome to {{ trigger.entity.name }}"`
 * - Resolves each template and replaces it in the string
 * - Returns final string with substitutions
 *
 * ## Error Handling Strategy
 *
 * **Errors (throw exception):**
 * - Invalid template syntax (parsing errors)
 * - Unrecognized root segment (not steps/trigger/variables/loops)
 * - Trigger not set when accessing {{ trigger.* }}
 *
 * **Warnings (logger + return null):**
 * - Node/variable/loop not found (may not have executed yet)
 * - Property not found in output (missing field)
 * - Type error (accessing property on non-map value)
 *
 * @property workflowNodeTemplateParserService Parser for template syntax
 */
@Service
class WorkflowNodeInputResolverService(
    private val workflowNodeTemplateParserService: WorkflowNodeTemplateParserService,
    private val logger: KLogger
) {

    /**
     * Resolve a single value that may contain a template.
     *
     * @param templateOrValue Value to resolve (String template or static value)
     * @param dataStore Workflow datastore containing execution state
     * @return Resolved value from datastore, or original value if not a template
     */
    fun resolve(
        templateOrValue: Any?,
        dataStore: WorkflowDataStore
    ): Any? {
        // Only strings can be templates
        if (templateOrValue !is String) {
            return templateOrValue
        }

        // Parse template
        val parsed = workflowNodeTemplateParserService.parse(templateOrValue)

        // Not a template - return as-is
        if (!parsed.isTemplate) {
            return parsed.rawValue
        }

        // Handle embedded templates (e.g., "Welcome to {{steps.node.output}}")
        if (parsed.isEmbeddedTemplate) {
            return resolveEmbeddedTemplates(parsed, dataStore)
        }

        // Single exact template - resolve from datastore
        val path = parsed.path ?: return null

        return resolveTemplatePath(path, dataStore)
    }

    /**
     * Resolve embedded templates within a larger string.
     *
     * Takes a string like "Welcome to {{trigger.entity.name}}" and replaces each
     * template with its resolved value.
     *
     * @param parsed Parsed template containing embedded templates
     * @param dataStore Workflow datastore
     * @return String with all templates replaced by resolved values, or null if any resolution fails
     */
    private fun resolveEmbeddedTemplates(
        parsed: WorkflowNodeTemplateParserService.ParsedTemplate,
        dataStore: WorkflowDataStore
    ): String? {
        var result = parsed.templateString ?: return null

        // Resolve each embedded template and replace in string
        for (embeddedTemplate in parsed.embeddedTemplates.orEmpty()) {
            val resolvedValue = resolveTemplatePath(embeddedTemplate.path, dataStore)

            // If resolution fails, return null (graceful degradation)
            if (resolvedValue == null) {
                logger.warn { "Failed to resolve embedded template ${embeddedTemplate.placeholder} in string: ${parsed.templateString}" }
                return null
            }

            // Convert resolved value to string and replace placeholder
            val stringValue = when (resolvedValue) {
                is String -> resolvedValue
                is Number -> resolvedValue.toString()
                is Boolean -> resolvedValue.toString()
                else -> {
                    logger.warn { "Cannot convert ${resolvedValue::class.simpleName} to string for embedded template: ${embeddedTemplate.placeholder}" }
                    resolvedValue.toString()
                }
            }

            result = result.replace(embeddedTemplate.placeholder, stringValue)
        }

        return result
    }

    /**
     * Resolve a template path from the datastore.
     *
     * Routes to the appropriate resolver based on the root segment:
     * - steps -> resolveStepsPath()
     * - trigger -> resolveTriggerPath()
     * - variables -> resolveVariablesPath()
     * - loops -> resolveLoopsPath()
     *
     * @param path Path segments (e.g., ["steps", "fetch_leads", "output", "email"])
     * @param dataStore Workflow datastore
     * @return Resolved value from datastore, or null if not found
     * @throws IllegalArgumentException if path is empty or has invalid root segment
     */
    private fun resolveTemplatePath(
        path: List<String>,
        dataStore: WorkflowDataStore
    ): Any? {
        if (path.isEmpty()) {
            throw IllegalArgumentException("Empty template path")
        }

        val rootSegment = path[0]

        return when (rootSegment) {
            "steps" -> resolveStepsPath(path.drop(1), dataStore)
            "trigger" -> resolveTriggerPath(path.drop(1), dataStore)
            "variables" -> resolveVariablesPath(path.drop(1), dataStore)
            "loops" -> resolveLoopsPath(path.drop(1), dataStore)
            else -> throw IllegalArgumentException(
                "Invalid root segment '$rootSegment'. Must be: steps, trigger, variables, loops"
            )
        }
    }

    /**
     * Resolve a path under the "steps" prefix.
     *
     * Format: steps.<nodeName>[.output].<field>...
     *
     * The "output" segment is optional for backward compatibility with
     * {{ steps.nodeName.output.field }} syntax.
     *
     * @param path Path segments after "steps" (e.g., ["fetch_leads", "output", "email"])
     * @param dataStore Workflow datastore
     * @return Resolved value, or null if not found
     */
    private fun resolveStepsPath(path: List<String>, dataStore: WorkflowDataStore): Any? {
        if (path.isEmpty()) {
            throw IllegalArgumentException("Template path must include node name: steps.<nodeName>")
        }

        val nodeName = path[0]
        val stepOutput = dataStore.getStepOutput(nodeName)

        if (stepOutput == null) {
            logger.warn { "Node '$nodeName' not found in datastore. Available: ${dataStore.getAllStepOutputs().keys}" }
            return null
        }

        if (stepOutput.status != WorkflowStatus.COMPLETED) {
            logger.warn { "Node '$nodeName' did not complete (status: ${stepOutput.status})" }
            return null
        }

        // Convert NodeOutput to map for traversal
        var current: Any? = stepOutput.output.toMap()

        // Determine start index (skip "output" if present for backward compat)
        val startIndex = if (path.size > 1 && path[1] == "output") 2 else 1

        // Traverse remaining path
        for (i in startIndex until path.size) {
            current = traverseProperty(current, path[i], listOf("steps") + path)
            if (current == null) return null
        }

        return current
    }

    /**
     * Resolve a path under the "trigger" prefix.
     *
     * Format: trigger.<property>...
     *
     * @param path Path segments after "trigger" (e.g., ["entity", "name"])
     * @param dataStore Workflow datastore
     * @return Resolved value, or null if not found
     * @throws IllegalStateException if trigger is not set
     */
    private fun resolveTriggerPath(path: List<String>, dataStore: WorkflowDataStore): Any? {
        val trigger = dataStore.getTrigger()
            ?: throw IllegalStateException("Trigger not set. Cannot resolve {{ trigger.* }}")

        var current: Any? = trigger.toMap()

        for (segment in path) {
            current = traverseProperty(current, segment, listOf("trigger") + path)
            if (current == null) return null
        }

        return current
    }

    /**
     * Resolve a path under the "variables" prefix.
     *
     * Format: variables.<name>[.<nestedProperty>...]
     *
     * @param path Path segments after "variables" (e.g., ["counter"] or ["user", "name"])
     * @param dataStore Workflow datastore
     * @return Resolved value, or null if not found
     */
    private fun resolveVariablesPath(path: List<String>, dataStore: WorkflowDataStore): Any? {
        if (path.isEmpty()) {
            throw IllegalArgumentException("Template path must include variable name: variables.<name>")
        }

        val varName = path[0]
        var current: Any? = dataStore.getVariable(varName)

        if (current == null) {
            logger.debug { "Variable '$varName' not found" }
            return null
        }

        // Traverse nested properties if path continues
        for (i in 1 until path.size) {
            current = traverseProperty(current, path[i], listOf("variables") + path)
            if (current == null) return null
        }

        return current
    }

    /**
     * Resolve a path under the "loops" prefix.
     *
     * Format: loops.<loopId>.<property>
     *
     * Available properties:
     * - currentIndex: Zero-based index
     * - currentItem: Current item being processed
     * - totalItems: Total count of items
     * - loopId: Loop identifier
     *
     * @param path Path segments after "loops" (e.g., ["processItems", "currentItem", "id"])
     * @param dataStore Workflow datastore
     * @return Resolved value, or null if not found
     */
    private fun resolveLoopsPath(path: List<String>, dataStore: WorkflowDataStore): Any? {
        if (path.isEmpty()) {
            throw IllegalArgumentException("Template path must include loop name: loops.<loopId>")
        }

        val loopId = path[0]
        val loopContext = dataStore.getLoopContext(loopId)

        if (loopContext == null) {
            logger.debug { "Loop context '$loopId' not found" }
            return null
        }

        // Convert to map for traversal
        var current: Any? = mapOf(
            "currentIndex" to loopContext.currentIndex,
            "currentItem" to loopContext.currentItem,
            "totalItems" to loopContext.totalItems,
            "loopId" to loopContext.loopId
        )

        for (i in 1 until path.size) {
            current = traverseProperty(current, path[i], listOf("loops") + path)
            if (current == null) return null
        }

        return current
    }

    /**
     * Helper to traverse a property on a value.
     *
     * @param current Current value being traversed
     * @param segment Property name to access
     * @param fullPath Full path for logging context
     * @return Property value, or null if not accessible
     */
    private fun traverseProperty(current: Any?, segment: String, fullPath: List<String>): Any? {
        return when (current) {
            null -> {
                logger.debug { "Cannot traverse '$segment' - value is null. Path: $fullPath" }
                null
            }

            is Map<*, *> -> {
                val value = current[segment]
                if (value == null) {
                    logger.debug { "Property '$segment' not found. Path: $fullPath" }
                }
                value
            }

            else -> {
                logger.warn { "Cannot access '$segment' on ${current::class.simpleName}. Path: $fullPath" }
                null
            }
        }
    }

    /**
     * Recursively resolve all templates in a configuration map.
     *
     * Walks the config structure and resolves any template strings found.
     * Preserves the structure of maps and lists.
     *
     * @param config Configuration map potentially containing templates
     * @param dataStore Workflow datastore containing execution state
     * @return Resolved configuration with templates replaced by values
     */
    fun resolveAll(
        config: Map<String, Any?>,
        dataStore: WorkflowDataStore
    ): Map<String, Any?> {
        return config.mapValues { (_, value) ->
            resolveValue(value, dataStore)
        }
    }

    /**
     * Recursively resolve a value (handles nested maps and lists).
     *
     * @param value Value to resolve (may be primitive, map, list, or template)
     * @param dataStore Workflow datastore
     * @return Resolved value
     */
    private fun resolveValue(
        value: Any?,
        dataStore: WorkflowDataStore
    ): Any? {
        return when (value) {
            is String -> resolve(value, dataStore)
            is Map<*, *> -> {
                // Recursively resolve nested map
                @Suppress("UNCHECKED_CAST")
                (value as Map<String, Any?>).mapValues { (_, v) ->
                    resolveValue(v, dataStore)
                }
            }

            is List<*> -> {
                // Recursively resolve list items
                value.map { item ->
                    resolveValue(item, dataStore)
                }
            }

            else -> value // Primitives, nulls, other types pass through
        }
    }
}
