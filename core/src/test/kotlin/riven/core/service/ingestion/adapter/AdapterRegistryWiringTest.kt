package cranium.core.service.ingestion.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.util.LoggerConfig
import cranium.core.enums.integration.SourceType
import cranium.core.service.ingestion.adapter.nango.NangoAdapter
import cranium.core.service.integration.NangoClientWrapper

/**
 * Verifies that [SourceTypeAdapterRegistry] assembles the
 * `Map<SourceType, IngestionAdapter>` Spring bean consumed by Phase 4's
 * IngestionOrchestrator. Ensures [NangoAdapter] is bound to
 * [SourceType.INTEGRATION] via its `@SourceTypeAdapter` qualifier.
 */
@SpringBootTest(classes = [NangoAdapter::class, SourceTypeAdapterRegistry::class, LoggerConfig::class])
class AdapterRegistryWiringTest {

    @MockitoBean
    private lateinit var nangoClientWrapper: NangoClientWrapper

    @Autowired
    private lateinit var registry: Map<SourceType, IngestionAdapter>

    @Test
    fun `registry contains INTEGRATION binding`() {
        assertThat(registry).containsKey(SourceType.INTEGRATION)
    }

    @Test
    fun `INTEGRATION entry is NangoAdapter`() {
        assertThat(registry[SourceType.INTEGRATION]).isInstanceOf(NangoAdapter::class.java)
    }

    @Test
    fun `registry has exactly one adapter in Phase 1`() {
        assertThat(registry).hasSize(1)
    }
}
