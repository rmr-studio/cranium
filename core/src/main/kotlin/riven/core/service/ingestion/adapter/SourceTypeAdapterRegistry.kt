package riven.core.service.ingestion.adapter

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import riven.core.enums.integration.SourceType

/**
 * Assembles the `Map<SourceType, IngestionAdapter>` consumed by Phase 4's
 * `IngestionOrchestrator`. Inspects the [SourceTypeAdapter] annotation on each
 * registered [IngestionAdapter] bean to build the map.
 *
 * Every `@Component` implementing [IngestionAdapter] must declare its
 * [SourceType] via `@SourceTypeAdapter(...)`; missing or duplicate declarations
 * are fail-fast at application startup.
 */
@Configuration
class SourceTypeAdapterRegistry {

    @Bean
    fun sourceTypeAdapterMap(adapters: List<IngestionAdapter>): Map<SourceType, IngestionAdapter> {
        val map = adapters.associateBy { adapter ->
            val annotation = adapter::class.java.getAnnotation(SourceTypeAdapter::class.java)
                ?: error(
                    "IngestionAdapter ${adapter::class.simpleName} is missing @SourceTypeAdapter annotation. " +
                        "Every adapter @Component must declare its SourceType."
                )
            annotation.value
        }
        require(map.size == adapters.size) {
            "Duplicate SourceType registration across adapter beans: " +
                adapters.joinToString { it::class.simpleName ?: "?" }
        }
        return map
    }
}
