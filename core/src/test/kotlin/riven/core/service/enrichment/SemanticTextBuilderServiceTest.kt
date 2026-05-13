package cranium.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.connotation.ConnotationStatus
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.enums.integration.SourceType
import cranium.core.models.connotation.AnalysisTier
import cranium.core.models.connotation.SentimentLabel
import cranium.core.models.connotation.SentimentMetadata
import cranium.core.service.util.factory.enrichment.EnrichmentFactory
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Unit tests for SemanticTextBuilderService.
 *
 * No Spring context needed — service performs pure text construction from
 * EnrichmentContext with no external dependencies beyond a logger.
 */
class SemanticTextBuilderServiceTest {

    private val logger: KLogger = mock<KLogger>()
    private val service = SemanticTextBuilderService(logger)

    // ------ buildText full context ------

    @Nested
    inner class FullContext {

        @Test
        fun `buildText with full context includes all 6 sections`() {
            val refId = UUID.randomUUID()
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(semanticLabel = "Name", value = "Acme Corp"),
                ),
                clusterMembers = listOf(
                    EnrichmentFactory.enrichmentClusterMemberContext(sourceType = SourceType.INTEGRATION, entityTypeName = "Company")
                ),
                relationshipDefinitions = listOf(
                    EnrichmentFactory.enrichmentRelationshipDefinitionContext(name = "Support Tickets", definition = "Escalation records.")
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "## Entity Type:")
            assertContains(result.text, "## Identity")
            assertContains(result.text, "## Attributes")
            assertContains(result.text, "## Relationships")
            assertContains(result.text, "## Identity Cluster")
            assertContains(result.text, "## Relationship Definitions")
        }

        @Test
        fun `buildText sections are separated by double newlines`() {
            val context = EnrichmentFactory.enrichmentContext()

            val result = service.buildText(context)

            assertTrue(result.text.contains("\n\n"), "Sections should be separated by double newlines")
        }

        @Test
        fun `buildText returns EnrichedTextResult with truncated false when within budget`() {
            val context = EnrichmentFactory.enrichmentContext()

            val result = service.buildText(context)

            assertFalse(result.truncated, "Small context should not be truncated")
            assertEquals(result.text.length / 4, result.estimatedTokens)
        }
    }

    // ------ Section 1: Entity Type Context ------

    @Nested
    inner class EntityTypeContextSection {

        @Test
        fun `buildText includes entity type name in section 1`() {
            val context = EnrichmentFactory.enrichmentContext(entityTypeName = "Customer")

            val result = service.buildText(context)

            assertContains(result.text, "## Entity Type: Customer")
        }

        @Test
        fun `buildText includes entity type definition when non-null`() {
            val definition = "A person or organization that purchases products or services."
            val context = EnrichmentFactory.enrichmentContext(entityTypeDefinition = definition)

            val result = service.buildText(context)

            assertContains(result.text, definition)
        }

        @Test
        fun `buildText omits entity type definition line when null`() {
            val context = EnrichmentFactory.enrichmentContext(
                entityTypeDefinition = null,
                entityTypeName = "Customer"
            )

            val result = service.buildText(context)

            assertContains(result.text, "## Entity Type: Customer")
            // Section 1 should still be present but no definition text
            assertContains(result.text, "Classification:")
        }

        @Test
        fun `buildText includes semantic group and lifecycle domain in section 1`() {
            val context = EnrichmentFactory.enrichmentContext(
                semanticGroup = SemanticGroup.CUSTOMER,
                lifecycleDomain = LifecycleDomain.ACQUISITION
            )

            val result = service.buildText(context)

            assertContains(result.text, "CUSTOMER")
            assertContains(result.text, "ACQUISITION")
        }
    }

    // ------ Section 2: Identity ------

    @Nested
    inner class IdentitySection {

        @Test
        fun `buildText includes identity section with entity type name`() {
            val context = EnrichmentFactory.enrichmentContext(entityTypeName = "Support Ticket")

            val result = service.buildText(context)

            assertContains(result.text, "## Identity")
            assertContains(result.text, "Type: Support Ticket")
        }
    }

    // ------ Section 3: Attributes ------

    @Nested
    inner class AttributesSection {

        @Test
        fun `buildText includes attributes section when attributes are present`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Acquisition Channel",
                        value = "Organic Search"
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "## Attributes")
            assertContains(result.text, "Acquisition Channel: Organic Search")
        }

        @Test
        fun `buildText omits attributes section when empty`() {
            val context = EnrichmentFactory.enrichmentContext(attributes = emptyList())

            val result = service.buildText(context)

            assertFalse(result.text.contains("## Attributes"), "Attributes section should be omitted when empty")
        }

        @Test
        fun `buildText formats null attribute values as not set`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Industry",
                        value = null,
                        schemaType = SchemaType.TEXT
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Industry: [not set]")
        }

        @Test
        fun `buildText uses semantic label for attributes not raw attribute key`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Acquisition Channel",
                        value = "Referral"
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Acquisition Channel: Referral")
        }

        // -- Type-aware formatting --

        @Test
        fun `buildText formats TEMPORAL attribute with valid ISO datetime as human-readable relative date`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Founded At",
                        value = "2020-01-15T00:00:00Z",
                        classification = SemanticAttributeClassification.TEMPORAL
                    )
                )
            )

            val result = service.buildText(context)

            // Should contain the formatted date like "January 15, 2020 (N years ago)"
            assertContains(result.text, "Founded At:")
            assertContains(result.text, "January 15, 2020")
            assertTrue(result.text.contains("ago") || result.text.contains("today"),
                "Should contain a relative date expression")
        }

        @Test
        fun `buildText falls back to raw value for unparseable TEMPORAL attribute`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Founded Year",
                        value = "2020",
                        classification = SemanticAttributeClassification.TEMPORAL
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Founded Year: 2020")
        }

        /**
         * Regression for PR #174 (r3056654650): future TEMPORAL values used to render as
         * "N days ago" because relativeDate took the absolute value before formatting.
         * Future dates must render as "in N …".
         */
        @Test
        fun `buildText renders future TEMPORAL values with 'in N' prefix not 'ago' suffix`() {
            val futureDate = java.time.ZonedDateTime.now().plusDays(45)
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Renewal Date",
                        value = futureDate.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        classification = SemanticAttributeClassification.TEMPORAL
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Renewal Date:")
            assertTrue(result.text.contains("in "),
                "Future date should render with 'in N' prefix, got: ${result.text}")
            assertFalse(result.text.contains("ago"),
                "Future date should not use 'ago' suffix")
        }

        /**
         * Regression for PR #174 (r3056654650): pin the past-date "ago" suffix to ensure
         * the future-date fix did not regress past-date rendering.
         */
        @Test
        fun `buildText renders past TEMPORAL values with 'ago' suffix`() {
            val pastDate = java.time.ZonedDateTime.now().minusDays(45)
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Closed Date",
                        value = pastDate.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        classification = SemanticAttributeClassification.TEMPORAL
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Closed Date:")
            assertTrue(result.text.contains("ago"),
                "Past date should render with 'ago' suffix, got: ${result.text}")
            assertFalse(result.text.contains("in "),
                "Past date should not use 'in N' prefix")
        }

        @Test
        fun `buildText formats FREETEXT attribute verbatim when under 500 chars`() {
            val shortText = "Brief description of the entity."
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Description",
                        value = shortText,
                        classification = SemanticAttributeClassification.FREETEXT
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Description: $shortText")
        }

        @Test
        fun `buildText truncates FREETEXT attribute at 500 chars with ellipsis`() {
            val longText = "A".repeat(600)
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Notes",
                        value = longText,
                        classification = SemanticAttributeClassification.FREETEXT
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Notes: ${"A".repeat(500)}...")
            assertFalse(result.text.contains("A".repeat(501)), "Should be truncated to 500 chars")
        }

        @Test
        fun `buildText resolves RELATIONAL_REFERENCE attribute to display name when found`() {
            val refId = UUID.randomUUID()
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Account Owner",
                        value = refId.toString(),
                        classification = SemanticAttributeClassification.RELATIONAL_REFERENCE
                    )
                ),
                referencedEntityIdentifiers = mapOf(refId to "Jane Smith")
            )

            val result = service.buildText(context)

            assertContains(result.text, "Account Owner: Jane Smith")
        }

        @Test
        fun `buildText shows reference not resolved when UUID not in map`() {
            val refId = UUID.randomUUID()
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Account Owner",
                        value = refId.toString(),
                        classification = SemanticAttributeClassification.RELATIONAL_REFERENCE
                    )
                ),
                referencedEntityIdentifiers = emptyMap()
            )

            val result = service.buildText(context)

            assertContains(result.text, "Account Owner: [reference not resolved]")
        }

        @Test
        fun `buildText shows reference not resolved when value is not a valid UUID`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Account Owner",
                        value = "not-a-uuid",
                        classification = SemanticAttributeClassification.RELATIONAL_REFERENCE
                    )
                ),
                referencedEntityIdentifiers = emptyMap()
            )

            val result = service.buildText(context)

            assertContains(result.text, "Account Owner: [reference not resolved]")
        }

        @Test
        fun `buildText formats IDENTIFIER attribute as raw value`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Email",
                        value = "user@example.com",
                        classification = SemanticAttributeClassification.IDENTIFIER
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Email: user@example.com")
        }

        @Test
        fun `buildText formats CATEGORICAL attribute as raw value`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Status",
                        value = "Active",
                        classification = SemanticAttributeClassification.CATEGORICAL
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Status: Active")
        }

        @Test
        fun `buildText formats QUANTITATIVE attribute as raw value`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Revenue",
                        value = "1500000",
                        classification = SemanticAttributeClassification.QUANTITATIVE
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Revenue: 1500000")
        }

        @Test
        fun `buildText formats null classification attribute as raw value`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(
                        semanticLabel = "Custom Field",
                        value = "some value",
                        classification = null
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Custom Field: some value")
        }
    }

    // ------ Section 4: Relationship Summaries ------

    @Nested
    inner class RelationshipSummariesSection {

        @Test
        fun `buildText includes relationships section when summaries are present`() {
            val context = EnrichmentFactory.enrichmentContext(
                relationshipSummaries = listOf(
                    EnrichmentFactory.enrichmentRelationshipSummary(
                        relationshipName = "Support Tickets",
                        count = 342
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "## Relationships")
            assertContains(result.text, "Support Tickets: 342 total")
        }

        @Test
        fun `buildText omits relationships section when empty`() {
            val context = EnrichmentFactory.enrichmentContext(relationshipSummaries = emptyList())

            val result = service.buildText(context)

            assertFalse(result.text.contains("## Relationships"), "Relationships section should be omitted when empty")
        }

        @Test
        fun `buildText formats relationship count correctly`() {
            val context = EnrichmentFactory.enrichmentContext(
                relationshipSummaries = listOf(
                    EnrichmentFactory.enrichmentRelationshipSummary(
                        relationshipName = "Invoices",
                        count = 12
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Invoices: 12 total")
        }

        @Test
        fun `buildText includes top categories in relationship summary when present`() {
            val context = EnrichmentFactory.enrichmentContext(
                relationshipSummaries = listOf(
                    EnrichmentFactory.enrichmentRelationshipSummary(
                        relationshipName = "Deals",
                        count = 5,
                        topCategories = listOf("Enterprise", "Mid-Market"),
                        latestActivityAt = "2024-03-15T10:00:00Z"
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "Enterprise")
            assertContains(result.text, "Mid-Market")
            assertContains(result.text, "2024-03-15T10:00:00Z")
        }
    }

    // ------ Section 5: Cluster Context ------

    @Nested
    inner class ClusterContextSection {

        @Test
        fun `buildText includes cluster context section when cluster members are present`() {
            val context = EnrichmentFactory.enrichmentContext(
                clusterMembers = listOf(
                    EnrichmentFactory.enrichmentClusterMemberContext(
                        sourceType = SourceType.INTEGRATION,
                        entityTypeName = "Company"
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "## Identity Cluster")
        }

        @Test
        fun `buildText omits cluster context section when cluster members empty`() {
            val context = EnrichmentFactory.enrichmentContext(clusterMembers = emptyList())

            val result = service.buildText(context)

            assertFalse(result.text.contains("## Identity Cluster"), "Cluster section should be absent when no members")
        }

        @Test
        fun `buildText cluster section shows source type and entity type names`() {
            val context = EnrichmentFactory.enrichmentContext(
                clusterMembers = listOf(
                    EnrichmentFactory.enrichmentClusterMemberContext(
                        sourceType = SourceType.INTEGRATION,
                        entityTypeName = "Company"
                    ),
                    EnrichmentFactory.enrichmentClusterMemberContext(
                        sourceType = SourceType.INTEGRATION,
                        entityTypeName = "Contact"
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "INTEGRATION")
            assertContains(result.text, "Company")
            assertContains(result.text, "Contact")
        }

        @Test
        fun `buildText cluster section groups members by source type`() {
            val context = EnrichmentFactory.enrichmentContext(
                clusterMembers = listOf(
                    EnrichmentFactory.enrichmentClusterMemberContext(
                        sourceType = SourceType.INTEGRATION,
                        entityTypeName = "Company"
                    ),
                    EnrichmentFactory.enrichmentClusterMemberContext(
                        sourceType = SourceType.IMPORT,
                        entityTypeName = "Account"
                    )
                )
            )

            val result = service.buildText(context)

            // Both source types should appear
            assertContains(result.text, "INTEGRATION")
            assertContains(result.text, "IMPORT")
        }

        @Test
        fun `buildText cluster section source count includes current entity`() {
            val context = EnrichmentFactory.enrichmentContext(
                clusterMembers = listOf(
                    EnrichmentFactory.enrichmentClusterMemberContext(
                        sourceType = SourceType.INTEGRATION,
                        entityTypeName = "Company"
                    )
                )
            )

            val result = service.buildText(context)

            // 1 cluster member (distinct source) + 1 for current entity = 2 sources
            assertContains(result.text, "2 sources")
        }
    }

    // ------ Section 6: Relationship Semantic Definitions ------

    @Nested
    inner class RelationshipDefinitionsSection {

        @Test
        fun `buildText includes relationship definitions section when definitions present`() {
            val context = EnrichmentFactory.enrichmentContext(
                relationshipDefinitions = listOf(
                    EnrichmentFactory.enrichmentRelationshipDefinitionContext(
                        name = "Support Tickets",
                        definition = "Escalation records from the help desk system."
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "## Relationship Definitions")
            assertContains(result.text, "Support Tickets: Escalation records from the help desk system.")
        }

        @Test
        fun `buildText omits relationship definitions section when list empty`() {
            val context = EnrichmentFactory.enrichmentContext(relationshipDefinitions = emptyList())

            val result = service.buildText(context)

            assertFalse(result.text.contains("## Relationship Definitions"), "Definitions section should be absent when empty")
        }

        @Test
        fun `buildText omits relationship definitions section when all definitions are null`() {
            val context = EnrichmentFactory.enrichmentContext(
                relationshipDefinitions = listOf(
                    EnrichmentFactory.enrichmentRelationshipDefinitionContext(
                        name = "Support Tickets",
                        definition = null
                    )
                )
            )

            val result = service.buildText(context)

            assertFalse(result.text.contains("## Relationship Definitions"), "Definitions section should be absent when all definitions null")
        }

        @Test
        fun `buildText includes only definitions with non-null text`() {
            val context = EnrichmentFactory.enrichmentContext(
                relationshipDefinitions = listOf(
                    EnrichmentFactory.enrichmentRelationshipDefinitionContext(
                        name = "Support Tickets",
                        definition = "Escalation records."
                    ),
                    EnrichmentFactory.enrichmentRelationshipDefinitionContext(
                        name = "Deals",
                        definition = null
                    )
                )
            )

            val result = service.buildText(context)

            assertContains(result.text, "## Relationship Definitions")
            assertContains(result.text, "Support Tickets: Escalation records.")
            assertFalse(result.text.contains("Deals:"), "Should omit null-definition relationships")
        }
    }

    // ------ Edge cases ------

    @Nested
    inner class EdgeCases {

        @Test
        fun `buildText with empty attributes and empty relationships only has sections 1 and 2`() {
            val context = EnrichmentFactory.enrichmentContext(
                attributes = emptyList(),
                relationshipSummaries = emptyList()
            )

            val result = service.buildText(context)

            assertContains(result.text, "## Entity Type:")
            assertContains(result.text, "## Identity")
            assertFalse(result.text.contains("## Attributes"), "Attributes section should be absent")
            assertFalse(result.text.contains("## Relationships"), "Relationships section should be absent")
        }
    }

    // ------ Section 7: Connotation Context ------

    @Nested
    inner class ConnotationContextSection {

        @Test
        fun `Connotation Context section emits when SENTIMENT is ANALYZED`() {
            val sentiment = SentimentMetadata(
                sentiment = 0.8,
                sentimentLabel = SentimentLabel.VERY_POSITIVE,
                themes = listOf("billing", "fast resolution"),
                analysisTier = AnalysisTier.DETERMINISTIC,
                status = ConnotationStatus.ANALYZED,
            )
            val context = EnrichmentFactory.enrichmentContext(sentiment = sentiment)

            val result = service.buildText(context)

            assertContains(result.text, "## Connotation Context")
            assertContains(result.text, "Sentiment: VERY_POSITIVE")
            assertContains(result.text, "0.80")
            assertContains(result.text, "billing")
            assertContains(result.text, "fast resolution")
        }

        @Test
        fun `Connotation Context section is omitted when sentiment is null`() {
            val context = EnrichmentFactory.enrichmentContext(sentiment = null)

            val result = service.buildText(context)

            assertFalse(
                result.text.contains("Connotation Context"),
                "Connotation Context should be absent when sentiment is null"
            )
        }

        @Test
        fun `Connotation Context section is bounded under 300 chars`() {
            val longThemes = (1..50).map { "very-long-theme-name-with-many-characters-$it" }
            val sentiment = SentimentMetadata(
                sentiment = 0.5,
                sentimentLabel = SentimentLabel.POSITIVE,
                themes = longThemes,
                analysisTier = AnalysisTier.DETERMINISTIC,
                status = ConnotationStatus.ANALYZED,
            )
            val context = EnrichmentFactory.enrichmentContext(sentiment = sentiment)

            val result = service.buildText(context)

            val sectionText = result.text.substringAfter("## Connotation Context")
                .let { if (it.contains("\n##")) it.substringBefore("\n##") else it }
            // +"## Connotation Context".length to count the heading prefix that substringAfter strips
            val totalSectionLength = "## Connotation Context".length + sectionText.length
            assertTrue(
                totalSectionLength <= 300,
                "Connotation Context section should be bounded ≤ 300 chars, was $totalSectionLength"
            )
        }
    }

    // ------ Task 2: Budget-aware truncation ------

    @Nested
    inner class BudgetTruncation {

        /**
         * Creates a context that exceeds the 27,000 character budget by filling it with
         * many IDENTIFIER attributes with long names (bypass the 500-char FREETEXT cap),
         * many relationship summaries with long category strings, and many relationship
         * definitions with long definition text.
         */
        private fun massiveContext(): cranium.core.models.enrichment.EnrichmentContext {
            val longValue = "V".repeat(500)
            // 40 IDENTIFIER attributes × ~515 chars each ≈ 20,600 chars for section 3 alone
            val attributes = (1..40).map { i ->
                EnrichmentFactory.enrichmentAttributeContext(
                    semanticLabel = "Attribute Label $i",
                    value = longValue,
                    classification = SemanticAttributeClassification.IDENTIFIER
                )
            }
            val relationships = (1..10).map { i ->
                EnrichmentFactory.enrichmentRelationshipSummary(
                    relationshipName = "Relationship $i",
                    count = 100 + i,
                    topCategories = listOf("Enterprise", "Mid-Market", "SMB", "Strategic", "Growth"),
                    latestActivityAt = "2024-03-15T10:00:00Z"
                )
            }
            val definitions = (1..20).map { i ->
                EnrichmentFactory.enrichmentRelationshipDefinitionContext(
                    name = "Definition $i",
                    definition = "D".repeat(500)
                )
            }
            val clusterMembers = (1..5).map { i ->
                EnrichmentFactory.enrichmentClusterMemberContext(
                    sourceType = SourceType.INTEGRATION,
                    entityTypeName = "EntityType $i"
                )
            }
            return EnrichmentFactory.enrichmentContext(
                attributes = attributes,
                relationshipSummaries = relationships,
                clusterMembers = clusterMembers,
                relationshipDefinitions = definitions
            )
        }

        @Test
        fun `buildText returns truncated false for small normal context`() {
            val context = EnrichmentFactory.enrichmentContext()

            val result = service.buildText(context)

            assertFalse(result.truncated, "Normal-sized context should not be truncated")
        }

        @Test
        fun `buildText sets truncated true when any section removed`() {
            val context = massiveContext()

            val result = service.buildText(context)

            assertTrue(result.truncated, "Massive context should trigger truncation")
        }

        @Test
        fun `buildText always includes sections 1 and 2 regardless of budget pressure`() {
            val context = massiveContext()

            val result = service.buildText(context)

            assertContains(result.text, "## Entity Type:")
            assertContains(result.text, "## Identity")
        }

        @Test
        fun `buildText removes section 6 first when budget exceeded`() {
            // Context with enough relationship definitions to exceed budget but
            // not enough other content to require further truncation after removing section 6
            val definitions = (1..50).map { i ->
                EnrichmentFactory.enrichmentRelationshipDefinitionContext(
                    name = "Definition $i",
                    definition = "D".repeat(600)
                )
            }
            val context = EnrichmentFactory.enrichmentContext(
                attributes = listOf(
                    EnrichmentFactory.enrichmentAttributeContext(semanticLabel = "Name", value = "Acme Corp")
                ),
                relationshipSummaries = listOf(
                    EnrichmentFactory.enrichmentRelationshipSummary(count = 5)
                ),
                clusterMembers = listOf(
                    EnrichmentFactory.enrichmentClusterMemberContext()
                ),
                relationshipDefinitions = definitions
            )

            val result = service.buildText(context)

            assertTrue(result.truncated, "Should be truncated")
            assertFalse(result.text.contains("## Relationship Definitions"), "Section 6 should be removed first")
        }

        @Test
        fun `buildText estimatedTokens equals text length divided by 4`() {
            val context = EnrichmentFactory.enrichmentContext()

            val result = service.buildText(context)

            assertEquals(result.text.length / 4, result.estimatedTokens)
        }

        @Test
        fun `buildText with massive context does not exceed budget`() {
            val context = massiveContext()

            val result = service.buildText(context)

            assertTrue(result.text.length <= 27_000,
                "Final text should not exceed 27,000 chars, was ${result.text.length}")
        }

        /**
         * Connotation section is bounded at 300 chars and survives every truncation step
         * so long entities don't lose sentiment context. Verifies the section is still
         * present at the deepest truncation step (Step 4 — reduced attributes).
         */
        @Test
        fun `buildText preserves Connotation Context through all truncation steps`() {
            val sentiment = SentimentMetadata(
                sentiment = 0.8,
                sentimentLabel = SentimentLabel.VERY_POSITIVE,
                analysisVersion = "v1",
                analysisTier = AnalysisTier.DETERMINISTIC,
                status = ConnotationStatus.ANALYZED,
            )
            val base = massiveContext()
            val context = EnrichmentFactory.enrichmentContext(
                attributes = base.attributes,
                relationshipSummaries = base.relationshipSummaries,
                clusterMembers = base.clusterMembers,
                relationshipDefinitions = base.relationshipDefinitions,
                sentiment = sentiment,
            )

            val result = service.buildText(context)

            assertTrue(result.truncated, "Massive context should trigger truncation")
            assertContains(result.text, "## Connotation Context")
        }
    }
}
