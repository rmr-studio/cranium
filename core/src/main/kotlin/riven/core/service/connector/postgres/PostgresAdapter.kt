package riven.core.service.connector.postgres

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import riven.core.enums.integration.SourceType
import riven.core.models.connector.CredentialPayload
import riven.core.models.ingestion.adapter.RecordBatch
import riven.core.models.ingestion.adapter.SchemaIntrospectionResult
import riven.core.models.ingestion.adapter.SyncMode
import riven.core.repository.connector.DataConnectorConnectionRepository
import riven.core.service.connector.CredentialEncryptionService
import riven.core.service.connector.EncryptedCredentials
import riven.core.service.connector.pool.WorkspaceConnectionPoolManager
import riven.core.service.ingestion.adapter.AdapterCallContext
import riven.core.service.ingestion.adapter.IngestionAdapter
import riven.core.service.ingestion.adapter.PostgresCallContext
import riven.core.service.ingestion.adapter.SourceTypeAdapter
import riven.core.service.ingestion.adapter.exception.AdapterAuthException
import riven.core.service.ingestion.adapter.exception.AdapterConnectionRefusedException
import riven.core.service.ingestion.adapter.exception.AdapterException
import riven.core.service.ingestion.adapter.exception.AdapterUnavailableException
import riven.core.util.ServiceUtil.findOrThrow
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Postgres-backed [IngestionAdapter] — Phase 3 PG-01..05.
 *
 * Resolves the per-call pool via [WorkspaceConnectionPoolManager], delegates
 * schema introspection to [PostgresIntrospector] and record fetching to
 * [PostgresFetcher]. All JDBC [SQLException]s flow through a single translator
 * that maps the PG SQLState onto the Phase 1 [AdapterException] hierarchy so
 * the Phase 4 Temporal orchestrator can classify retries uniformly.
 *
 * Registered with `@SourceTypeAdapter(SourceType.CONNECTOR)` — discovered by
 * [riven.core.service.ingestion.adapter.SourceTypeAdapterRegistry].
 */
@Component
@SourceTypeAdapter(SourceType.CONNECTOR)
@ConditionalOnProperty(prefix = "riven.connector", name = ["enabled"], havingValue = "true")
class PostgresAdapter(
    private val poolManager: WorkspaceConnectionPoolManager,
    private val connectionRepository: DataConnectorConnectionRepository,
    private val encryptionService: CredentialEncryptionService,
    private val introspector: PostgresIntrospector,
    private val fetcher: PostgresFetcher,
    private val objectMapper: ObjectMapper,
    @Suppress("unused") private val logger: KLogger,
) : IngestionAdapter {

    override fun syncMode(): SyncMode = SyncMode.POLL

    override fun introspectSchema(context: AdapterCallContext): SchemaIntrospectionResult {
        val ctx = requirePostgresContext(context)
        val dataSource = resolveDataSource(ctx)
        return runCatchingJdbc { introspector.introspect(dataSource, ctx.schema).schema }
    }

    /**
     * Sibling of [introspectSchema] that additionally surfaces FK metadata —
     * consumed by plan 03-03's relationship materialiser. Keeps the Phase 1
     * [IngestionAdapter] contract unchanged.
     */
    fun introspectWithFkMetadata(context: AdapterCallContext): IntrospectionResult {
        val ctx = requirePostgresContext(context)
        val dataSource = resolveDataSource(ctx)
        return runCatchingJdbc { introspector.introspect(dataSource, ctx.schema) }
    }

    override fun fetchRecords(
        context: AdapterCallContext,
        cursor: String?,
        limit: Int,
    ): RecordBatch {
        val ctx = requirePostgresContext(context)
        val dataSource = resolveDataSource(ctx)
        return runCatchingJdbc { fetcher.fetch(dataSource, ctx, cursor, limit) }
    }

    // ------ Private helpers ------

    private fun requirePostgresContext(context: AdapterCallContext): PostgresCallContext {
        require(context is PostgresCallContext) {
            "PostgresAdapter requires PostgresCallContext, got ${context::class.simpleName}"
        }
        return context
    }

    private fun resolveDataSource(ctx: PostgresCallContext): DataSource {
        val entity = findOrThrow { connectionRepository.findByIdAndWorkspaceId(ctx.connectionId, ctx.workspaceId) }
        val decryptedJson = encryptionService.decrypt(
            EncryptedCredentials(entity.encryptedCredentials, entity.iv, entity.keyVersion),
        )
        val payload: CredentialPayload = objectMapper.readValue(decryptedJson)
        return poolManager.getPool(ctx.connectionId, payload)
    }

    /**
     * Runs [block] and translates any [SQLException] cause chain into a Phase 1
     * [AdapterException]. Pre-existing adapter exceptions rethrow verbatim.
     */
    private inline fun <T> runCatchingJdbc(block: () -> T): T {
        return try {
            block()
        } catch (e: AdapterException) {
            throw e
        } catch (e: RuntimeException) {
            val sqlCause = findSqlException(e)
            if (sqlCause != null) throw mapJdbcException(sqlCause, e)
            throw e
        } catch (e: SQLException) {
            throw mapJdbcException(e, e)
        }
    }

    private fun findSqlException(e: Throwable): SQLException? {
        var cur: Throwable? = e
        while (cur != null) {
            if (cur is SQLException) return cur
            cur = cur.cause
        }
        return null
    }

    /**
     * Translate a JDBC [SQLException] into the adapter exception hierarchy:
     *  - 28xxx → [AdapterAuthException] (auth failure)
     *  - 57014 → [AdapterUnavailableException] (query_canceled, timeout)
     *  - 08xxx → [AdapterConnectionRefusedException]
     *  - default → [AdapterUnavailableException]
     */
    private fun mapJdbcException(sql: SQLException, original: Throwable): AdapterException {
        val state = sql.sqlState ?: ""
        val message = sql.message ?: "JDBC error"
        return when {
            state.startsWith("28") -> AdapterAuthException("Postgres auth failed: $message", original)
            state == "57014" -> AdapterUnavailableException("Postgres query cancelled (timeout): $message", original)
            state.startsWith("08") -> AdapterConnectionRefusedException("Postgres connection refused: $message", original)
            else -> AdapterUnavailableException("Postgres error (SQLState=$state): $message", original)
        }
    }
}
