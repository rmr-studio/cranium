package cranium.core.service.ingestion.adapter

import org.springframework.beans.factory.annotation.Qualifier
import cranium.core.enums.integration.SourceType

/**
 * Marks an [IngestionAdapter] `@Component` as the implementation for a given
 * [SourceType]. Phase 4's `IngestionOrchestrator` consumes a
 * `Map<SourceType, IngestionAdapter>` assembled from adapters carrying this
 * qualifier.
 *
 * Annotation-only in Phase 1. The `@Configuration` factory that assembles the
 * registry lands in Plan 01-03 alongside the first concrete adapter
 * (NangoAdapter) so the wiring has something to exercise.
 */
@Qualifier
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SourceTypeAdapter(val value: SourceType)
