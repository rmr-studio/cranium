package riven.core.service.connector.postgres

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Phase 3 Wave-0 placeholder for PostgresAdapter (owned by plan 03-02, covers PG-01..03, PG-05).
 * Downstream plan removes @Disabled and replaces placeholder() with real assertions.
 */
@Disabled("populated by plan 03-02")
class PostgresAdapterTest {

    @Test fun fetchRecordsUsesUpdatedAtCursorWhenColumnPresent() = placeholder()
    @Test fun fetchRecordsFallsBackToPkInsertsOnlyWhenNoCursor() = placeholder()
    @Test fun fetchRecordsHonorsLimitStrictly() = placeholder()
    @Test fun fetchRecordsRoundTripsJsonbAsObjectStructure() = placeholder()
    @Test fun fetchRecordsReturnsEmptyBatchWithHasMoreFalse() = placeholder()
    @Test fun introspectSchemaReturnsTablesWithFkMetadata() = placeholder()
    @Test fun syncModeReturnsPOLL() = placeholder()
    @Test fun mapsPostgresAuthFailureToFatalAdapterAuthException() = placeholder()
    @Test fun mapsPostgresTimeoutToTransientAdapterException() = placeholder()

    private fun placeholder() {}
}
