package cranium.core.enums.knowledge

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Workspace entity-type keys for the knowledge domain. The [key] string matches the
 * `entity_types.key` column populated by [cranium.core.service.catalog.TemplateInstallationService]
 * and the workspace-scoped `findByworkspaceIdAndKey` lookup used by knowledge ingestion
 * services / projectors.
 */
enum class KnowledgeEntityTypeKey(val key: String) {
    @JsonProperty("note") NOTE("note"),
    @JsonProperty("glossary") GLOSSARY("glossary");

    companion object {
        fun fromKey(key: String): KnowledgeEntityTypeKey? = entries.firstOrNull { it.key == key }
    }
}
