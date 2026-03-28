package riven.core.service.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Service
import riven.core.configuration.properties.ManifestConfigurationProperties
import riven.core.enums.catalog.ManifestType
import riven.core.models.catalog.ScannedManifest

/**
 * Scans classpath for manifest files (models, templates, integrations),
 * parses JSON, and validates each against its JSON Schema.
 * Invalid or unparseable manifests are skipped with WARN logging.
 */
@Service
class ManifestScannerService(
    private val resourcePatternResolver: ResourcePatternResolver,
    private val objectMapper: ObjectMapper,
    private val manifestProperties: ManifestConfigurationProperties,
    private val logger: KLogger
) {
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909)

    // ------ Public Scan Methods ------

    /** Scans classpath models directory for model manifests. Key derived from filename. */
    fun scanModels(): List<ScannedManifest> {
        val resources = safeGetResources("${manifestProperties.basePath}/models/*.json")
        return resources.mapNotNull { resource ->
            val filename = resource.filename ?: return@mapNotNull null
            val key = filename.removeSuffix(".json")
            parseAndValidate(resource, key, ManifestType.MODEL, "manifests/schemas/model.schema.json")
        }
    }

    /** Scans classpath lifecycle-spine directory for template manifests. Key derived from directory name. */
    fun scanTemplates(): List<ScannedManifest> {
        val resources = safeGetResources("${manifestProperties.basePath}/lifecycle-spine/*/manifest.json")
        return resources.mapNotNull { resource ->
            val key = extractDirectoryName(resource, "lifecycle-spine")
            parseAndValidate(resource, key, ManifestType.TEMPLATE, "manifests/schemas/template.schema.json")
        }
    }

    /** Scans classpath integrations directory for integration manifests. Key derived from directory name. */
    fun scanIntegrations(): List<ScannedManifest> {
        val resources = safeGetResources("${manifestProperties.basePath}/integrations/*/manifest.json")
        return resources.mapNotNull { resource ->
            val key = extractDirectoryName(resource, "integrations")
            parseAndValidate(resource, key, ManifestType.INTEGRATION, "manifests/schemas/integration.schema.json")
        }
    }

    // ------ Private Helpers ------

    /**
     * Parses a resource as JSON, validates against the given schema path.
     * Returns null (with WARN log) on parse failure or validation errors.
     */
    private fun parseAndValidate(
        resource: Resource,
        key: String,
        type: ManifestType,
        schemaPath: String
    ): ScannedManifest? {
        return try {
            val jsonNode = objectMapper.readTree(resource.inputStream)
            val schema = loadSchema(schemaPath)
            val errors = schema.validate(jsonNode)
            if (errors.isNotEmpty()) {
                val errorMessages = errors.map { it.message }
                logger.warn { "Manifest $key ($type) failed validation: $errorMessages" }
                return null
            }
            ScannedManifest(key = key, type = type, json = jsonNode)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse manifest $key ($type)" }
            null
        }
    }

    /** Loads a JSON Schema from classpath for validation. Strips $id to avoid URI resolution issues with networknt 1.0.83. */
    private fun loadSchema(path: String): JsonSchema {
        val schemaResource = resourcePatternResolver.getResource("classpath:$path")
        val schemaNode = objectMapper.readTree(schemaResource.inputStream)
        // networknt 1.0.83 chokes on relative $id URIs; remove them since we don't need URI-based resolution
        if (schemaNode is ObjectNode) {
            schemaNode.remove("\$id")
            schemaNode.remove("\$schema")
        }
        return schemaFactory.getSchema(schemaNode)
    }

    /** Returns resources matching the pattern, or empty array if the parent directory doesn't exist on classpath. */
    private fun safeGetResources(pattern: String): Array<Resource> {
        return try {
            resourcePatternResolver.getResources(pattern) ?: emptyArray()
        } catch (e: java.io.FileNotFoundException) {
            logger.warn(e) { "Manifest directory not found for pattern: $pattern" }
            emptyArray()
        }
    }

    /**
     * Extracts the directory name from a resource URL path.
     * E.g., for URL path containing /templates/saas-starter/manifest.json, returns "saas-starter".
     */
    private fun extractDirectoryName(resource: Resource, parentDir: String): String {
        val urlPath = resource.url.path
        val afterParent = urlPath.substringAfter("/$parentDir/")
        return afterParent.substringBefore("/")
    }
}
