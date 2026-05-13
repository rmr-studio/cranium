package cranium.core.models.connotation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.connotation.ConnotationStatus
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.enums.integration.SourceType
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.util.StdDateFormat
import tools.jackson.module.kotlin.KotlinModule
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Jackson round-trip + forward-compat coverage for [EntityMetadataSnapshot].
 *
 * The snapshot is persisted as JSONB via Hypersistence's JsonBinaryType. Round-trip parity
 * matters because the snapshot is the source of truth for non-pipeline consumers (frontend
 * display, debug tooling, future Layer 4 metadata categories); shape drift breaks downstream
 * readers.
 */
class EntityMetadataSnapshotTest {

    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .defaultDateFormat(StdDateFormat().withTimeZone(TimeZone.getTimeZone("UTC")))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .build()

    @Test
    fun `Phase A snapshot (placeholder SENTIMENT, populated RELATIONAL+STRUCTURAL) round-trips through Jackson`() {
        val now = ZonedDateTime.parse("2026-04-29T12:00:00Z")
        val snapshot = EntityMetadataSnapshot(
            snapshotVersion = "v1",
            metadata = EntityMetadata(
                sentiment = SentimentMetadata(),
                relational = RelationalMetadata(
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
                structural = StructuralMetadata(
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

        val json = mapper.writeValueAsString(snapshot)
        val restored = mapper.readValue(json, EntityMetadataSnapshot::class.java)

        assertEquals(snapshot, restored)
    }

    @Test
    fun `snapshot JSON has metadata keyed by SENTIMENT, RELATIONAL, STRUCTURAL`() {
        val snapshot = EntityMetadataSnapshot(
            metadata = EntityMetadata(
                sentiment = SentimentMetadata(),
                relational = RelationalMetadata(snapshotAt = ZonedDateTime.now()),
                structural = StructuralMetadata(
                    entityTypeName = "T",
                    semanticGroup = SemanticGroup.UNCATEGORIZED,
                    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                    schemaVersion = 1,
                    snapshotAt = ZonedDateTime.now(),
                ),
            ),
            embeddedAt = ZonedDateTime.now(),
        )

        val json = mapper.writeValueAsString(snapshot)

        assertTrue(json.contains("\"SENTIMENT\""), "JSON should contain SENTIMENT key")
        assertTrue(json.contains("\"RELATIONAL\""), "JSON should contain RELATIONAL key")
        assertTrue(json.contains("\"STRUCTURAL\""), "JSON should contain STRUCTURAL key")
        assertTrue(json.contains("\"snapshotVersion\":\"v1\""), "JSON should contain snapshotVersion v1")
    }

    @Test
    fun `forward-compat - deserialize tolerates unknown future fields`() {
        val now = ZonedDateTime.parse("2026-04-29T12:00:00Z")
        val futureJson = """
            {
              "snapshotVersion": "v1",
              "metadata": {
                "SENTIMENT": {
                  "status": "NOT_APPLICABLE",
                  "stalenessModel": "ON_SOURCE_TEXT_CHANGE",
                  "themes": [],
                  "futureFieldOnSentiment": "ignore-me"
                },
                "FUTURE_CATEGORY": { "x": 1 },
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

        val restored = mapper.readValue(futureJson, EntityMetadataSnapshot::class.java)

        assertEquals("v1", restored.snapshotVersion)
        assertEquals(ConnotationStatus.NOT_APPLICABLE, restored.metadata.sentiment?.status)
        assertNull(restored.metadata.relational, "RELATIONAL metadata is absent in this fixture")
        assertEquals("Customer", restored.metadata.structural?.entityTypeName)
    }

    @Test
    fun `defaults - SentimentMetadata defaults to NOT_APPLICABLE status and ON_SOURCE_TEXT_CHANGE staleness`() {
        val sentiment = SentimentMetadata()

        assertEquals(ConnotationStatus.NOT_APPLICABLE, sentiment.status)
        assertEquals(MetadataStalenessModel.ON_SOURCE_TEXT_CHANGE, sentiment.stalenessModel)
        assertNull(sentiment.sentiment)
        assertNull(sentiment.sentimentLabel)
        assertTrue(sentiment.themes.isEmpty())
    }
}
