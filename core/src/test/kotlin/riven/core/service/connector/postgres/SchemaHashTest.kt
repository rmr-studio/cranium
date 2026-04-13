package riven.core.service.connector.postgres

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Phase 3 Wave-0 placeholder for SchemaHash (owned by plan 03-01, covers PG-06).
 * Downstream plan removes @Disabled and replaces placeholder() with real assertions.
 */
@Disabled("populated by plan 03-01")
class SchemaHashTest {

    @Test fun producesIdenticalHashForSameSchema() = placeholder()
    @Test fun producesIdenticalHashRegardlessOfColumnOrder() = placeholder()
    @Test fun producesDifferentHashOnColumnAdd() = placeholder()
    @Test fun producesDifferentHashOnColumnTypeChange() = placeholder()
    @Test fun producesDifferentHashOnColumnDrop() = placeholder()

    private fun placeholder() {}
}
