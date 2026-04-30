package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.enums.entity.semantics.SemanticAttributeClassification

import riven.core.models.connotation.SentimentMetadata
import riven.core.models.enrichment.EnrichedTextResult
import riven.core.models.enrichment.EnrichmentAttributeContext
import riven.core.models.enrichment.EnrichmentContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

/**
 * Constructs enriched text from an [EnrichmentContext] snapshot using a section-based architecture.
 *
 * The output is human-readable text optimised for embedding — structured sections separated
 * by double newlines, each with a Markdown heading. Empty sections (no attributes, no relationships)
 * are omitted entirely rather than emitting empty headings.
 *
 * Section layout:
 * 1. Entity Type Context — type name, optional definition, semantic group and lifecycle domain
 * 2. Identity — type classification of this entity
 * 3. Attributes — semantic-label:value pairs formatted according to classification (omitted when empty)
 * 4. Relationship Summaries — count-based summaries per relationship type (omitted when empty)
 * 5. Identity Cluster — cross-source cluster context, grouped by source type (omitted when empty)
 * 6. Relationship Definitions — semantic meaning of each relationship type (omitted when empty)
 *
 * When the combined text would exceed the 27,000-character budget, sections are progressively
 * removed or compacted in priority order: remove Section 6, compact Section 5, compact Section 4,
 * then drop FREETEXT and RELATIONAL_REFERENCE attributes from Section 3.
 * Sections 1 and 2 are never truncated. The Connotation Context section is bounded at
 * [MAX_CONNOTATION_SECTION_CHARS] and is preserved through every truncation step.
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
     * Builds multi-section enriched text from the provided [EnrichmentContext].
     *
     * Returns an [EnrichedTextResult] carrying the text, a truncated flag, and an estimated
     * token count. Budget is 27,000 characters (~6,750 tokens). When the budget is exceeded,
     * sections are progressively removed or compacted in priority order.
     *
     * @param context Snapshot of entity state including type metadata, attributes, and relationship summaries.
     * @return EnrichedTextResult with text ready for embedding, truncation metadata, and token estimate.
     */
    fun buildText(context: EnrichmentContext): EnrichedTextResult {
        val sections = mutableListOf<String>()
        var truncated = false

        // Sections 1 and 2 are always included — never truncated
        sections.add(buildEntityTypeContextSection(context))
        sections.add(buildIdentitySection(context))

        // Build all optional sections at full quality
        val fullAttributes = buildAttributesSection(context)
        val fullRelationships = buildRelationshipSummariesSection(context)
        val fullCluster = buildClusterContextSection(context)
        val fullDefinitions = buildRelationshipSemanticDefinitionsSection(context)
        val fullConnotation: String? = context.sentiment?.let { buildConnotationContextSection(it) }

        val mandatoryLength = sections.sumOf { it.length } + (sections.size - 1) * 2
        val remaining = listOfNotNull(fullAttributes, fullRelationships, fullCluster, fullDefinitions, fullConnotation)
        val totalFull = mandatoryLength + remaining.sumOf { it.length } + remaining.size * 2

        if (totalFull <= CHAR_BUDGET) {
            // Everything fits at full quality
            sections.addAll(remaining)
        } else {
            // Progressive truncation — apply each step and check budget
            truncated = true

            // Connotation is bounded at MAX_CONNOTATION_SECTION_CHARS so it's safe to retain
            // through every truncation step — long entities are precisely the ones most likely
            // to need sentiment context preserved. Place last so the assembled order matches
            // the full-quality path (definitions/connotation tail).

            // Step 1: Try without Section 6
            val withoutDefs = listOfNotNull(fullAttributes, fullRelationships, fullCluster, fullConnotation)
            val totalStep1 = mandatoryLength + withoutDefs.sumOf { it.length } + withoutDefs.size * 2
            if (totalStep1 <= CHAR_BUDGET) {
                sections.addAll(withoutDefs)
            } else {
                // Step 2: Compact Section 5 (cluster → source names only)
                val compactCluster = buildClusterContextCompact(context)
                val step2Sections = listOfNotNull(fullAttributes, fullRelationships, compactCluster, fullConnotation)
                val totalStep2 = mandatoryLength + step2Sections.sumOf { it.length } + step2Sections.size * 2
                if (totalStep2 <= CHAR_BUDGET) {
                    sections.addAll(step2Sections)
                } else {
                    // Step 3: Compact Section 4 (relationship summaries → count + last activity only)
                    val compactRelationships = buildRelationshipSummariesCompact(context)
                    val step3Sections = listOfNotNull(fullAttributes, compactRelationships, compactCluster, fullConnotation)
                    val totalStep3 = mandatoryLength + step3Sections.sumOf { it.length } + step3Sections.size * 2
                    if (totalStep3 <= CHAR_BUDGET) {
                        sections.addAll(step3Sections)
                    } else {
                        // Step 4: Drop FREETEXT and RELATIONAL_REFERENCE from Section 3
                        val reducedAttributes = buildAttributesSectionReduced(context)
                        val step4Sections = listOfNotNull(reducedAttributes, compactRelationships, compactCluster, fullConnotation)
                        sections.addAll(step4Sections)
                    }
                }
            }
        }

        val text = sections.joinToString("\n\n")
        logger.debug { "Built enriched text with ${sections.size} sections for entity ${context.entityId} (truncated=$truncated)" }

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
    private fun buildEntityTypeContextSection(context: EnrichmentContext): String {
        val lines = buildList {
            add("## Entity Type: ${context.entityTypeName}")
            context.entityTypeDefinition?.let { add(it) }
            add("Classification: ${context.semanticGroup} | Lifecycle: ${context.lifecycleDomain}")
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 2: Identity.
     *
     * Records the entity's type classification. Always present.
     */
    private fun buildIdentitySection(context: EnrichmentContext): String {
        return "## Identity\nType: ${context.entityTypeName}"
    }

    /**
     * Section 3: Attributes (full version).
     *
     * Lists each attribute as "semanticLabel: value", formatted according to the
     * attribute's [SemanticAttributeClassification]. Returns null when empty.
     */
    private fun buildAttributesSection(context: EnrichmentContext): String? {
        if (context.attributes.isEmpty()) return null

        val lines = buildList {
            add("## Attributes")
            context.attributes.forEach { attr ->
                add("${attr.semanticLabel}: ${formatAttributeValue(attr, context)}")
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 3 reduced: Attributes with FREETEXT and RELATIONAL_REFERENCE dropped.
     *
     * Used during truncation step 4. Returns null if no attributes remain after filtering.
     */
    private fun buildAttributesSectionReduced(context: EnrichmentContext): String? {
        val filteredAttributes = context.attributes.filter {
            it.classification != SemanticAttributeClassification.FREETEXT &&
                it.classification != SemanticAttributeClassification.RELATIONAL_REFERENCE
        }
        if (filteredAttributes.isEmpty()) return null

        val lines = buildList {
            add("## Attributes")
            filteredAttributes.forEach { attr ->
                add("${attr.semanticLabel}: ${formatAttributeValue(attr, context)}")
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 4: Relationship Summaries (full version).
     *
     * Lists each relationship with count, top categories, and last activity.
     * Returns null when empty.
     */
    private fun buildRelationshipSummariesSection(context: EnrichmentContext): String? {
        if (context.relationshipSummaries.isEmpty()) return null

        val lines = buildList {
            add("## Relationships")
            context.relationshipSummaries.forEach { rel ->
                val categoriesClause = if (rel.topCategories.isNotEmpty()) {
                    ", top categories: ${rel.topCategories.joinToString(", ")}"
                } else ""
                val activityClause = rel.latestActivityAt?.let { ", last activity: $it" } ?: ""
                add("${rel.relationshipName}: ${rel.count} total$categoriesClause$activityClause")
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 4 compact: Relationship summaries with only count and last activity.
     *
     * Used during truncation step 3. Returns null when empty.
     */
    private fun buildRelationshipSummariesCompact(context: EnrichmentContext): String? {
        if (context.relationshipSummaries.isEmpty()) return null

        val lines = buildList {
            add("## Relationships")
            context.relationshipSummaries.forEach { rel ->
                val activityClause = rel.latestActivityAt?.let { ", last activity: $it" } ?: ""
                add("${rel.relationshipName}: ${rel.count} total$activityClause")
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Section 5: Identity Cluster (full version).
     *
     * Shows other cluster members grouped by source type with entity type names.
     * Returns null when no cluster members present.
     */
    private fun buildClusterContextSection(context: EnrichmentContext): String? {
        if (context.clusterMembers.isEmpty()) return null

        val groupedBySource = context.clusterMembers.groupBy { it.sourceType.name }
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
    private fun buildClusterContextCompact(context: EnrichmentContext): String? {
        if (context.clusterMembers.isEmpty()) return null

        val sourceNames = context.clusterMembers.map { it.sourceType.name }.distinct()
        return "## Identity Cluster\nSources: ${sourceNames.joinToString(", ")}"
    }

    /**
     * Section 6: Relationship Semantic Definitions.
     *
     * Shows the semantic meaning of each relationship type that has a non-null definition.
     * Returns null when list is empty or all definitions are null.
     */
    private fun buildRelationshipSemanticDefinitionsSection(context: EnrichmentContext): String? {
        if (context.relationshipDefinitions.isEmpty()) return null

        val definitionLines = context.relationshipDefinitions
            .filter { it.definition != null }
            .map { "${it.name}: ${it.definition}" }

        if (definitionLines.isEmpty()) return null

        return (listOf("## Relationship Definitions") + definitionLines).joinToString("\n")
    }

    /**
     * Section 7: Connotation Context. Emitted when the enrichment context carries an
     * ANALYZED SENTIMENT axis. Bounded ≤ MAX_CONNOTATION_SECTION_CHARS to prevent runaway
     * theme lists from inflating the text.
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
     * - RELATIONAL_REFERENCE: resolved to display name from context, fallback "[reference not resolved]"
     * - All others (IDENTIFIER, CATEGORICAL, QUANTITATIVE, null): raw value
     */
    private fun formatAttributeValue(attr: EnrichmentAttributeContext, context: EnrichmentContext): String {
        val raw = attr.value ?: return "[not set]"
        return when (attr.classification) {
            SemanticAttributeClassification.TEMPORAL -> formatTemporal(raw)
            SemanticAttributeClassification.FREETEXT -> if (raw.length > 500) "${raw.take(500)}..." else raw
            SemanticAttributeClassification.RELATIONAL_REFERENCE -> resolveReference(raw, context)
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
     * Resolves a UUID string to its display name in [EnrichmentContext.referencedEntityIdentifiers].
     *
     * Returns "[reference not resolved]" if the UUID is invalid or not found in the map.
     */
    private fun resolveReference(raw: String, context: EnrichmentContext): String {
        return try {
            val uuid = UUID.fromString(raw)
            context.referencedEntityIdentifiers[uuid] ?: "[reference not resolved]"
        } catch (e: IllegalArgumentException) {
            "[reference not resolved]"
        }
    }
}
