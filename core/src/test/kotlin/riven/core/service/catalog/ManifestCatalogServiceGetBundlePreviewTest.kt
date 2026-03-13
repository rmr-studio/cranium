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

class ManifestCatalogServiceGetBundlePreviewTest {

    private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository
    private lateinit var catalogRelationshipRepository: CatalogRelationshipRepository
    private lateinit var catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository
    private lateinit var catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository
    private lateinit var catalogFieldMappingRepository: CatalogFieldMappingRepository
    private lateinit var logger: KLogger
    private lateinit var service: ManifestCatalogService

    private val bundleId = UUID.randomUUID()
    private val template1ManifestId = UUID.randomUUID()
    private val template2ManifestId = UUID.randomUUID()
    private val entityType1Id = UUID.randomUUID()
    private val entityType2Id = UUID.randomUUID()
    private val relationship1Id = UUID.randomUUID()

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

    // ------ getBundlePreview ------

    @Test
    fun `getBundlePreview returns templates with entity types and relationships`() {
        val bundle = createManifestEntity(
            ManifestType.BUNDLE, id = bundleId, key = "test-bundle", name = "Test Bundle",
            templateKeys = listOf("crm-template", "billing-template")
        )
        val template1 = createManifestEntity(
            ManifestType.TEMPLATE, id = template1ManifestId, key = "crm-template", name = "CRM"
        )
        val template2 = createManifestEntity(
            ManifestType.TEMPLATE, id = template2ManifestId, key = "billing-template", name = "Billing"
        )
        val entityType1 = createEntityTypeEntity(template1ManifestId, id = entityType1Id, key = "customer")
        val entityType2 = createEntityTypeEntity(template2ManifestId, id = entityType2Id, key = "invoice")
        val relationship1 = createRelationshipEntity(template1ManifestId, id = relationship1Id)
        val targetRule1 = createTargetRuleEntity(relationship1Id)
        val semanticMetadata1 = createSemanticMetadataEntity(entityType1Id)

        // Bundle lookup
        whenever(manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse("test-bundle", ManifestType.BUNDLE))
            .thenReturn(bundle)

        // Template 1 (CRM) — has entity type, relationship, semantic metadata
        whenever(manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse("crm-template", ManifestType.TEMPLATE))
            .thenReturn(template1)
        whenever(catalogEntityTypeRepository.findByManifestId(template1ManifestId))
            .thenReturn(listOf(entityType1))
        whenever(catalogSemanticMetadataRepository.findByCatalogEntityTypeIdIn(listOf(entityType1Id)))
            .thenReturn(listOf(semanticMetadata1))
        whenever(catalogRelationshipRepository.findByManifestId(template1ManifestId))
            .thenReturn(listOf(relationship1))
        whenever(catalogRelationshipTargetRuleRepository.findByCatalogRelationshipIdIn(listOf(relationship1Id)))
            .thenReturn(listOf(targetRule1))
        whenever(catalogFieldMappingRepository.findByManifestId(template1ManifestId))
            .thenReturn(emptyList())

        // Template 2 (Billing) — has entity type, no relationships
        whenever(manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse("billing-template", ManifestType.TEMPLATE))
            .thenReturn(template2)
        whenever(catalogEntityTypeRepository.findByManifestId(template2ManifestId))
            .thenReturn(listOf(entityType2))
        whenever(catalogSemanticMetadataRepository.findByCatalogEntityTypeIdIn(listOf(entityType2Id)))
            .thenReturn(emptyList())
        whenever(catalogRelationshipRepository.findByManifestId(template2ManifestId))
            .thenReturn(emptyList())
        whenever(catalogRelationshipTargetRuleRepository.findByCatalogRelationshipIdIn(emptyList()))
            .thenReturn(emptyList())
        whenever(catalogFieldMappingRepository.findByManifestId(template2ManifestId))
            .thenReturn(emptyList())

        val result = service.getBundlePreview("test-bundle")

        assertEquals(bundleId, result.id)
        assertEquals("test-bundle", result.key)
        assertEquals("Test Bundle", result.name)
        assertEquals(2, result.templates.size)

        // Template 1 assertions
        val t1 = result.templates[0]
        assertEquals("crm-template", t1.key)
        assertEquals("CRM", t1.name)
        assertEquals(1, t1.entityTypes.size)
        assertEquals("customer", t1.entityTypes[0].key)
        assertEquals(1, t1.entityTypes[0].semanticMetadata.size)
        assertEquals(1, t1.relationships.size)
        assertEquals(1, t1.relationships[0].targetRules.size)

        // Template 2 assertions
        val t2 = result.templates[1]
        assertEquals("billing-template", t2.key)
        assertEquals("Billing", t2.name)
        assertEquals(1, t2.entityTypes.size)
        assertEquals("invoice", t2.entityTypes[0].key)
        assertTrue(t2.relationships.isEmpty())
    }

    @Test
    fun `getBundlePreview throws NotFoundException for unknown bundle key`() {
        whenever(manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse("nonexistent", ManifestType.BUNDLE))
            .thenReturn(null)

        val exception = assertThrows<NotFoundException> {
            service.getBundlePreview("nonexistent")
        }

        assertTrue(exception.message!!.contains("nonexistent"))
    }

    @Test
    fun `getBundlePreview throws NotFoundException when bundle references missing template`() {
        val bundle = createManifestEntity(
            ManifestType.BUNDLE, id = bundleId, key = "broken-bundle",
            templateKeys = listOf("missing-template")
        )

        whenever(manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse("broken-bundle", ManifestType.BUNDLE))
            .thenReturn(bundle)
        whenever(manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse("missing-template", ManifestType.TEMPLATE))
            .thenReturn(null)

        val exception = assertThrows<NotFoundException> {
            service.getBundlePreview("broken-bundle")
        }

        assertTrue(exception.message!!.contains("missing-template"))
    }
}
