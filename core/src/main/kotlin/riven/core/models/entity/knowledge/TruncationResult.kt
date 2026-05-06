package riven.core.models.entity.knowledge

/**
 * Output of [riven.core.service.enrichment.EntityKnowledgeViewProjector.toEmbeddingText].
 *
 * Carries the final embedding text string and per-section telemetry for observability.
 * Phase 2 stub returns TruncationResult(text = "", telemetry = emptyList()); Phase 3 PROJ-01..PROJ-06
 * fills both fields with real content.
 *
 * Note: tokenCount is intentionally absent (Decision 8A — charCount over tokenCount).
 * If you are considering adding tokenCount, consult the feature design doc first.
 *
 * @property text The assembled embedding text after truncation phases have been applied.
 * @property telemetry Per-section telemetry list describing how many items were kept vs dropped.
 */
data class TruncationResult(
    val text: String,
    val telemetry: List<SectionTelemetry>,
)

/**
 * Per-section telemetry entry produced by each truncation phase in Phase 3.
 *
 * The [section] name matches the corresponding [KnowledgeSections] property name, allowing
 * consumers to correlate telemetry with the section that was processed.
 *
 * @property section Section property name — matches KnowledgeSections property names (e.g. "knowledgeBacklinks").
 * @property keptCount Number of items (or chars) kept after this truncation phase.
 * @property droppedCount Number of items (or chars) dropped by this truncation phase.
 * @property charCount Total character count emitted by this section after truncation.
 */
data class SectionTelemetry(
    val section: String,
    val keptCount: Int,
    val droppedCount: Int,
    val charCount: Int,
)
