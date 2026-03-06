package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import riven.core.entity.catalog.ManifestCatalogEntity
import riven.core.entity.integration.IntegrationDefinitionEntity
import riven.core.enums.catalog.ManifestType
import riven.core.enums.integration.IntegrationCategory
import riven.core.repository.catalog.ManifestCatalogRepository
import riven.core.repository.integration.IntegrationDefinitionRepository
import java.util.*

class IntegrationDefinitionStaleSyncServiceTest {

    private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    private lateinit var integrationDefinitionRepository: IntegrationDefinitionRepository
    private lateinit var logger: KLogger
    private lateinit var service: IntegrationDefinitionStaleSyncService

    @BeforeEach
    fun setUp() {
        manifestCatalogRepository = mock()
        integrationDefinitionRepository = mock()
        logger = mock()
        service = IntegrationDefinitionStaleSyncService(
            manifestCatalogRepository,
            integrationDefinitionRepository,
            logger
        )
    }

    @Test
    fun `syncStaleFlags propagates stale flag from catalog to integration definition`() {
        val staleCatalogEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "hubspot",
            name = "HubSpot",
            manifestType = ManifestType.INTEGRATION,
            stale = true
        )
        val nonStaleCatalogEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "salesforce",
            name = "Salesforce",
            manifestType = ManifestType.INTEGRATION,
            stale = false
        )

        whenever(manifestCatalogRepository.findByManifestType(ManifestType.INTEGRATION))
            .thenReturn(listOf(staleCatalogEntry, nonStaleCatalogEntry))

        val hubspotDef = IntegrationDefinitionEntity(
            id = UUID.randomUUID(),
            slug = "hubspot",
            name = "HubSpot",
            category = IntegrationCategory.CRM,
            nangoProviderKey = "hubspot",
            stale = false
        )
        val salesforceDef = IntegrationDefinitionEntity(
            id = UUID.randomUUID(),
            slug = "salesforce",
            name = "Salesforce",
            category = IntegrationCategory.CRM,
            nangoProviderKey = "salesforce",
            stale = true
        )

        whenever(integrationDefinitionRepository.findBySlug("hubspot")).thenReturn(hubspotDef)
        whenever(integrationDefinitionRepository.findBySlug("salesforce")).thenReturn(salesforceDef)

        service.syncStaleFlags()

        verify(integrationDefinitionRepository).save(argThat<IntegrationDefinitionEntity> {
            slug == "hubspot" && stale
        })
        verify(integrationDefinitionRepository).save(argThat<IntegrationDefinitionEntity> {
            slug == "salesforce" && !stale
        })
    }

    @Test
    fun `syncStaleFlags skips integration definitions that dont exist`() {
        val catalogEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "missing",
            name = "Missing",
            manifestType = ManifestType.INTEGRATION,
            stale = true
        )

        whenever(manifestCatalogRepository.findByManifestType(ManifestType.INTEGRATION))
            .thenReturn(listOf(catalogEntry))
        whenever(integrationDefinitionRepository.findBySlug("missing")).thenReturn(null)

        service.syncStaleFlags()

        verify(integrationDefinitionRepository, never()).save(any())
    }

    @Test
    fun `syncStaleFlags does not save when stale flags already match`() {
        val catalogEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "hubspot",
            name = "HubSpot",
            manifestType = ManifestType.INTEGRATION,
            stale = true
        )

        whenever(manifestCatalogRepository.findByManifestType(ManifestType.INTEGRATION))
            .thenReturn(listOf(catalogEntry))

        val definition = IntegrationDefinitionEntity(
            id = UUID.randomUUID(),
            slug = "hubspot",
            name = "HubSpot",
            category = IntegrationCategory.CRM,
            nangoProviderKey = "hubspot",
            stale = true
        )
        whenever(integrationDefinitionRepository.findBySlug("hubspot")).thenReturn(definition)

        service.syncStaleFlags()

        verify(integrationDefinitionRepository, never()).save(any())
    }
}
