package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.models.enrichment.EnrichedTextResult
import riven.core.models.entity.knowledge.AttributeSection
import riven.core.models.entity.knowledge.CatalogBacklinkSection
import riven.core.models.entity.knowledge.ClusterSiblingSection
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.models.entity.knowledge.RelationalReferenceSection
import riven.core.models.connotation.SentimentMetadata
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

/**
 * Phase 2 adapter: maps [EntityKnowledgeView] → existing section formatters.
 * Phase 3 PROJ-08 deletes this service in favor of [EntityKnowledgeViewProjector.toEmbeddingText].
 *
 * Constructs enriched text from an [EntityKnowledgeView] snapshot using a section-based architecture.
 *
 * The output is human-readable text optimised for embedding — structured sections separated
 * by double newlines, each with a Markdown heading. Empty sections (no attributes, no relationships)
 * are omitted entirely rather than emitting empty headings.
 *
 * Section layout (Phase 2, preserving Phase 1 byte-identity for the canonical fixture):
 * 1. Entity Type Context — type name, optional definition, semantic group and lifecycle domain
 * 2. Identity — type classification of this entity
 * 3. Attributes — semantic-label:value pairs formatted according to classification (omitted when empty)
 * 4. Relationship Summaries — count-based summaries per relationship type (omitted when empty)
 * 5. Identity Cluster — cross-source cluster context, grouped by source type (omitted when empty)
 * 6. Relationship Definitions — NOT surfaced in Phase 2 (section removed from view model;
 *    typeNarrative.glossaryDefinitions carries glossary content instead)
 *
 * When the combined text would exceed the 27,000-character budget, sections are progressively
 * removed or compacted in priority order: compact Section 5, compact Section 4,
 * then drop FREETEXT and RELATIONAL_REFERENCE attributes from Section 3.
 * Sections 1 and 2 are never truncated. The Connotation Context section is bounded at
 * [MAX_CONNOTATION_SECTION_CHARS] and is preserved through every truncation step.
 *
 * Phase 2 intent: byte-identical embedding text output to Phase 1 for the canonical fixture
 * WHEN the fixture has zero glossary narratives and zero KNOWLEDGE backlinks. The Phase 1
 * fixture does NOT have either — by design. So byte-identity holds for the fixture.
 */
@Service
class SemanticTextBuilderService(
    private val logger: KLogger
) {

    companion object {
        private const val CHAR_BUDGET = 27_000
        private const val MAX_CONNOTATION_SECTION_CHARS = 300
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    }

    // ------ Public API ------

    /**
     * Phase 2 adapter: builds multi-section enriched text from the provided [EntityKnowledgeView].
     *
     * Maps view sections back to the Phase 1 formatting logic to produce byte-identical text
     * output for the canonical fixture (which has zero glossary narratives and zero KNOWLEDGE
     * backlinks). Phase 3 deletes this service in favor of [EntityKnowledgeViewProjector].
     *
     * Returns an [EnrichedTextResult] carrying the text, a truncated flag, and an estimated
     * token count. Budget is 27,000 characters (~6,750 tokens). When the budget is exceeded,
     * sections are progressively removed or compacted in priority order.
     *
     * @param view Assembled entity knowledge view.
     * @return EnrichedTextResult with text ready for embedding, truncation metadata, and token estimate.
     */
    fun buildText(view: EntityKnowledgeView): EnrichedTextResult {
        // Phase 2: glossaryDefinitions/glossaryNarrative carried in view for Phase 3
        // PROJ-12 parity IT; not consumed by buildText in Phase 2.
        // Phase 2: knowledgeBacklinks carried in view for Phase 3 PROJ-04/05;
        // not consumed by buildText in Phase 2.

        val sections = mutableListOf<String>()
        var truncated = false

        // Sections 1 and 2 are always included — never truncated
        sections.add(buildEntityTypeContextSection(view))
        sections.add(buildIdentitySection(view))

        // Build all optional sections at full quality
        val fullAttributes = buildAttributesSection(view)
        val fullRelationships = buildRelationshipSummariesSection(view)
        val fullCluster = buildClusterContextSection(view)
        // Phase 2: Relationship Definitions section removed from view model;
        // glossary definitions are in typeNarrative.glossaryDefinitions but NOT consumed in Phase 2 text.
        val fullConnotation: String? = view.sections.entityMetadata.sentiment?.let { buildConnotationContextSection(it) }

        val mandatoryLength = sections.sumOf { it.length } + (sections.size - 1) * 2
        val remaining = listOfNotNull(fullAttributes, fullRelationships, fullCluster, fullConnotation)
        val totalFull = mandatoryLength + remaining.sumOf { it.length } + remaining.size * 2

        if (totalFull <= CHAR_BUDGET) {
            // Everything fits at full quality
            sections.addAll(remaining)
        } else {
            // Progressive truncation — apply each step and check budget
            truncated = true

            // Connotation is bounded at MAX_CONNOTATION_SECTION_CHARS so it's safe to retain
            // through every truncation step — long entities are precisely the ones most likely
            // to need sentiment context preserved.

            // Step 1: Try without Section 6 (already removed in Phase 2 view model)
            val withoutDefs = listOfNotNull(fullAttributes, fullRelationships, fullCluster, fullConnotation)
            val totalStep1 = mandatoryLength + withoutDefs.sumOf { it.length } + withoutDefs.size * 2
            if (totalStep1 <= CHAR_BUDGET) {
                sections.addAll(withoutDefs)
            } else {
                // Step 2: Compact Section 5 (cluster → source names only)
                val compactCluster = buildClusterContextCompact(view)
                val step2Sections = listOfNotNull(fullAttributes, fullRelationships, compactCluster, fullConnotation)
                val totalStep2 = mandatoryLength + step2Sections.sumOf { it.length } + step2Sections.size * 2
                if (totalStep2 <= CHAR_BUDGET) {
                    sections.addAll(step2Sections)
                } else {
                    // Step 3: Compact Section 4 (relationship summaries → count + last activity only)
                    val compactRelationships = buildRelationshipSummariesCompact(view)
                    val step3Sections = listOfNotNull(fullAttributes, compactRelationships, compactCluster, fullConnotation)
                    val totalStep3 = mandatoryLength + step3Sections.sumOf { it.length } + step3Sections.size * 2
                    if (totalStep3 <= CHAR_BUDGET) {
                        sections.addAll(step3Sections)
                    } else {
                        // Step 4: Drop FREETEXT and RELATIONAL_REFERENCE from Section 3
                        val reducedAttributes = buildAttributesSectionReduced(view)
                        val step4Sections = listOfNotNull(reducedAttributes, compactRelationships, compactCluster, fullConnotation)
                        sections.addAll(step4Sections)
                    }
                }
            }
        }

        val text = sections.joinToString("\n\n")
        logger.debug { "Built enriched text with ${sections.size} sections for entity ${view.entityId} (truncated=$truncated)" }

        return EnrichedTextResult(
            text = text,
            truncated = truncated,
            estimatedTokens = text.length / 4,
        )
    }

    // ------ Private section builders ------

    /**
     * Section 1: Entity Type Context.
     *
     * Includes the entity type name, optional structured definition, semantic group,
     * and lifecycle domain. Always present.
     */
    private fun buildEntityTypeContextSection(view: EntityKnowledgeView): String {
        val narrative = view.sections.typeNarrative
        val lines = buildList {
            add("## Entity Type: ${narrative.entityTypeName}")
            narrative.metadataDefinition?.let { add(it) }
            add("Classification: ${narrative.semanticGroup} | Lifecycle: ${narrative.lifecycleDomain}")
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 2: Identity.
     *
     * Records the entity's type classification. Always present.
     */
    private fun buildIdentitySection(view: EntityKnowledgeView): String {
        return "## Identity\nType: ${view.sections.typeNarrative.entityTypeName}"
    }

    /**
     * Section 3: Attributes (full version).
     *
     * Lists each attribute as "semanticLabel: value", formatted according to the
     * attribute's [SemanticAttributeClassification]. Returns null when empty.
     */
    private fun buildAttributesSection(view: EntityKnowledgeView): String? {
        val attrs = view.sections.attributes
        if (attrs.isEmpty()) return null

        // Build a reference map from relationalReferences for RELATIONAL_REFERENCE resolution
        val referenceMap = view.sections.relationalReferences.associate { it.referencedEntityId to it.displayValue }

        val lines = buildList {
            add("## Attributes")
            attrs.forEach { attr ->
                add("${attr.semanticLabel}: ${formatAttributeValue(attr, referenceMap)}")
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 3 reduced: Attributes with FREETEXT and RELATIONAL_REFERENCE dropped.
     *
     * Used during truncation step 4. Returns null if no attributes remain after filtering.
     */
    private fun buildAttributesSectionReduced(view: EntityKnowledgeView): String? {
        val filteredAttributes = view.sections.attributes.filter {
            it.classification != SemanticAttributeClassification.FREETEXT &&
                it.classification != SemanticAttributeClassification.RELATIONAL_REFERENCE
        }
        if (filteredAttributes.isEmpty()) return null

        val referenceMap = view.sections.relationalReferences.associate { it.referencedEntityId to it.displayValue }

        val lines = buildList {
            add("## Attributes")
            filteredAttributes.forEach { attr ->
                add("${attr.semanticLabel}: ${formatAttributeValue(attr, referenceMap)}")
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 4: Relationship Summaries (full version).
     *
     * Phase 2: maps [CatalogBacklinkSection] list (formerly [EnrichmentRelationshipSummary]).
     * topCategories are absent from [CatalogBacklinkSection] — dropped per CONTEXT.md.
     * For the canonical fixture (empty topCategories in Phase 1), Section 4 output is byte-identical.
     * Returns null when empty.
     */
    private fun buildRelationshipSummariesSection(view: EntityKnowledgeView): String? {
        val backlinks = view.sections.catalogBacklinks
        if (backlinks.isEmpty()) return null

        val lines = buildList {
            add("## Relationships")
            backlinks.forEach { rel ->
                // Phase 2: topCategories absent from CatalogBacklinkSection (CONTEXT.md; was conditionally
                // emitted in Phase 1 but empty for the canonical fixture — byte-identity preserved).
                val activityClause = rel.latestActivityAt?.let { ", last activity: $it" } ?: ""
                add("${rel.relationshipName}: ${rel.count} total$activityClause")
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 4 compact: Relationship summaries with only count and last activity.
     *
     * Used during truncation step 3. Returns null when empty.
     */
    private fun buildRelationshipSummariesCompact(view: EntityKnowledgeView): String? {
        val backlinks = view.sections.catalogBacklinks
        if (backlinks.isEmpty()) return null

        val lines = buildList {
            add("## Relationships")
            backlinks.forEach { rel ->
                val activityClause = rel.latestActivityAt?.let { ", last activity: $it" } ?: ""
                add("${rel.relationshipName}: ${rel.count} total$activityClause")
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 5: Identity Cluster (full version).
     *
     * Phase 2: maps [ClusterSiblingSection] list (shape identical to former [EnrichmentClusterMemberContext]).
     * Shows other cluster members grouped by source type with entity type names.
     * Returns null when no cluster members present.
     */
    private fun buildClusterContextSection(view: EntityKnowledgeView): String? {
        val siblings = view.sections.clusterSiblings
        if (siblings.isEmpty()) return null

        val groupedBySource = siblings.groupBy { it.sourceType }
        val sourceParts = groupedBySource.entries.map { (sourceName, members) ->
            val typeNames = members.joinToString(", ") { it.entityTypeName }
            "$sourceName ($typeNames)"
        }
        val totalSources = groupedBySource.size + 1 // +1 for current entity
        return "## Identity Cluster\n$totalSources sources: ${sourceParts.joinToString(", ")}"
    }

    /**
     * Section 5 compact: Cluster context with source names only (no entity type names).
     *
     * Used during truncation step 2. Returns null when no cluster members.
     */
    private fun buildClusterContextCompact(view: EntityKnowledgeView): String? {
        val siblings = view.sections.clusterSiblings
        if (siblings.isEmpty()) return null

        val sourceNames = siblings.map { it.sourceType }.distinct()
        return "## Identity Cluster\nSources: ${sourceNames.joinToString(", ")}"
    }

    /**
     * Section 7: Connotation Context. Emitted when the view's entityMetadata carries
     * ANALYZED SENTIMENT metadata. Bounded ≤ MAX_CONNOTATION_SECTION_CHARS.
     */
    private fun buildConnotationContextSection(sentiment: SentimentMetadata): String {
        val score = sentiment.sentiment?.let { "%.2f".format(it) } ?: "—"
        val label = sentiment.sentimentLabel?.name ?: "UNKNOWN"
        val themesText = sentiment.themes.joinToString(", ").let {
            if (it.isEmpty()) "" else " | Themes: $it"
        }
        val raw = "## Connotation Context\nSentiment: $label ($score)$themesText"
        return raw.take(MAX_CONNOTATION_SECTION_CHARS)
    }

    // ------ Private formatting helpers ------

    /**
     * Formats an attribute value according to its [SemanticAttributeClassification].
     *
     * - TEMPORAL: formatted as "Month Day, Year (N units ago)"
     * - FREETEXT: verbatim up to 500 chars, truncated with "..." if longer
     * - RELATIONAL_REFERENCE: resolved to display name from referenceMap, fallback "[reference not resolved]"
     * - All others (IDENTIFIER, CATEGORICAL, QUANTITATIVE, null): raw value
     *
     * @param attr The attribute section carrying the value and classification.
     * @param referenceMap UUID → display value map built from view.sections.relationalReferences.
     */
    private fun formatAttributeValue(attr: AttributeSection, referenceMap: Map<UUID, String>): String {
        val raw = attr.value ?: return "[not set]"
        return when (attr.classification) {
            SemanticAttributeClassification.TEMPORAL -> formatTemporal(raw)
            SemanticAttributeClassification.FREETEXT -> if (raw.length > 500) "${raw.take(500)}..." else raw
            SemanticAttributeClassification.RELATIONAL_REFERENCE -> resolveReference(raw, referenceMap)
            else -> raw
        }
    }

    /**
     * Formats a temporal string as "Month Day, Year (relative expression)".
     *
     * Expects ISO-8601 format (ZonedDateTime). Falls back to the raw value if parsing fails.
     */
    private fun formatTemporal(raw: String): String {
        return try {
            val dt = ZonedDateTime.parse(raw)
            val formatted = dt.format(DATE_FORMATTER)
            val relative = relativeDate(dt)
            "$formatted ($relative)"
        } catch (e: DateTimeParseException) {
            logger.debug { "Failed to parse temporal value '$raw' — falling back to raw: ${e.message}" }
            raw
        }
    }

    /**
     * Produces a human-readable relative date expression from a [ZonedDateTime] to now.
     *
     * Past dates render as "N units ago"; future dates as "in N units". Dates within
     * the current day render as "today" regardless of direction.
     */
    private fun relativeDate(dt: ZonedDateTime): String {
        val now = ZonedDateTime.now()
        val rawDays = ChronoUnit.DAYS.between(dt, now)
        val days = abs(rawDays)
        val isFuture = rawDays < 0

        if (days < 1) return "today"

        val expression = when {
            days < 7 -> "$days days"
            days < 30 -> "${days / 7} weeks"
            days < 365 -> "${days / 30} months"
            else -> "${days / 365} years"
        }

        return if (isFuture) "in $expression" else "$expression ago"
    }

    /**
     * Resolves a UUID string to its display name in the reference map.
     *
     * Returns "[reference not resolved]" if the UUID is invalid or not found in the map.
     * The referenceMap is built from [EntityKnowledgeView.sections.relationalReferences],
     * replacing Phase 1's [EnrichmentContext.referencedEntityIdentifiers].
     */
    private fun resolveReference(raw: String, referenceMap: Map<UUID, String>): String {
        return try {
            val uuid = UUID.fromString(raw)
            referenceMap[uuid] ?: "[reference not resolved]"
        } catch (e: IllegalArgumentException) {
            "[reference not resolved]"
        }
    }
}
