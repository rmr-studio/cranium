package riven.core.models.entity.knowledge

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.service.enrichment.EntityKnowledgeViewProjector
import java.time.ZonedDateTime
import java.util.*
import kotlin.reflect.full.declaredMemberProperties

/**
 * VIEW-11 contract gate: enforces the exact 8-property set of [KnowledgeSections] by reflection.
 *
 * Rationale: section names are the primary contract surface for Plan 02-02 assembler output
 * and Plan 02-03 structural snapshot. Any rename, addition, or removal of a section property
 * is caught at compile-test time, not at runtime or in downstream consumer code.
 */
class KnowledgeSectionsContractTest {

    /**
     * VIEW-11 — section name set: verifies that [KnowledgeSections] carries exactly the 8
     * expected property names and no others.
     */
    @Test
    fun `VIEW-11 - KnowledgeSections has exactly the 8 expected property names`() {
        val expectedNames = setOf(
            "identity",
            "typeNarrative",
            "attributes",
            "catalogBacklinks",
            "knowledgeBacklinks",
            "entityMetadata",
            "clusterSiblings",
            "relationalReferences",
        )
        val actualNames = KnowledgeSections::class.declaredMemberProperties.map { it.name }.toSet()
        assertEquals(
            expectedNames,
            actualNames,
            "KnowledgeSections property names do not match the VIEW-11 contract. " +
                "Expected: $expectedNames, Actual: $actualNames",
        )
    }

    /**
     * VIEW-11 — exactly 8 sections: guards against accidental introduction of a 9th or removal
     * of a required section via count verification independent of name comparison.
     */
    @Test
    fun `VIEW-11 - KnowledgeSections has exactly 8 properties`() {
        val count = KnowledgeSections::class.declaredMemberProperties.size
        assertEquals(8, count, "KnowledgeSections must have exactly 8 declared member properties, found $count.")
    }

    /**
     * VIEW-11 — signalBacklinks absent: SIGNAL entity type backlinks are deferred to v2
     * per FUTURE-02. This test prevents accidental resurrection of the field in this phase.
     */
    @Test
    fun `VIEW-11 - signalBacklinks is absent from KnowledgeSections (FUTURE-02 deferred to v2)`() {
        val propertyNames = KnowledgeSections::class.declaredMemberProperties.map { it.name }
        assertFalse(
            "signalBacklinks" in propertyNames,
            "signalBacklinks must NOT be a section in KnowledgeSections — it is deferred to v2 " +
                "when SIGNAL entity types ship (FUTURE-02). If you are adding SIGNAL support, remove this assertion.",
        )
    }

    /**
     * Sentiment placement: verifies that [SentimentMetadata] lives inside [EntityMetadataSection]
     * and has NOT drifted into a separate 9th section per the CONTEXT.md architecture decision.
     */
    @Test
    fun `sentiment lives inside EntityMetadataSection, not as a separate section`() {
        val hassentiment = EntityMetadataSection::class.declaredMemberProperties.any { it.name == "sentiment" }
        assertTrue(
            hassentiment,
            "EntityMetadataSection must carry a 'sentiment' property. If sentiment is missing here, " +
                "it has incorrectly moved to a standalone 9th section.",
        )
    }

    /**
     * Projector stub contract: proves that [EntityKnowledgeViewProjector] compiles, can be
     * instantiated, and returns an empty [TruncationResult] for any input.
     *
     * Inline fixture acceptable for stub-only test; structural fixture factory ships in Plan 02-03
     * with the snapshot test.
     */
    @Test
    fun `EntityKnowledgeViewProjector stub returns empty TruncationResult for any input`() {
        val logger = KotlinLogging.logger {}
        val projector = EntityKnowledgeViewProjector(logger)

        val fixtureView = buildFixtureView()
        val budget = TruncationBudget(maxChars = 0)

        val result = projector.toEmbeddingText(fixtureView, budget)

        assertEquals("", result.text, "Stub must return empty text — Phase 3 implements truncation.")
        assertTrue(result.telemetry.isEmpty(), "Stub must return empty telemetry list — Phase 3 implements telemetry.")
    }

    // ------ Private helpers ------

    private fun buildFixtureView(): EntityKnowledgeView {
        val fixedId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val now = ZonedDateTime.now()

        val identity = IdentitySection(
            entityId = fixedId,
            entityTypeId = fixedId,
            identifierValue = null,
            displayLabel = fixedId.toString(),
        )

        val typeNarrative = TypeNarrativeSection(
            entityTypeName = "TestType",
            semanticGroup = SemanticGroup.UNCATEGORIZED,
            lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
            metadataDefinition = null,
            glossaryDefinitions = emptyList(),
        )

        val entityMetadata = EntityMetadataSection(
            schemaVersion = 1,
            composedAt = now,
            sentiment = null,
        )

        val sections = KnowledgeSections(
            identity = identity,
            typeNarrative = typeNarrative,
            attributes = emptyList(),
            catalogBacklinks = emptyList(),
            knowledgeBacklinks = emptyList(),
            entityMetadata = entityMetadata,
            clusterSiblings = emptyList(),
            relationalReferences = emptyList(),
        )

        return EntityKnowledgeView(
            queueItemId = fixedId,
            entityId = fixedId,
            workspaceId = fixedId,
            entityTypeId = fixedId,
            schemaVersion = 1,
            sections = sections,
        )
    }
}
