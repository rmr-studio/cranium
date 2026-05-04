package riven.core.service.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import riven.core.enums.connotation.ConnotationStatus
import riven.core.models.entity.knowledge.KnowledgeSections
import riven.core.service.util.factory.enrichment.EnrichmentSnapshotFixture
import kotlin.reflect.full.declaredMemberProperties

/**
 * Structural snapshot test for [EntityKnowledgeView] as assembled by [EnrichmentSnapshotFixture.buildView].
 *
 * Phase 2 fixture extends Phase 1 with a glossary DEFINES edge to exercise the new read path:
 * [KnowledgeSections.typeNarrative.glossaryDefinitions] is populated with one [GlossaryNarrative]
 * sourced from [EnrichmentSnapshotFixture.GLOSSARY_ENTITY_ID].
 *
 * This test validates the structural shape of the canonical view fixture without a Spring context
 * or database — [EnrichmentSnapshotFixture.buildView] is a pure in-memory factory. The assertions
 * serve as a regression gate: any change to the fixture's section population or scalar values
 * will fail here before affecting downstream consumers.
 *
 * Seven structural invariants are checked:
 * 1. [KnowledgeSections] carries exactly 8 section properties (VIEW-11 parity check).
 * 2. Identifying scalars match the fixture's fixed UUIDs.
 * 3. [KnowledgeSections.attributes] carries 3 entries with the expected semantic labels.
 * 4. [KnowledgeSections.catalogBacklinks] carries exactly 1 entry.
 * 5. [KnowledgeSections.knowledgeBacklinks] is empty (no MENTION edges in this fixture).
 * 6. [KnowledgeSections.typeNarrative.glossaryDefinitions] is non-empty (DEFINES edge populated).
 * 7. [KnowledgeSections.entityMetadata.sentiment] is non-null with status ANALYZED.
 */
class EntityKnowledgeViewSnapshotTest {

    private val view = EnrichmentSnapshotFixture.buildView()

    // ------ Test 1: 8 sections present ------

    /**
     * VIEW-11 parity: the fixture's sections container has exactly 8 declared member properties.
     * Mirrors the assertion in [riven.core.models.entity.knowledge.KnowledgeSectionsContractTest]
     * to ensure the fixture exercises the full contract surface.
     */
    @Test
    fun `sections container has exactly 8 declared member properties`() {
        val sectionNames = KnowledgeSections::class.declaredMemberProperties.map { it.name }.toSet()
        assertEquals(
            setOf(
                "identity",
                "typeNarrative",
                "attributes",
                "catalogBacklinks",
                "knowledgeBacklinks",
                "entityMetadata",
                "clusterSiblings",
                "relationalReferences",
            ),
            sectionNames,
            "KnowledgeSections must carry exactly the 8 expected section names.",
        )
        assertEquals(8, sectionNames.size, "KnowledgeSections must have exactly 8 properties.")
    }

    // ------ Test 2: identifying scalars ------

    /**
     * Fixture scalars outside [KnowledgeSections] match the deterministic UUIDs baked into
     * [EnrichmentSnapshotFixture]. Any UUID drift in the fixture will fail here.
     */
    @Test
    fun `identifying scalars match fixture fixed UUIDs`() {
        assertEquals(EnrichmentSnapshotFixture.QUEUE_ITEM_ID, view.queueItemId)
        assertEquals(EnrichmentSnapshotFixture.ENTITY_ID, view.entityId)
        assertEquals(EnrichmentSnapshotFixture.WORKSPACE_ID, view.workspaceId)
        assertEquals(EnrichmentSnapshotFixture.ENTITY_TYPE_ID, view.entityTypeId)
        assertEquals(3, view.schemaVersion, "schemaVersion must be 3 per the Phase 2 fixture")
    }

    // ------ Test 3: attributes section ------

    /**
     * [KnowledgeSections.attributes] must carry 3 entries — IDENTIFIER, CONNOTATION_SOURCE, and
     * RELATIONAL_REFERENCE — matching the 3 attribute UUIDs seeded by [EnrichmentSnapshotFixture.buildView].
     */
    @Test
    fun `attributes section has 3 entries with expected UUIDs`() {
        val attrs = view.sections.attributes
        assertEquals(3, attrs.size, "Fixture must produce exactly 3 attribute sections.")

        val attrIds = attrs.map { it.attributeId }.toSet()
        assertTrue(EnrichmentSnapshotFixture.ATTR_NAME_ID in attrIds, "IDENTIFIER attribute must be present.")
        assertTrue(EnrichmentSnapshotFixture.ATTR_SENTIMENT_SOURCE_ID in attrIds, "CONNOTATION_SOURCE attribute must be present.")
        assertTrue(EnrichmentSnapshotFixture.ATTR_RELATIONAL_REF_ID in attrIds, "RELATIONAL_REFERENCE attribute must be present.")
    }

    // ------ Test 4: catalogBacklinks section ------

    /**
     * [KnowledgeSections.catalogBacklinks] must carry exactly 1 entry for the Support Tickets
     * relationship definition, with the deterministic definition UUID.
     */
    @Test
    fun `catalogBacklinks section has exactly 1 entry matching fixture definition UUID`() {
        val backlinks = view.sections.catalogBacklinks
        assertEquals(1, backlinks.size, "Fixture must produce exactly 1 catalog backlink.")
        assertEquals(
            EnrichmentSnapshotFixture.REL_DEFINITION_ID,
            backlinks[0].definitionId,
            "catalogBacklinks[0].definitionId must match the fixture relationship definition UUID.",
        )
    }

    // ------ Test 5: knowledgeBacklinks section ------

    /**
     * [KnowledgeSections.knowledgeBacklinks] is empty — this fixture has no MENTION edges.
     * MENTION-edge coverage is delegated to [EnrichmentBacklinkMatrixIT].
     */
    @Test
    fun `knowledgeBacklinks section is empty in base fixture`() {
        assertTrue(
            view.sections.knowledgeBacklinks.isEmpty(),
            "knowledgeBacklinks must be empty — no MENTION edges in this fixture.",
        )
    }

    // ------ Test 6: typeNarrative carries glossary narrative ------

    /**
     * Phase 2 extension: [KnowledgeSections.typeNarrative.glossaryDefinitions] must be non-empty.
     * The fixture populates one [GlossaryNarrative] sourced from [EnrichmentSnapshotFixture.GLOSSARY_ENTITY_ID]
     * to exercise the DEFINES-edge read path introduced in Plan 02-03.
     */
    @Test
    fun `typeNarrative carries non-empty glossaryDefinitions from DEFINES edge`() {
        val glossary = view.sections.typeNarrative.glossaryDefinitions
        assertTrue(glossary.isNotEmpty(), "typeNarrative.glossaryDefinitions must be non-empty — fixture must contain at least one DEFINES edge.")
        val narrative = glossary[0]
        assertEquals(
            EnrichmentSnapshotFixture.GLOSSARY_ENTITY_ID,
            narrative.sourceEntityId,
            "First glossary narrative must originate from GLOSSARY_ENTITY_ID.",
        )
        assertTrue(
            narrative.narrative.isNotBlank(),
            "Glossary narrative text must be non-blank.",
        )
    }

    // ------ Test 7: entityMetadata.sentiment present and ANALYZED ------

    /**
     * [KnowledgeSections.entityMetadata.sentiment] must be non-null and carry status = ANALYZED.
     * This confirms that the Phase 2 fixture populates sentiment correctly and that the field
     * lives inside [EntityMetadataSection] (not as a 9th section — VIEW-11 architecture constraint).
     */
    @Test
    fun `entityMetadata sentiment is non-null and has status ANALYZED`() {
        val sentiment = view.sections.entityMetadata.sentiment
        assertNotNull(sentiment, "entityMetadata.sentiment must not be null in the Phase 2 fixture.")
        assertEquals(
            ConnotationStatus.ANALYZED,
            sentiment!!.status,
            "entityMetadata.sentiment.status must be ANALYZED.",
        )
    }
}
