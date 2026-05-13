package cranium.core.service.ingestion.adapter

import java.time.Instant
import java.util.UUID

/**
 * Per-call context carrying connection identity and any per-call parameters.
 *
 * Sealed — Phase 1 ships the base plus [NangoCallContext]; Phase 3 will add a
 * `PostgresCallContext` variant carrying pool + schema coordinates.
 */
sealed class AdapterCallContext {
    /** Workspace this adapter call is executing on behalf of. */
    abstract val workspaceId: UUID
}

/**
 * Call context for Nango-backed adapters.
 *
 * [workspaceId] is required — the Phase 4 `IngestionOrchestrator` must
 * populate it from the triggering workflow so workspace attribution is
 * never silently dropped.
 */
data class NangoCallContext(
    override val workspaceId: UUID,
    val providerConfigKey: String,
    val connectionId: String,
    val model: String,
    val modifiedAfter: Instant? = null,
) : AdapterCallContext()
