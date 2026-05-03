package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import riven.core.repository.catalog.CatalogEntityTypeRepository
import riven.core.repository.catalog.CatalogFieldMappingRepository
import riven.core.repository.catalog.CatalogRelationshipRepository
import riven.core.repository.catalog.CatalogRelationshipTargetRuleRepository
import riven.core.repository.catalog.CatalogSemanticMetadataRepository
import riven.core.repository.catalog.ManifestCatalogRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.util.factory.catalog.CatalogFactory
import riven.core.service.util.factory.entity.EntityFactory
import java.util.Optional
import java.util.UUID

class ManifestCatalogServiceTest {

    private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository
    private lateinit var catalogRelationshipRepository: CatalogRelationshipRepository
    private lateinit var catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository
    private lateinit var catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository
    private lateinit var catalogFieldMappingRepository: CatalogFieldMappingRepository
    private lateinit var entityTypeRepository: EntityTypeRepository
    private lateinit var logger: KLogger
    private lateinit var service: ManifestCatalogService

    @BeforeEach
    fun setUp() {
        manifestCatalogRepository = mock()
        catalogEntityTypeRepository = mock()
        catalogRelationshipRepository = mock()
        catalogRelationshipTargetRuleRepository = mock()
        catalogSemanticMetadataRepository = mock()
        catalogFieldMappingRepository = mock()
        entityTypeRepository = mock()
        logger = mock()

        service = ManifestCatalogService(
            manifestCatalogRepository,
            catalogEntityTypeRepository,
            catalogRelationshipRepository,
            catalogRelationshipTargetRuleRepository,
            catalogSemanticMetadataRepository,
            catalogFieldMappingRepository,
            entityTypeRepository,
            logger,
        )
    }

    // ------ getConnotationSignalsForEntityType ------

    @Test
    fun `getConnotationSignalsForEntityType returns signals when catalog row has them`() {
        val entityTypeId = UUID.randomUUID()
        val manifestId = UUID.randomUUID()
        val key = "review"
        val signals = CatalogFactory.connotationSignals()

        val entityType = EntityFactory.createEntityType(
            id = entityTypeId,
            key = key,
            sourceManifestId = manifestId,
        )
        val catalogRow = CatalogFactory.catalogEntityTypeEntityWithSignals(
            signals = signals,
            manifestId = manifestId,
        )

        whenever(entityTypeRepository.findById(entityTypeId)).thenReturn(Optional.of(entityType))
        whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, key))
            .thenReturn(catalogRow)

        val result = service.getConnotationSignalsForEntityType(entityTypeId)

        assertEquals(signals, result)
    }

    @Test
    fun `getConnotationSignalsForEntityType returns null when catalog row has no signals`() {
        val entityTypeId = UUID.randomUUID()
        val manifestId = UUID.randomUUID()
        val key = "review"

        val entityType = EntityFactory.createEntityType(
            id = entityTypeId,
            key = key,
            sourceManifestId = manifestId,
        )
        val catalogRow = CatalogFactory.catalogEntityTypeEntityWithSignals(
            signals = null,
            manifestId = manifestId,
        )

        whenever(entityTypeRepository.findById(entityTypeId)).thenReturn(Optional.of(entityType))
        whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, key))
            .thenReturn(catalogRow)

        val result = service.getConnotationSignalsForEntityType(entityTypeId)

        assertNull(result)
    }

    @Test
    fun `getConnotationSignalsForEntityType returns null when entity type has no sourceManifestId`() {
        val entityTypeId = UUID.randomUUID()
        val entityType = EntityFactory.createEntityType(
            id = entityTypeId,
            sourceManifestId = null,
        )

        whenever(entityTypeRepository.findById(entityTypeId)).thenReturn(Optional.of(entityType))

        val result = service.getConnotationSignalsForEntityType(entityTypeId)

        assertNull(result)
    }

    @Test
    fun `getConnotationSignalsForEntityType returns null when entity type does not exist`() {
        val entityTypeId = UUID.randomUUID()

        whenever(entityTypeRepository.findById(entityTypeId)).thenReturn(Optional.empty())

        val result = service.getConnotationSignalsForEntityType(entityTypeId)

        assertNull(result)
    }

    @Test
    fun `getConnotationSignalsForEntityType returns null when no catalog row matches manifestId and key`() {
        val entityTypeId = UUID.randomUUID()
        val manifestId = UUID.randomUUID()
        val key = "review"

        val entityType = EntityFactory.createEntityType(
            id = entityTypeId,
            key = key,
            sourceManifestId = manifestId,
        )

        whenever(entityTypeRepository.findById(entityTypeId)).thenReturn(Optional.of(entityType))
        whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, key))
            .thenReturn(null)

        val result = service.getConnotationSignalsForEntityType(entityTypeId)

        assertNull(result)
    }
}
