package riven.core.models.connotation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.util.StdDateFormat
import tools.jackson.module.kotlin.KotlinModule
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Jackson round-trip + forward-compat coverage for [ConnotationMetadataEnvelope].
 *
 * The envelope is persisted as JSONB via Hypersistence's JsonBinaryType. Round-trip parity
 * matters because the envelope is the source of truth for non-pipeline consumers (frontend
 * display, debug tooling, future Layer 4 axes); shape drift breaks downstream readers.
 */
class ConnotationMetadataEnvelopeTest {

    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .defaultDateFormat(StdDateFormat().withTimeZone(TimeZone.getTimeZone("UTC")))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .build()

    @Test
    fun `Phase A envelope (placeholder SENTIMENT, populated RELATIONAL+STRUCTURAL) round-trips through Jackson`() {
        val now = ZonedDateTime.parse("2026-04-29T12:00:00Z")
        val envelope = ConnotationMetadataEnvelope(
            envelopeVersion = "v1",
            axes = ConnotationAxes(
                sentiment = SentimentAxis(),
                relational = RelationalAxis(
                    relationshipSummaries = listOf(
                        RelationshipSummarySnapshot(
                            definitionId = "def-1",
                            definitionName = "Support Tickets",
                            count = 3,
                            topCategories = listOf("priority: high (2), low (1)"),
                            latestActivityAt = "2026-04-28T10:00:00Z",
                        )
                    ),
                    clusterMembers = listOf(
                        ClusterMemberSnapshot(sourceType = SourceType.INTEGRATION, entityTypeName = "Customer"),
                    ),
                    relationalReferenceResolutions = listOf(
                        RelationalReferenceResolution(
                            attributeId = "attr-1",
                            targetEntityId = "entity-2",
                            targetIdentifierValue = "alice@example.com",
                        ),
                    ),
                    snapshotAt = now,
                ),
                structural = StructuralAxis(
                    entityTypeName = "Customer",
                    semanticGroup = SemanticGroup.CUSTOMER,
                    lifecycleDomain = LifecycleDomain.ACQUISITION,
                    entityTypeDefinition = "Person who has purchased a product",
                    schemaVersion = 7,
                    attributeClassifications = listOf(
                        AttributeClassificationSnapshot(
                            attributeId = "attr-1",
                            semanticLabel = "Email",
                            classification = SemanticAttributeClassification.IDENTIFIER,
                            schemaType = SchemaType.TEXT,
                        ),
                    ),
                    relationshipSemanticDefinitions = listOf(
                        RelationshipSemanticDefinitionSnapshot(
                            definitionName = "owns",
                            definitionText = "Customer owns Subscription",
                        ),
                    ),
                    snapshotAt = now,
                ),
            ),
            embeddedAt = now,
        )

        val json = mapper.writeValueAsString(envelope)
        val restored = mapper.readValue(json, ConnotationMetadataEnvelope::class.java)

        assertEquals(envelope, restored)
    }

    @Test
    fun `envelope JSON has axes keyed by SENTIMENT, RELATIONAL, STRUCTURAL`() {
        val envelope = ConnotationMetadataEnvelope(
            axes = ConnotationAxes(
                sentiment = SentimentAxis(),
                relational = RelationalAxis(snapshotAt = ZonedDateTime.now()),
                structural = StructuralAxis(
                    entityTypeName = "T",
                    semanticGroup = SemanticGroup.UNCATEGORIZED,
                    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                    schemaVersion = 1,
                    snapshotAt = ZonedDateTime.now(),
                ),
            ),
            embeddedAt = ZonedDateTime.now(),
        )

        val json = mapper.writeValueAsString(envelope)

        assertTrue(json.contains("\"SENTIMENT\""), "JSON should contain SENTIMENT axis key")
        assertTrue(json.contains("\"RELATIONAL\""), "JSON should contain RELATIONAL axis key")
        assertTrue(json.contains("\"STRUCTURAL\""), "JSON should contain STRUCTURAL axis key")
        assertTrue(json.contains("\"envelopeVersion\":\"v1\""), "JSON should contain envelopeVersion v1")
    }

    @Test
    fun `forward-compat - deserialize tolerates unknown future fields`() {
        val now = ZonedDateTime.parse("2026-04-29T12:00:00Z")
        val futureJson = """
            {
              "envelopeVersion": "v1",
              "axes": {
                "SENTIMENT": {
                  "status": "NOT_APPLICABLE",
                  "stalenessModel": "ON_SOURCE_TEXT_CHANGE",
                  "themes": [],
                  "futureFieldOnSentiment": "ignore-me"
                },
                "FUTURE_AXIS": { "x": 1 },
                "STRUCTURAL": {
                  "entityTypeName": "Customer",
                  "semanticGroup": "CUSTOMER",
                  "lifecycleDomain": "ACQUISITION",
                  "schemaVersion": 1,
                  "stalenessModel": "ON_TYPE_METADATA_CHANGE",
                  "snapshotAt": "$now",
                  "futureFieldOnStructural": 99
                }
              },
              "embeddedAt": "$now",
              "futureTopLevelField": true
            }
        """.trimIndent()

        val restored = mapper.readValue(futureJson, ConnotationMetadataEnvelope::class.java)

        assertEquals("v1", restored.envelopeVersion)
        assertEquals(ConnotationStatus.NOT_APPLICABLE, restored.axes.sentiment?.status)
        assertNull(restored.axes.relational, "RELATIONAL axis is absent in this fixture")
        assertEquals("Customer", restored.axes.structural?.entityTypeName)
    }

    @Test
    fun `defaults - SentimentAxis defaults to NOT_APPLICABLE status and ON_SOURCE_TEXT_CHANGE staleness`() {
        val sentiment = SentimentAxis()

        assertEquals(ConnotationStatus.NOT_APPLICABLE, sentiment.status)
        assertEquals(AxisStalenessModel.ON_SOURCE_TEXT_CHANGE, sentiment.stalenessModel)
        assertNull(sentiment.sentiment)
        assertNull(sentiment.sentimentLabel)
        assertTrue(sentiment.themes.isEmpty())
    }
}
