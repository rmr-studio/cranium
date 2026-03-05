package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import riven.core.entity.catalog.*
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.exceptions.NotFoundException
import riven.core.repository.catalog.*
import java.util.*

class ManifestCatalogServiceTest {

    private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository
    private lateinit var catalogRelationshipRepository: CatalogRelationshipRepository
    private lateinit var catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository
    private lateinit var catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository
    private lateinit var catalogFieldMappingRepository: CatalogFieldMappingRepository
    private lateinit var logger: KLogger
    private lateinit var service: ManifestCatalogService

    private val manifestId = UUID.randomUUID()
    private val entityTypeId = UUID.randomUUID()
    private val relationshipId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        manifestCatalogRepository = mock()
        catalogEntityTypeRepository = mock()
        catalogRelationshipRepository = mock()
        catalogRelationshipTargetRuleRepository = mock()
        catalogSemanticMetadataRepository = mock()
        catalogFieldMappingRepository = mock()
        logger = mock()

        service = ManifestCatalogService(
            manifestCatalogRepository,
            catalogEntityTypeRepository,
            catalogRelationshipRepository,
            catalogRelationshipTargetRuleRepository,
            catalogSemanticMetadataRepository,
            catalogFieldMappingRepository,
            logger
        )
    }

    // ------ getAvailableTemplates ------

    @Test
    fun `getAvailableTemplates returns summaries for non-stale templates`() {
        val manifest = createManifestEntity(ManifestType.TEMPLATE)
        val entityTypes = listOf(createEntityTypeEntity(manifestId))

        whenever(manifestCatalogRepository.findByManifestTypeAndStaleFalse(ManifestType.TEMPLATE))
            .thenReturn(listOf(manifest))
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(entityTypes)

        val result = service.getAvailableTemplates()

        assertEquals(1, result.size)
        assertEquals(manifest.key, result[0].key)
        assertEquals(manifest.name, result[0].name)
        assertEquals(1, result[0].entityTypeCount)
    }

    @Test
    fun `getAvailableTemplates returns empty list when no templates exist`() {
        whenever(manifestCatalogRepository.findByManifestTypeAndStaleFalse(ManifestType.TEMPLATE))
            .thenReturn(emptyList())

        val result = service.getAvailableTemplates()

        assertTrue(result.isEmpty())
    }

    // ------ getAvailableModels ------

    @Test
    fun `getAvailableModels returns summaries for non-stale models`() {
        val manifest = createManifestEntity(ManifestType.MODEL)
        val entityTypes = listOf(
            createEntityTypeEntity(manifestId),
            createEntityTypeEntity(manifestId, key = "second-type")
        )

        whenever(manifestCatalogRepository.findByManifestTypeAndStaleFalse(ManifestType.MODEL))
            .thenReturn(listOf(manifest))
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(entityTypes)

        val result = service.getAvailableModels()

        assertEquals(1, result.size)
        assertEquals(2, result[0].entityTypeCount)
    }

    // ------ getManifestByKey ------

    @Test
    fun `getManifestByKey returns fully hydrated detail`() {
        val manifest = createManifestEntity(ManifestType.TEMPLATE)
        val entityType = createEntityTypeEntity(manifestId)
        val relationship = createRelationshipEntity(manifestId)
        val targetRule = createTargetRuleEntity(relationshipId)
        val semanticMetadata = createSemanticMetadataEntity(entityTypeId)
        val fieldMapping = createFieldMappingEntity(manifestId)

        whenever(manifestCatalogRepository.findByKeyAndStaleFalse("test-manifest"))
            .thenReturn(manifest)
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(entityType))
        whenever(catalogSemanticMetadataRepository.findByCatalogEntityTypeIdIn(listOf(entityTypeId)))
            .thenReturn(listOf(semanticMetadata))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(listOf(relationship))
        whenever(catalogRelationshipTargetRuleRepository.findByCatalogRelationshipIdIn(listOf(relationshipId)))
            .thenReturn(listOf(targetRule))
        whenever(catalogFieldMappingRepository.findByManifestId(manifestId))
            .thenReturn(listOf(fieldMapping))

        val result = service.getManifestByKey("test-manifest")

        assertEquals(manifest.key, result.key)
        assertEquals(manifest.name, result.name)
        assertEquals(ManifestType.TEMPLATE, result.manifestType)
        assertEquals(1, result.entityTypes.size)
        assertEquals(1, result.entityTypes[0].semanticMetadata.size)
        assertEquals(1, result.relationships.size)
        assertEquals(1, result.relationships[0].targetRules.size)
        assertEquals(1, result.fieldMappings.size)

        // Verify batch loading was used
        verify(catalogSemanticMetadataRepository).findByCatalogEntityTypeIdIn(listOf(entityTypeId))
        verify(catalogRelationshipTargetRuleRepository).findByCatalogRelationshipIdIn(listOf(relationshipId))
    }

    @Test
    fun `getManifestByKey throws NotFoundException for missing key`() {
        whenever(manifestCatalogRepository.findByKeyAndStaleFalse("nonexistent"))
            .thenReturn(null)

        val exception = assertThrows<NotFoundException> {
            service.getManifestByKey("nonexistent")
        }

        assertTrue(exception.message!!.contains("nonexistent"))
    }

    @Test
    fun `getManifestByKey handles manifest with no children`() {
        val manifest = createManifestEntity(ManifestType.MODEL)

        whenever(manifestCatalogRepository.findByKeyAndStaleFalse("empty-manifest"))
            .thenReturn(manifest)
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())
        whenever(catalogSemanticMetadataRepository.findByCatalogEntityTypeIdIn(emptyList()))
            .thenReturn(emptyList())
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())
        whenever(catalogRelationshipTargetRuleRepository.findByCatalogRelationshipIdIn(emptyList()))
            .thenReturn(emptyList())
        whenever(catalogFieldMappingRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())

        val result = service.getManifestByKey("empty-manifest")

        assertTrue(result.entityTypes.isEmpty())
        assertTrue(result.relationships.isEmpty())
        assertTrue(result.fieldMappings.isEmpty())
    }

    // ------ getEntityTypesForManifest ------

    @Test
    fun `getEntityTypesForManifest returns entity types with semantic metadata`() {
        val manifest = createManifestEntity(ManifestType.TEMPLATE)
        val entityType = createEntityTypeEntity(manifestId)
        val semanticMetadata = createSemanticMetadataEntity(entityTypeId)

        whenever(manifestCatalogRepository.findById(manifestId))
            .thenReturn(Optional.of(manifest))
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(entityType))
        whenever(catalogSemanticMetadataRepository.findByCatalogEntityTypeIdIn(listOf(entityTypeId)))
            .thenReturn(listOf(semanticMetadata))

        val result = service.getEntityTypesForManifest(manifestId)

        assertEquals(1, result.size)
        assertEquals(entityType.key, result[0].key)
        assertEquals(1, result[0].semanticMetadata.size)
        assertEquals(semanticMetadata.targetId, result[0].semanticMetadata[0].targetId)

        // Verify batch loading
        verify(catalogSemanticMetadataRepository).findByCatalogEntityTypeIdIn(listOf(entityTypeId))
    }

    @Test
    fun `getEntityTypesForManifest throws NotFoundException for missing manifest`() {
        whenever(manifestCatalogRepository.findById(manifestId))
            .thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.getEntityTypesForManifest(manifestId)
        }
    }

    // ------ Test Data Factories ------

    private fun createManifestEntity(type: ManifestType) = ManifestCatalogEntity(
        id = manifestId,
        key = "test-manifest",
        name = "Test Manifest",
        description = "A test manifest",
        manifestType = type,
        manifestVersion = "1.0.0",
        stale = false
    )

    private fun createEntityTypeEntity(
        manifestId: UUID,
        key: String = "test-entity-type"
    ) = CatalogEntityTypeEntity(
        id = entityTypeId,
        manifestId = manifestId,
        key = key,
        displayNameSingular = "Test Entity",
        displayNamePlural = "Test Entities",
        iconType = IconType.CIRCLE_DASHED,
        iconColour = IconColour.NEUTRAL,
        semanticGroup = SemanticGroup.UNCATEGORIZED,
        schema = mapOf("type" to "object"),
        columns = listOf(mapOf("key" to "name", "label" to "Name"))
    )

    private fun createRelationshipEntity(manifestId: UUID) = CatalogRelationshipEntity(
        id = relationshipId,
        manifestId = manifestId,
        key = "test-relationship",
        sourceEntityTypeKey = "test-entity-type",
        name = "Test Relationship",
        cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY
    )

    private fun createTargetRuleEntity(relationshipId: UUID) = CatalogRelationshipTargetRuleEntity(
        id = UUID.randomUUID(),
        catalogRelationshipId = relationshipId,
        targetEntityTypeKey = "target-entity-type",
        semanticTypeConstraint = SemanticGroup.CUSTOMER,
        cardinalityOverride = EntityRelationshipCardinality.ONE_TO_ONE,
        inverseVisible = true,
        inverseName = "reverse-test"
    )

    private fun createSemanticMetadataEntity(entityTypeId: UUID) = CatalogSemanticMetadataEntity(
        id = UUID.randomUUID(),
        catalogEntityTypeId = entityTypeId,
        targetType = SemanticMetadataTargetType.ENTITY_TYPE,
        targetId = "test-entity-type",
        definition = "A test entity type",
        classification = SemanticAttributeClassification.IDENTIFIER,
        tags = listOf("test", "core")
    )

    private fun createFieldMappingEntity(manifestId: UUID) = CatalogFieldMappingEntity(
        id = UUID.randomUUID(),
        manifestId = manifestId,
        entityTypeKey = "test-entity-type",
        mappings = mapOf("externalField" to "internalField")
    )
}
