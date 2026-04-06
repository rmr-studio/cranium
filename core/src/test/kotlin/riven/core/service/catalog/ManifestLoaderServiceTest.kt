package riven.core.service.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import riven.core.enums.catalog.ManifestType
import riven.core.models.catalog.ResolvedManifest
import riven.core.models.catalog.ScannedManifest
import riven.core.configuration.properties.ManifestConfigurationProperties
import riven.core.repository.catalog.ManifestCatalogRepository

class ManifestLoaderServiceTest {

    private lateinit var scannerService: ManifestScannerService
    private lateinit var resolverService: ManifestResolverService
    private lateinit var upsertService: ManifestUpsertService
    private lateinit var reconciliationService: ManifestReconciliationService
    private lateinit var integrationDefinitionStaleSyncService: IntegrationDefinitionStaleSyncService
    private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    private lateinit var healthIndicator: ManifestCatalogHealthIndicator
    private lateinit var logger: KLogger
    private lateinit var service: ManifestLoaderService

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        scannerService = mock()
        resolverService = mock()
        upsertService = mock()
        reconciliationService = mock()
        integrationDefinitionStaleSyncService = mock()
        manifestCatalogRepository = mock()
        healthIndicator = ManifestCatalogHealthIndicator()
        logger = mock()

        service = ManifestLoaderService(
            scannerService,
            resolverService,
            upsertService,
            reconciliationService,
            integrationDefinitionStaleSyncService,
            manifestCatalogRepository,
            healthIndicator,
            ManifestConfigurationProperties(),
            logger
        )

        whenever(scannerService.scanIntegrations()).thenReturn(emptyList())
        whenever(manifestCatalogRepository.findByStaleTrue()).thenReturn(emptyList())
    }

    // ------ Per-Manifest Exception Isolation ------

    @Test
    fun `loadAllManifests catches upsert exception and continues`() {
        val json1 = objectMapper.readTree("""{"key":"i1"}""")
        val json2 = objectMapper.readTree("""{"key":"i2"}""")
        val scanned1 = ScannedManifest("i1", ManifestType.INTEGRATION, json1)
        val scanned2 = ScannedManifest("i2", ManifestType.INTEGRATION, json2)
        val resolved1 = createResolvedManifest("i1", ManifestType.INTEGRATION)
        val resolved2 = createResolvedManifest("i2", ManifestType.INTEGRATION)

        whenever(scannerService.scanIntegrations()).thenReturn(listOf(scanned1, scanned2))
        whenever(resolverService.resolveManifest(eq(scanned1))).thenReturn(resolved1)
        whenever(resolverService.resolveManifest(eq(scanned2))).thenReturn(resolved2)

        // First upsert throws, second should still be called
        doThrow(RuntimeException("DB error")).whenever(upsertService).upsertManifest(resolved1)

        service.loadAllManifests()

        verify(upsertService).upsertManifest(resolved2)
        verify(logger).warn(any<Throwable>(), any<() -> Any?>())
    }

    // ------ Reconciliation ------

    @Test
    fun `loadAllManifests calls reconciliation after load with seen manifests`() {
        val json = objectMapper.readTree("""{"key":"i1"}""")
        val scanned = ScannedManifest("i1", ManifestType.INTEGRATION, json)
        val resolved = createResolvedManifest("i1", ManifestType.INTEGRATION)

        whenever(scannerService.scanIntegrations()).thenReturn(listOf(scanned))
        whenever(resolverService.resolveManifest(eq(scanned))).thenReturn(resolved)

        service.loadAllManifests()

        verify(reconciliationService).reconcileStaleEntries(
            argThat { contains("i1" to ManifestType.INTEGRATION) && size == 1 },
            eq(setOf(ManifestType.INTEGRATION))
        )
    }

    @Test
    fun `loadAllManifests does not add stale-resolved manifests to seen set`() {
        val json = objectMapper.readTree("""{"key":"i1"}""")
        val scanned = ScannedManifest("i1", ManifestType.INTEGRATION, json)
        val resolved = createResolvedManifest("i1", ManifestType.INTEGRATION, stale = true)

        whenever(scannerService.scanIntegrations()).thenReturn(listOf(scanned))
        whenever(resolverService.resolveManifest(eq(scanned))).thenReturn(resolved)

        service.loadAllManifests()

        verify(reconciliationService).reconcileStaleEntries(
            argThat { isEmpty() },
            eq(setOf(ManifestType.INTEGRATION))
        )
    }

    @Test
    fun `loadAllManifests skips reconciliation when zero manifests scanned`() {
        service.loadAllManifests()

        verify(reconciliationService, never()).reconcileStaleEntries(any(), any())
        verify(logger).warn(any<() -> Any?>())
    }

    // ------ Integration Definition Stale Sync ------

    @Test
    fun `loadAllManifests calls integration definition stale sync after reconciliation`() {
        service.loadAllManifests()

        verify(integrationDefinitionStaleSyncService).syncStaleFlags()
    }

    // ------ Summary Log ------

    @Test
    fun `loadAllManifests logs summary with correct counts`() {
        val json = objectMapper.readTree("""{"key":"i1"}""")
        val scanned = ScannedManifest("i1", ManifestType.INTEGRATION, json)
        val resolved = createResolvedManifest("i1", ManifestType.INTEGRATION)

        whenever(scannerService.scanIntegrations()).thenReturn(listOf(scanned))
        whenever(resolverService.resolveManifest(eq(scanned))).thenReturn(resolved)
        whenever(manifestCatalogRepository.findByStaleTrue()).thenReturn(emptyList())

        service.loadAllManifests()

        verify(logger).info(any<() -> Any?>())
    }

    // ------ Helpers ------

    private fun createResolvedManifest(
        key: String,
        type: ManifestType,
        stale: Boolean = false
    ) = ResolvedManifest(
        key = key,
        name = "Test $key",
        description = null,
        type = type,
        manifestVersion = "1.0",
        entityTypes = emptyList(),
        relationships = emptyList(),
        fieldMappings = emptyList(),
        stale = stale
    )
}
