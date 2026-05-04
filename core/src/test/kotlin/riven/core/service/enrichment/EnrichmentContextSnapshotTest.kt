package riven.core.service.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.configuration.util.ObjectMapperConfig
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.repository.connotation.EntityConnotationRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.workspace.WorkspaceRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.catalog.ManifestCatalogService
import riven.core.service.connotation.ConnotationAnalysisService
import riven.core.service.entity.EntityAttributeService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.enrichment.EnrichmentSnapshotFixture
import tools.jackson.databind.ObjectMapper
import java.util.Optional

/**
 * Byte-identical snapshot test for [EnrichmentAnalysisService.analyzeSemantics].
 *
 * This test establishes the Phase 1 verification gate (ENRICH-03): a checked-in JSON file
 * captures the exact [riven.core.models.enrichment.EnrichmentContext] shape produced by the
 * enrichment analysis service. All subsequent Plans must maintain byte-identity against this
 * snapshot — any structural change to the context that passes the snapshot test but changes
 * the JSON is caught immediately.
 *
 * **Snapshot test class shift (Plan 01-03):** This test previously wired `@SpringBootTest(classes =
 * [EnrichmentService::class, ...])` (Plan 01). Post-Plan-03, `EnrichmentService` is deleted and this
 * test wires `EnrichmentAnalysisService` instead. The fixture data and the JSON snapshot resource at
 * `classpath:enrichment/enrichment-context-snapshot.json` are UNCHANGED — byte-identity is maintained.
 * The class shift is the unavoidable consequence of deleting `EnrichmentService.kt`; documented here
 * for Phase 2 reviewers so the gate's evolution is traceable.
 *
 * The fixture uses deterministic FIXED UUIDs so the output JSON is stable across runs.
 * The test uses the production ObjectMapper (not a fresh one) so serialization config is identical
 * to what would be used in the actual pipeline.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EnrichmentAnalysisService::class,
        EnrichmentContextAssembler::class,
        ObjectMapperConfig::class,
    ]
)
class EnrichmentContextSnapshotTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @MockitoBean
    private lateinit var entityConnotationRepository: EntityConnotationRepository

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var semanticMetadataRepository: EntityTypeSemanticMetadataRepository

    @MockitoBean
    private lateinit var entityAttributeService: EntityAttributeService

    @MockitoBean
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @MockitoBean
    private lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var identityClusterMemberRepository: IdentityClusterMemberRepository

    @MockitoBean
    private lateinit var relationshipTargetRuleRepository: RelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var workspaceRepository: WorkspaceRepository

    @MockitoBean
    private lateinit var manifestCatalogService: ManifestCatalogService

    @MockitoBean
    private lateinit var connotationAnalysisService: ConnotationAnalysisService

    @Autowired
    private lateinit var enrichmentAnalysisService: EnrichmentAnalysisService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val fixture = EnrichmentSnapshotFixture.build()

    @BeforeEach
    fun setUp() {
        val f = fixture

        // Queue item
        whenever(executionQueueRepository.findById(f.queueItemId)).thenReturn(Optional.of(f.queueItem))
        whenever(executionQueueRepository.save(any())).thenAnswer { it.arguments[0] }

        // Primary entity and type
        whenever(entityRepository.findById(f.entityId)).thenReturn(Optional.of(f.entity))
        whenever(entityTypeRepository.findById(f.entityTypeId)).thenReturn(Optional.of(f.entityType))

        // Semantic metadata for primary entity type
        whenever(semanticMetadataRepository.findByEntityTypeId(f.entityTypeId)).thenReturn(f.allPrimaryMetadata)

        // Primary entity attributes
        whenever(entityAttributeService.getAttributes(f.entityId)).thenReturn(f.entityAttributes)

        // Relationship definitions for primary entity type
        whenever(
            relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(f.workspaceId, f.entityTypeId)
        ).thenReturn(listOf(f.relationshipDefinition))

        // Relationship instances
        whenever(
            entityRelationshipRepository.findAllRelationshipsForEntity(f.entityId, f.workspaceId)
        ).thenReturn(listOf(f.relationshipEntity))

        // Target rules for the definition
        whenever(
            relationshipTargetRuleRepository.findByRelationshipDefinitionIdIn(listOf(EnrichmentSnapshotFixture.REL_DEFINITION_ID))
        ).thenReturn(listOf(f.targetRule))

        // Semantic metadata for relationship target type (CATEGORICAL attr lookup)
        whenever(
            semanticMetadataRepository.findByEntityTypeId(EnrichmentSnapshotFixture.REL_TARGET_TYPE_ID)
        ).thenReturn(listOf(f.metaCategoricalAttr))

        // Attribute values for related entities (topCategories resolution)
        whenever(
            entityAttributeService.getAttributesForEntities(listOf(EnrichmentSnapshotFixture.RELATED_ENTITY_ID))
        ).thenReturn(
            mapOf(EnrichmentSnapshotFixture.RELATED_ENTITY_ID to f.relatedEntityAttributes)
        )

        // Cluster membership
        whenever(
            identityClusterMemberRepository.findByEntityId(f.entityId)
        ).thenReturn(f.primaryClusterMember)
        whenever(
            identityClusterMemberRepository.findByClusterId(EnrichmentSnapshotFixture.CLUSTER_ID)
        ).thenReturn(f.allClusterMembers)

        // Cluster member entities and types
        whenever(
            entityRepository.findAllById(listOf(EnrichmentSnapshotFixture.CLUSTER_MEMBER_ENTITY_ID))
        ).thenReturn(listOf(f.clusterMemberEntity))
        whenever(
            entityTypeRepository.findAllById(listOf(EnrichmentSnapshotFixture.CLUSTER_MEMBER_TYPE_ID))
        ).thenReturn(listOf(f.clusterMemberEntityType))

        // RELATIONAL_REFERENCE resolution: referenced entity and type
        whenever(
            entityRepository.findAllById(listOf(EnrichmentSnapshotFixture.REF_ENTITY_ID))
        ).thenReturn(listOf(f.referencedEntity))

        whenever(
            semanticMetadataRepository.findByEntityTypeIdIn(listOf(EnrichmentSnapshotFixture.REF_ENTITY_TYPE_ID))
        ).thenReturn(listOf(f.metaRefTypeIdentifier))

        whenever(
            entityAttributeService.getAttributesForEntities(listOf(EnrichmentSnapshotFixture.REF_ENTITY_ID))
        ).thenReturn(
            mapOf(EnrichmentSnapshotFixture.REF_ENTITY_ID to f.referencedEntityAttributes)
        )

        // Workspace with connotationEnabled = true
        whenever(workspaceRepository.findById(f.workspaceId)).thenReturn(Optional.of(f.workspace))

        // Manifest signals present → sentiment path proceeds
        whenever(
            manifestCatalogService.getConnotationSignalsForEntityType(f.entityTypeId)
        ).thenReturn(f.connotationSignals)

        // ConnotationAnalysisService stub returns ANALYZED sentiment (no internal calls)
        whenever(
            connotationAnalysisService.analyze(
                entityId = any(),
                workspaceId = any(),
                signals = any(),
                sourceValue = any(),
                themeValues = any(),
            )
        ).thenReturn(f.analyzedSentiment)

        // Connotation upsert is a no-op in snapshot test (returns 1 row affected)
        whenever(entityConnotationRepository.upsertByEntityId(any(), any(), any(), any())).thenReturn(1)
    }

    @Test
    fun `analyzeSemantics matches checked-in JSON snapshot`() {
        val context = enrichmentAnalysisService.analyzeSemantics(fixture.queueItemId)

        // Branch-coverage guards: verify all required branches are exercised in the returned context.
        // These assertions prevent a "snapshot passes but fixture is empty" regression.
        assertTrue(
            context.attributes.any { it.classification == SemanticAttributeClassification.RELATIONAL_REFERENCE },
            "Context must contain at least one RELATIONAL_REFERENCE attribute"
        )
        assertTrue(
            context.relationshipSummaries.any { it.topCategories.isNotEmpty() },
            "Context must contain at least one relationship summary with non-empty topCategories"
        )
        assertTrue(
            context.clusterMembers.isNotEmpty(),
            "Context must contain at least one cluster member"
        )
        assertTrue(
            context.relationshipDefinitions.isNotEmpty(),
            "Context must contain at least one relationship definition"
        )
        assertTrue(
            context.referencedEntityIdentifiers.isNotEmpty(),
            "Context must contain at least one referencedEntityIdentifier entry"
        )
        assertEquals(
            ConnotationStatus.ANALYZED,
            context.sentiment?.status,
            "Context sentiment must have status ANALYZED"
        )

        // Serialize using the production ObjectMapper with pretty-printing for human-readable, stable output
        val actual = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context)

        // Load the checked-in snapshot from classpath
        val snapshotStream = this::class.java.classLoader
            .getResourceAsStream("enrichment/enrichment-context-snapshot.json")
            ?: error(
                "Snapshot file not found at classpath:enrichment/enrichment-context-snapshot.json. " +
                    "Delete this file and re-run the test to regenerate it."
            )

        val expected = snapshotStream.bufferedReader().readText()

        assertEquals(expected, actual, "EnrichmentContext JSON does not match the checked-in snapshot.\n" +
            "If this is intentional, regenerate the snapshot by deleting the snapshot file and re-running the test " +
            "to capture the new baseline.")
    }
}
