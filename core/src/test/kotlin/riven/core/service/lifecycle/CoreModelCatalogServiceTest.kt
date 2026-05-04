package riven.core.service.lifecycle

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.models.catalog.ResolvedManifest
import riven.core.service.catalog.CoreModelCatalogService
import riven.core.service.catalog.ManifestCatalogHealthIndicator
import riven.core.service.catalog.ManifestUpsertService

@SpringBootTest(classes = [CoreModelCatalogService::class])
class CoreModelCatalogServiceTest {

    @MockitoBean
    private lateinit var upsertService: ManifestUpsertService

    @MockitoBean
    private lateinit var healthIndicator: ManifestCatalogHealthIndicator

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: CoreModelCatalogService

    @BeforeEach
    fun setUp() {
        // Clear invocations from ApplicationReadyEvent firing during context startup
        clearInvocations(upsertService)
    }

    @Test
    fun `onApplicationReady upserts every registered core model set to catalog`() {
        service.onApplicationReady()

        // dtc-ecommerce + knowledge ship today.
        verify(upsertService, times(2)).upsertManifest(any())
    }

    @Test
    fun `onApplicationReady upserts dtc-ecommerce with correct key`() {
        service.onApplicationReady()

        verify(upsertService).upsertManifest(argThat<ResolvedManifest> {
            key == "dtc-ecommerce" && entityTypes.any { it.key == "order" }
        })
    }

    @Test
    fun `onApplicationReady upserts knowledge with correct key`() {
        service.onApplicationReady()

        verify(upsertService).upsertManifest(argThat<ResolvedManifest> {
            key == "knowledge" && entityTypes.any { it.key == "note" }
        })
    }

    // ------ Error Resilience Tests ------

    @Test
    fun `upsert failure sets health indicator to FAILED`() {
        whenever(upsertService.upsertManifest(any()))
            .thenThrow(RuntimeException("simulated failure"))

        service.onApplicationReady()

        verify(upsertService, times(2)).upsertManifest(any())
        verify(healthIndicator).loadState = ManifestCatalogHealthIndicator.LoadState.FAILED
        verify(healthIndicator).lastError = argThat<String> { contains("2/2") }
    }

    @Test
    fun `successful load does not set health indicator to FAILED`() {
        service.onApplicationReady()

        verify(upsertService, times(2)).upsertManifest(any())
        verify(healthIndicator, never()).loadState = ManifestCatalogHealthIndicator.LoadState.FAILED
        verify(healthIndicator, never()).lastError = any()
    }
}
