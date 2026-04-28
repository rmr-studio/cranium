package riven.core.service.ingestion.adapter.nango

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.util.LoggerConfig
import riven.core.exceptions.NangoApiException
import riven.core.exceptions.RateLimitException
import riven.core.exceptions.TransientNangoException
import riven.core.models.integration.NangoRecord
import riven.core.models.integration.NangoRecordAction
import riven.core.models.integration.NangoRecordMetadata
import java.util.UUID
import riven.core.models.integration.NangoRecordsPage
import riven.core.models.ingestion.adapter.SyncMode
import riven.core.service.ingestion.adapter.NangoCallContext
import riven.core.service.ingestion.adapter.exception.AdapterAuthException
import riven.core.service.ingestion.adapter.exception.AdapterCapabilityNotSupportedException
import riven.core.service.ingestion.adapter.exception.AdapterConnectionRefusedException
import riven.core.service.ingestion.adapter.exception.AdapterUnavailableException
import riven.core.service.ingestion.adapter.exception.TransientAdapterException
import riven.core.service.integration.NangoClientWrapper

/**
 * Contract + behaviour tests for [NangoAdapter].
 *
 * Verifies:
 *  - syncMode() == PUSH
 *  - introspectSchema throws typed AdapterCapabilityNotSupportedException
 *  - fetchRecords delegates to NangoClientWrapper with NangoCallContext fields
 *  - NangoRecord → SourceRecord translation preserves externalId, payload, metadata
 *  - Nango exception → AdapterException translation table
 */
@SpringBootTest(classes = [NangoAdapter::class, LoggerConfig::class])
class NangoAdapterTest {

    @MockitoBean
    private lateinit var nangoClientWrapper: NangoClientWrapper

    @Autowired
    private lateinit var adapter: NangoAdapter

    private fun ctx(model: String = "Contact") = NangoCallContext(
        workspaceId = UUID.randomUUID(),
        providerConfigKey = "hubspot",
        connectionId = "conn-1",
        model = model,
        modifiedAfter = null,
    )

    private fun record(id: String = "ext-1", payload: MutableMap<String, Any?> = mutableMapOf("name" to "Alice")): NangoRecord =
        NangoRecord(
            nangoMetadata = NangoRecordMetadata(
                lastAction = NangoRecordAction.ADDED,
                cursor = "c-$id",
                firstSeenAt = "2026-04-12T00:00:00Z",
                lastModifiedAt = "2026-04-12T00:00:00Z",
            ),
            payload = payload.apply { put("id", id) },
        )

    // ---- syncMode ------------------------------------------------------

    @Test
    fun `syncMode returns PUSH`() {
        assertThat(adapter.syncMode()).isEqualTo(SyncMode.PUSH)
    }

    // ---- introspectSchema ---------------------------------------------

    @Test
    fun `introspectSchema throws AdapterCapabilityNotSupportedException`() {
        assertThatThrownBy { adapter.introspectSchema(ctx()) }
            .isInstanceOf(AdapterCapabilityNotSupportedException::class.java)
    }

    // ---- fetchRecords delegation --------------------------------------

    @Test
    fun `fetchRecords delegates to NangoClientWrapper with context fields`() {
        whenever(
            nangoClientWrapper.fetchRecords(
                any<String>(), any<String>(), any<String>(),
                anyOrNull<String>(), anyOrNull<String>(), anyOrNull<Int>(),
            )
        ).thenReturn(NangoRecordsPage(records = emptyList(), nextCursor = null))

        val result = adapter.fetchRecords(ctx(), cursor = "t-1", limit = 50)

        assertThat(result.records).isEmpty()
        assertThat(result.nextCursor).isNull()
        assertThat(result.hasMore).isFalse()

        verify(nangoClientWrapper).fetchRecords(
            providerConfigKey = eq("hubspot"),
            connectionId = eq("conn-1"),
            model = eq("Contact"),
            cursor = eq("t-1"),
            modifiedAfter = anyOrNull(),
            limit = eq(50),
        )
    }

    @Test
    fun `fetchRecords translates NangoRecord into SourceRecord with externalId + payload + metadata`() {
        whenever(
            nangoClientWrapper.fetchRecords(
                any<String>(), any<String>(), any<String>(),
                anyOrNull<String>(), anyOrNull<String>(), anyOrNull<Int>(),
            )
        ).thenReturn(
            NangoRecordsPage(
                records = listOf(record(id = "ext-42", payload = mutableMapOf("name" to "Alice"))),
                nextCursor = "next-cursor",
            )
        )

        val batch = adapter.fetchRecords(ctx(), cursor = null, limit = 100)

        assertThat(batch.records).hasSize(1)
        val sr = batch.records.single()
        assertThat(sr.externalId).isEqualTo("ext-42")
        assertThat(sr.payload).containsEntry("name", "Alice")
        assertThat(sr.payload).containsEntry("id", "ext-42")
        assertThat(sr.sourceMetadata).isNotNull()
        assertThat(sr.sourceMetadata!!).containsKey("cursor")
        assertThat(batch.nextCursor).isEqualTo("next-cursor")
        assertThat(batch.hasMore).isTrue()
    }

    // ---- Error translation --------------------------------------------

    @Test
    fun `RateLimitException translates to TransientAdapterException with cause preserved`() {
        val underlying = RateLimitException("rate limited, retry after 60s")
        whenever(
            nangoClientWrapper.fetchRecords(
                any<String>(), any<String>(), any<String>(),
                anyOrNull<String>(), anyOrNull<String>(), anyOrNull<Int>(),
            )
        ).thenThrow(underlying)

        assertThatThrownBy { adapter.fetchRecords(ctx(), cursor = null, limit = 10) }
            .isInstanceOf(TransientAdapterException::class.java)
            .hasCause(underlying)
    }

    @Test
    fun `TransientNangoException translates to TransientAdapterException with cause preserved`() {
        val underlying = TransientNangoException("Nango 503", 503)
        whenever(
            nangoClientWrapper.fetchRecords(
                any<String>(), any<String>(), any<String>(),
                anyOrNull<String>(), anyOrNull<String>(), anyOrNull<Int>(),
            )
        ).thenThrow(underlying)

        assertThatThrownBy { adapter.fetchRecords(ctx(), cursor = null, limit = 10) }
            .isInstanceOf(TransientAdapterException::class.java)
            .hasCause(underlying)
    }

    @Test
    fun `NangoApiException 401 translates to AdapterAuthException`() {
        val underlying = NangoApiException("unauthorized", 401)
        whenever(
            nangoClientWrapper.fetchRecords(
                any<String>(), any<String>(), any<String>(),
                anyOrNull<String>(), anyOrNull<String>(), anyOrNull<Int>(),
            )
        ).thenThrow(underlying)

        assertThatThrownBy { adapter.fetchRecords(ctx(), cursor = null, limit = 10) }
            .isInstanceOf(AdapterAuthException::class.java)
            .hasCause(underlying)
    }

    @Test
    fun `NangoApiException 403 translates to AdapterAuthException`() {
        val underlying = NangoApiException("forbidden", 403)
        whenever(
            nangoClientWrapper.fetchRecords(
                any<String>(), any<String>(), any<String>(),
                anyOrNull<String>(), anyOrNull<String>(), anyOrNull<Int>(),
            )
        ).thenThrow(underlying)

        assertThatThrownBy { adapter.fetchRecords(ctx(), cursor = null, limit = 10) }
            .isInstanceOf(AdapterAuthException::class.java)
            .hasCause(underlying)
    }

    @Test
    fun `NangoApiException 404 translates to AdapterConnectionRefusedException`() {
        val underlying = NangoApiException("not found", 404)
        whenever(
            nangoClientWrapper.fetchRecords(
                any<String>(), any<String>(), any<String>(),
                anyOrNull<String>(), anyOrNull<String>(), anyOrNull<Int>(),
            )
        ).thenThrow(underlying)

        assertThatThrownBy { adapter.fetchRecords(ctx(), cursor = null, limit = 10) }
            .isInstanceOf(AdapterConnectionRefusedException::class.java)
            .hasCause(underlying)
    }

    @Test
    fun `NangoApiException 500 translates to AdapterUnavailableException`() {
        val underlying = NangoApiException("server error", 500)
        whenever(
            nangoClientWrapper.fetchRecords(
                any<String>(), any<String>(), any<String>(),
                anyOrNull<String>(), anyOrNull<String>(), anyOrNull<Int>(),
            )
        ).thenThrow(underlying)

        assertThatThrownBy { adapter.fetchRecords(ctx(), cursor = null, limit = 10) }
            .isInstanceOf(AdapterUnavailableException::class.java)
            .hasCause(underlying)
    }
}
