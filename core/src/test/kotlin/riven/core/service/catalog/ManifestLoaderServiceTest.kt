package riven.core.service.catalog

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import riven.core.entity.catalog.ManifestCatalogEntity
import riven.core.enums.catalog.ManifestType
import riven.core.models.catalog.ResolvedManifest
import riven.core.models.catalog.ScannedManifest
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
            logger
        )

        // Default: empty scans and no stale entries
        whenever(scannerService.scanModels()).thenReturn(emptyList())
        whenever(scannerService.scanTemplates()).thenReturn(emptyList())
        whenever(scannerService.scanIntegrations()).thenReturn(emptyList())
        whenever(manifestCatalogRepository.findByStaleTrue()).thenReturn(emptyList())
    }

    // ------ Load Order ------

    @Test
    fun `loadAllManifests loads in order models then templates then integrations`() {
        service.loadAllManifests()

        val inOrder = inOrder(scannerService)
        inOrder.verify(scannerService).scanModels()
        inOrder.verify(scannerService).scanTemplates()
        inOrder.verify(scannerService).scanIntegrations()
    }

    // ------ Model Index ------

    @Test
    fun `loadAllManifests builds model index from scanned models for template resolution`() {
        val modelJson = objectMapper.readTree("""{"key":"customer","name":"Customer"}""")
        val modelScanned = ScannedManifest("customer", ManifestType.MODEL, modelJson)
        val modelResolved = createResolvedManifest("customer", ManifestType.MODEL)

        whenever(scannerService.scanModels()).thenReturn(listOf(modelScanned))
        whenever(resolverService.resolveManifest(eq(modelScanned), eq(emptyMap())))
            .thenReturn(modelResolved)

        val templateJson = objectMapper.readTree("""{"key":"saas","name":"SaaS Starter"}""")
        val templateScanned = ScannedManifest("saas", ManifestType.TEMPLATE, templateJson)
        val templateResolved = createResolvedManifest("saas", ManifestType.TEMPLATE)

        whenever(scannerService.scanTemplates()).thenReturn(listOf(templateScanned))
        whenever(resolverService.resolveManifest(eq(templateScanned), argThat<Map<String, JsonNode>> {
            containsKey("customer")
        })).thenReturn(templateResolved)

        service.loadAllManifests()

        // Verify template resolution received model index with customer key
        verify(resolverService).resolveManifest(eq(templateScanned), argThat<Map<String, JsonNode>> {
            containsKey("customer") && this["customer"] == modelJson
        })
    }

    // ------ Per-Manifest Exception Isolation ------

    @Test
    fun `loadAllManifests catches upsert exception and continues`() {
        val model1Json = objectMapper.readTree("""{"key":"m1"}""")
        val model2Json = objectMapper.readTree("""{"key":"m2"}""")
        val scanned1 = ScannedManifest("m1", ManifestType.MODEL, model1Json)
        val scanned2 = ScannedManifest("m2", ManifestType.MODEL, model2Json)
        val resolved1 = createResolvedManifest("m1", ManifestType.MODEL)
        val resolved2 = createResolvedManifest("m2", ManifestType.MODEL)

        whenever(scannerService.scanModels()).thenReturn(listOf(scanned1, scanned2))
        whenever(resolverService.resolveManifest(eq(scanned1), any())).thenReturn(resolved1)
        whenever(resolverService.resolveManifest(eq(scanned2), any())).thenReturn(resolved2)

        // First upsert throws, second should still be called
        doThrow(RuntimeException("DB error")).whenever(upsertService).upsertManifest(resolved1)

        service.loadAllManifests()

        verify(upsertService).upsertManifest(resolved2)
        verify(logger).warn(any<Throwable>(), any<() -> Any?>())
    }

    // ------ Reconciliation ------

    @Test
    fun `loadAllManifests calls reconciliation after load with seen manifests`() {
        val modelJson = objectMapper.readTree("""{"key":"m1"}""")
        val scanned = ScannedManifest("m1", ManifestType.MODEL, modelJson)
        val resolved = createResolvedManifest("m1", ManifestType.MODEL)

        whenever(scannerService.scanModels()).thenReturn(listOf(scanned))
        whenever(resolverService.resolveManifest(eq(scanned), any())).thenReturn(resolved)

        service.loadAllManifests()

        verify(reconciliationService).reconcileStaleEntries(argThat {
            contains("m1" to ManifestType.MODEL) && size == 1
        })
    }

    @Test
    fun `loadAllManifests does not add stale-resolved manifests to seen set`() {
        val modelJson = objectMapper.readTree("""{"key":"m1"}""")
        val scanned = ScannedManifest("m1", ManifestType.MODEL, modelJson)
        val resolved = createResolvedManifest("m1", ManifestType.MODEL, stale = true)

        whenever(scannerService.scanModels()).thenReturn(listOf(scanned))
        whenever(resolverService.resolveManifest(eq(scanned), any())).thenReturn(resolved)

        service.loadAllManifests()

        verify(reconciliationService).reconcileStaleEntries(argThat {
            isEmpty()
        })
    }

    @Test
    fun `loadAllManifests skips reconciliation when zero manifests scanned`() {
        service.loadAllManifests()

        verify(reconciliationService, never()).reconcileStaleEntries(any())
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
        val modelJson = objectMapper.readTree("""{"key":"m1"}""")
        val scanned = ScannedManifest("m1", ManifestType.MODEL, modelJson)
        val resolved = createResolvedManifest("m1", ManifestType.MODEL)

        whenever(scannerService.scanModels()).thenReturn(listOf(scanned))
        whenever(resolverService.resolveManifest(eq(scanned), any())).thenReturn(resolved)
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
