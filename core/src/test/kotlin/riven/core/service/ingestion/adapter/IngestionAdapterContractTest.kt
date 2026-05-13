package cranium.core.service.ingestion.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import cranium.core.models.ingestion.adapter.ColumnSchema
import cranium.core.models.ingestion.adapter.RecordBatch
import cranium.core.models.ingestion.adapter.SchemaIntrospectionResult
import cranium.core.models.ingestion.adapter.SourceRecord
import cranium.core.models.ingestion.adapter.SyncMode
import cranium.core.models.ingestion.adapter.TableSchema
import java.util.UUID

/**
 * Contract-level test for [IngestionAdapter]. A private [FakeAdapter] inline
 * implementation proves the interface surface (three methods + [AdapterCallContext])
 * is usable by callers without any Spring wiring.
 */
class IngestionAdapterContractTest {

    private class FakeAdapter(
        private val mode: SyncMode,
        private val batch: RecordBatch,
        private val schema: SchemaIntrospectionResult,
    ) : IngestionAdapter {
        override fun syncMode(): SyncMode = mode
        override fun introspectSchema(context: AdapterCallContext): SchemaIntrospectionResult = schema
        override fun fetchRecords(context: AdapterCallContext, cursor: String?, limit: Int): RecordBatch = batch
    }

    private fun nangoContext(): NangoCallContext = NangoCallContext(
        workspaceId = UUID.randomUUID(),
        providerConfigKey = "shopify",
        connectionId = "conn-1",
        model = "Customer",
        modifiedAfter = null,
    )

    private fun sampleBatch(): RecordBatch = RecordBatch(
        records = listOf(
            SourceRecord(
                externalId = "ext-1",
                payload = mapOf("name" to "Alice"),
                sourceMetadata = mapOf("source" to "fake"),
            ),
        ),
        nextCursor = "cursor-2",
        hasMore = true,
    )

    private fun sampleSchema(): SchemaIntrospectionResult = SchemaIntrospectionResult(
        tables = listOf(
            TableSchema(
                name = "customers",
                columns = listOf(
                    ColumnSchema(name = "id", typeLiteral = "uuid", nullable = false),
                    ColumnSchema(name = "name", typeLiteral = "text", nullable = true),
                ),
            ),
        ),
    )

    @Test
    fun `fake adapter returns the batch it was constructed with`() {
        val batch = sampleBatch()
        val adapter: IngestionAdapter = FakeAdapter(
            mode = SyncMode.POLL,
            batch = batch,
            schema = sampleSchema(),
        )

        val result = adapter.fetchRecords(nangoContext(), cursor = null, limit = 100)

        assertThat(result.records).isEqualTo(batch.records)
        assertThat(result.nextCursor).isEqualTo("cursor-2")
        assertThat(result.hasMore).isTrue()
    }

    @Test
    fun `fake adapter exposes its declared sync mode`() {
        SyncMode.entries.forEach { mode ->
            val adapter: IngestionAdapter = FakeAdapter(
                mode = mode,
                batch = sampleBatch(),
                schema = sampleSchema(),
            )
            assertThat(adapter.syncMode()).isEqualTo(mode)
        }
    }

    @Test
    fun `fake adapter returns the schema it was constructed with`() {
        val schema = sampleSchema()
        val adapter: IngestionAdapter = FakeAdapter(
            mode = SyncMode.POLL,
            batch = sampleBatch(),
            schema = schema,
        )

        val result = adapter.introspectSchema(nangoContext())

        assertThat(result).isEqualTo(schema)
        assertThat(result.tables).hasSize(1)
        assertThat(result.tables[0].name).isEqualTo("customers")
    }

    @Test
    fun `NangoCallContext is a valid AdapterCallContext`() {
        val ctx: AdapterCallContext = NangoCallContext(
            workspaceId = UUID.randomUUID(),
            providerConfigKey = "shopify",
            connectionId = "conn-1",
            model = "Customer",
            modifiedAfter = null,
        )
        assertThat(ctx).isInstanceOf(NangoCallContext::class.java)
    }
}
