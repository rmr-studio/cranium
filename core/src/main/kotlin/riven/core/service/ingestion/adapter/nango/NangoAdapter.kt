package riven.core.service.ingestion.adapter.nango

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Component
import riven.core.enums.integration.SourceType
import riven.core.exceptions.NangoApiException
import riven.core.exceptions.RateLimitException
import riven.core.exceptions.TransientNangoException
import riven.core.models.ingestion.adapter.RecordBatch
import riven.core.models.ingestion.adapter.SchemaIntrospectionResult
import riven.core.models.ingestion.adapter.SourceRecord
import riven.core.models.ingestion.adapter.SyncMode
import riven.core.models.integration.NangoRecord
import riven.core.models.integration.NangoRecordsPage
import riven.core.service.ingestion.adapter.AdapterCallContext
import riven.core.service.ingestion.adapter.IngestionAdapter
import riven.core.service.ingestion.adapter.NangoCallContext
import riven.core.service.ingestion.adapter.SourceTypeAdapter
import riven.core.service.ingestion.adapter.exception.AdapterAuthException
import riven.core.service.ingestion.adapter.exception.AdapterCapabilityNotSupportedException
import riven.core.service.ingestion.adapter.exception.AdapterConnectionRefusedException
import riven.core.service.ingestion.adapter.exception.AdapterUnavailableException
import riven.core.service.ingestion.adapter.exception.FatalAdapterException
import riven.core.service.ingestion.adapter.exception.TransientAdapterException
import riven.core.service.integration.NangoClientWrapper

/**
 * IngestionAdapter implementation that delegates to [NangoClientWrapper].
 *
 * Phase 1 responsibility: expose Nango-backed integrations through the neutral
 * adapter contract and translate Nango-specific exceptions into the
 * [riven.core.service.ingestion.adapter.exception.AdapterException] hierarchy
 * so the Phase 4 Temporal orchestrator can classify retries uniformly.
 *
 * This adapter is registered but NOT yet consumed by
 * `IntegrationSyncWorkflowImpl`; the existing Nango sync path remains
 * byte-identical until Phase 4+ unifies ingestion through
 * `IngestionOrchestrator`.
 */
@Component
@SourceTypeAdapter(SourceType.INTEGRATION)
class NangoAdapter(
    private val nangoClientWrapper: NangoClientWrapper,
    private val logger: KLogger,
) : IngestionAdapter {

    override fun syncMode(): SyncMode = SyncMode.PUSH

    override fun introspectSchema(context: AdapterCallContext): SchemaIntrospectionResult {
        throw AdapterCapabilityNotSupportedException(
            "Nango schema is derived from integration manifests, not runtime introspection. " +
                "Use the integration definition for schema shape."
        )
    }

    override fun fetchRecords(context: AdapterCallContext, cursor: String?, limit: Int): RecordBatch {
        val ctx = context as? NangoCallContext
            ?: throw AdapterCapabilityNotSupportedException(
                "NangoAdapter requires NangoCallContext, got ${context::class.simpleName}"
            )
        return try {
            val page = nangoClientWrapper.fetchRecords(
                providerConfigKey = ctx.providerConfigKey,
                connectionId = ctx.connectionId,
                model = ctx.model,
                cursor = cursor,
                modifiedAfter = ctx.modifiedAfter?.toString(),
                limit = limit,
            )
            page.toRecordBatch()
        } catch (e: RateLimitException) {
            logger.warn(e) { "Nango rate limit on fetchRecords(connection=${ctx.connectionId}, model=${ctx.model})" }
            throw TransientAdapterException(e.message ?: "Nango rate limit", e)
        } catch (e: TransientNangoException) {
            logger.warn(e) { "Nango transient error on fetchRecords(connection=${ctx.connectionId}, model=${ctx.model})" }
            throw TransientAdapterException(e.message ?: "Nango transient error", e)
        } catch (e: NangoApiException) {
            logger.warn(e) { "Nango API error ${e.statusCode} on fetchRecords(connection=${ctx.connectionId}, model=${ctx.model})" }
            throw mapNangoApiException(e)
        }
    }

    // ------ Private helpers ------

    private fun mapNangoApiException(e: NangoApiException): FatalAdapterException = when (e.statusCode) {
        401, 403 -> AdapterAuthException(e.message ?: "Nango auth failed", e)
        404 -> AdapterConnectionRefusedException(e.message ?: "Nango resource not found", e)
        else -> AdapterUnavailableException(e.message ?: "Nango API error (status ${e.statusCode})", e)
    }

    private fun NangoRecordsPage.toRecordBatch(): RecordBatch = RecordBatch(
        records = records.map { it.toSourceRecord() },
        nextCursor = nextCursor,
        hasMore = nextCursor != null,
    )

    private fun NangoRecord.toSourceRecord(): SourceRecord {
        val externalId = payload["id"]?.toString()
            ?: nangoMetadata.cursor
        val sourceMetadata = mapOf<String, Any?>(
            "lastAction" to nangoMetadata.lastAction.name,
            "cursor" to nangoMetadata.cursor,
            "firstSeenAt" to nangoMetadata.firstSeenAt,
            "lastModifiedAt" to nangoMetadata.lastModifiedAt,
            "deletedAt" to nangoMetadata.deletedAt,
        )
        return SourceRecord(
            externalId = externalId,
            payload = payload.toMap(),
            sourceMetadata = sourceMetadata,
        )
    }
}
