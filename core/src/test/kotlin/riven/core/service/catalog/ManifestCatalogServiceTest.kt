package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import riven.core.enums.catalog.ManifestType
import riven.core.exceptions.NotFoundException
import riven.core.repository.catalog.*
import riven.core.service.util.factory.catalog.CatalogFactory.createEntityTypeEntity
import riven.core.service.util.factory.catalog.CatalogFactory.createFieldMappingEntity
import riven.core.service.util.factory.catalog.CatalogFactory.createManifestEntity
import riven.core.service.util.factory.catalog.CatalogFactory.createRelationshipEntity
import riven.core.service.util.factory.catalog.CatalogFactory.createSemanticMetadataEntity
import riven.core.service.util.factory.catalog.CatalogFactory.createTargetRuleEntity
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
        val manifest = createManifestEntity(ManifestType.TEMPLATE, id = manifestId)
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
        val manifest = createManifestEntity(ManifestType.MODEL, id = manifestId)
        val entityTypes = listOf(
            createEntityTypeEntity(manifestId, id = entityTypeId),
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
        val manifest = createManifestEntity(ManifestType.TEMPLATE, id = manifestId)
        val entityType = createEntityTypeEntity(manifestId, id = entityTypeId)
        val relationship = createRelationshipEntity(manifestId, id = relationshipId)
        val targetRule = createTargetRuleEntity(relationshipId)
        val semanticMetadata = createSemanticMetadataEntity(entityTypeId)
        val fieldMapping = createFieldMappingEntity(manifestId)

        whenever(manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse("test-manifest", ManifestType.TEMPLATE))
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

        val result = service.getManifestByKey("test-manifest", ManifestType.TEMPLATE)

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
        whenever(manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse("nonexistent", ManifestType.TEMPLATE))
            .thenReturn(null)

        val exception = assertThrows<NotFoundException> {
            service.getManifestByKey("nonexistent", ManifestType.TEMPLATE)
        }

        assertTrue(exception.message!!.contains("nonexistent"))
    }

    @Test
    fun `getManifestByKey handles manifest with no children`() {
        val manifest = createManifestEntity(ManifestType.MODEL, id = manifestId)

        whenever(manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse("empty-manifest", ManifestType.MODEL))
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

        val result = service.getManifestByKey("empty-manifest", ManifestType.MODEL)

        assertTrue(result.entityTypes.isEmpty())
        assertTrue(result.relationships.isEmpty())
        assertTrue(result.fieldMappings.isEmpty())
    }

    // ------ getEntityTypesForManifest ------

    @Test
    fun `getEntityTypesForManifest returns entity types with semantic metadata`() {
        val manifest = createManifestEntity(ManifestType.TEMPLATE, id = manifestId)
        val entityType = createEntityTypeEntity(manifestId, id = entityTypeId)
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

}
