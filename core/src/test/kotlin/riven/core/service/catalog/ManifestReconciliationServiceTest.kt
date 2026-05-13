package cranium.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import cranium.core.entity.catalog.ManifestCatalogEntity
import cranium.core.enums.catalog.ManifestType
import cranium.core.repository.catalog.ManifestCatalogRepository
import java.util.*

class ManifestReconciliationServiceTest {

    private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    private lateinit var logger: KLogger
    private lateinit var service: ManifestReconciliationService

    @BeforeEach
    fun setUp() {
        manifestCatalogRepository = mock()
        logger = mock()
        service = ManifestReconciliationService(manifestCatalogRepository, logger)
    }

    @Test
    fun `reconcileStaleEntries marks unseen entries as stale`() {
        val seenEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "customer",
            name = "Customer",
            manifestType = ManifestType.INTEGRATION,
            stale = false
        )
        val unseenEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "old-model",
            name = "Old Integration",
            manifestType = ManifestType.INTEGRATION,
            stale = false
        )

        whenever(manifestCatalogRepository.findAll()).thenReturn(listOf(seenEntry, unseenEntry))
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { it.getArgument<ManifestCatalogEntity>(0) }

        val seen = setOf("customer" to ManifestType.INTEGRATION)
        service.reconcileStaleEntries(seen, setOf(ManifestType.INTEGRATION))

        // Only unseenEntry should be saved (stale changed from false to true)
        verify(manifestCatalogRepository, times(1)).save(argThat<ManifestCatalogEntity> {
            key == "old-model" && stale
        })
        // seenEntry should not be saved (stale unchanged)
        verify(manifestCatalogRepository, never()).save(argThat<ManifestCatalogEntity> {
            key == "customer"
        })
    }

    @Test
    fun `reconcileStaleEntries un-stales previously stale entries that are now seen`() {
        val entry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "customer",
            name = "Customer",
            manifestType = ManifestType.INTEGRATION,
            stale = true
        )

        whenever(manifestCatalogRepository.findAll()).thenReturn(listOf(entry))
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { it.getArgument<ManifestCatalogEntity>(0) }

        val seen = setOf("customer" to ManifestType.INTEGRATION)
        service.reconcileStaleEntries(seen, setOf(ManifestType.INTEGRATION))

        verify(manifestCatalogRepository).save(argThat<ManifestCatalogEntity> {
            key == "customer" && !stale
        })
    }

    @Test
    fun `reconcileStaleEntries skips manifest types not in reconciledTypes`() {
        val integrationEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "hubspot",
            name = "HubSpot Integration",
            manifestType = ManifestType.INTEGRATION,
            stale = false
        )
        val templateEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "b2c",
            name = "B2C Template",
            manifestType = ManifestType.TEMPLATE,
            stale = false
        )

        whenever(manifestCatalogRepository.findAll()).thenReturn(listOf(integrationEntry, templateEntry))
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { it.getArgument<ManifestCatalogEntity>(0) }

        // Only reconciling INTEGRATION type — template should be untouched
        val seen = setOf("hubspot" to ManifestType.INTEGRATION)
        service.reconcileStaleEntries(seen, setOf(ManifestType.INTEGRATION))

        // Template should NOT be touched (not in reconciledTypes)
        verify(manifestCatalogRepository, never()).save(argThat<ManifestCatalogEntity> {
            key == "b2c"
        })
        // Integration is seen and unchanged — also not saved
        verify(manifestCatalogRepository, never()).save(any())
    }

    @Test
    fun `reconcileStaleEntries marks unseen integration stale without affecting templates`() {
        val unseenIntegration = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "old-integration",
            name = "Old Integration",
            manifestType = ManifestType.INTEGRATION,
            stale = false
        )
        val templateEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "b2c",
            name = "B2C Template",
            manifestType = ManifestType.TEMPLATE,
            stale = false
        )

        whenever(manifestCatalogRepository.findAll()).thenReturn(listOf(unseenIntegration, templateEntry))
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { it.getArgument<ManifestCatalogEntity>(0) }

        service.reconcileStaleEntries(emptySet(), setOf(ManifestType.INTEGRATION))

        // Unseen integration should be marked stale
        verify(manifestCatalogRepository).save(argThat<ManifestCatalogEntity> {
            key == "old-integration" && stale
        })
        // Template should NOT be touched
        verify(manifestCatalogRepository, never()).save(argThat<ManifestCatalogEntity> {
            key == "b2c"
        })
    }

    @Test
    fun `reconcileStaleEntries does nothing when all entries are already correct`() {
        val entry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "customer",
            name = "Customer",
            manifestType = ManifestType.INTEGRATION,
            stale = false
        )

        whenever(manifestCatalogRepository.findAll()).thenReturn(listOf(entry))

        val seen = setOf("customer" to ManifestType.INTEGRATION)
        service.reconcileStaleEntries(seen, setOf(ManifestType.INTEGRATION))

        verify(manifestCatalogRepository, never()).save(any())
    }
}
