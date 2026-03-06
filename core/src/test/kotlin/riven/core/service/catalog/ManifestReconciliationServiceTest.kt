package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import riven.core.entity.catalog.ManifestCatalogEntity
import riven.core.enums.catalog.ManifestType
import riven.core.repository.catalog.ManifestCatalogRepository
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
            manifestType = ManifestType.MODEL,
            stale = false
        )
        val unseenEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "old-model",
            name = "Old Model",
            manifestType = ManifestType.MODEL,
            stale = false
        )

        whenever(manifestCatalogRepository.findAll()).thenReturn(listOf(seenEntry, unseenEntry))
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { it.getArgument<ManifestCatalogEntity>(0) }

        val seen = setOf("customer" to ManifestType.MODEL)
        service.reconcileStaleEntries(seen)

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
            manifestType = ManifestType.MODEL,
            stale = true
        )

        whenever(manifestCatalogRepository.findAll()).thenReturn(listOf(entry))
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { it.getArgument<ManifestCatalogEntity>(0) }

        val seen = setOf("customer" to ManifestType.MODEL)
        service.reconcileStaleEntries(seen)

        verify(manifestCatalogRepository).save(argThat<ManifestCatalogEntity> {
            key == "customer" && !stale
        })
    }

    @Test
    fun `reconcileStaleEntries distinguishes entries by manifest type`() {
        val modelEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "customer",
            name = "Customer Model",
            manifestType = ManifestType.MODEL,
            stale = false
        )
        val templateEntry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "customer",
            name = "Customer Template",
            manifestType = ManifestType.TEMPLATE,
            stale = false
        )

        whenever(manifestCatalogRepository.findAll()).thenReturn(listOf(modelEntry, templateEntry))
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { it.getArgument<ManifestCatalogEntity>(0) }

        // Only MODEL was seen, not TEMPLATE
        val seen = setOf("customer" to ManifestType.MODEL)
        service.reconcileStaleEntries(seen)

        // Template should be marked stale
        verify(manifestCatalogRepository).save(argThat<ManifestCatalogEntity> {
            key == "customer" && manifestType == ManifestType.TEMPLATE && stale
        })
        // Model should not be saved (unchanged)
        verify(manifestCatalogRepository, never()).save(argThat<ManifestCatalogEntity> {
            key == "customer" && manifestType == ManifestType.MODEL
        })
    }

    @Test
    fun `reconcileStaleEntries does nothing when all entries are already correct`() {
        val entry = ManifestCatalogEntity(
            id = UUID.randomUUID(),
            key = "customer",
            name = "Customer",
            manifestType = ManifestType.MODEL,
            stale = false
        )

        whenever(manifestCatalogRepository.findAll()).thenReturn(listOf(entry))

        val seen = setOf("customer" to ManifestType.MODEL)
        service.reconcileStaleEntries(seen)

        verify(manifestCatalogRepository, never()).save(any())
    }
}
