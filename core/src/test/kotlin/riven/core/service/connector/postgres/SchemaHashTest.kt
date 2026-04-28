package riven.core.service.connector.postgres

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import riven.core.service.util.factory.PostgresIntrospectionFactory.column

/**
 * Phase 3 plan 03-01 PG-06 coverage for [SchemaHasher]. Test names inherit
 * verbatim from the 03-00 Wave-0 scaffold.
 */
class SchemaHashTest {

    private val tableName = "customers"

    private val baseColumns = listOf(
        column(name = "id", type = "uuid", nullable = false),
        column(name = "email", type = "text", nullable = false),
        column(name = "updated_at", type = "timestamptz", nullable = true),
    )

    @Test
    fun producesIdenticalHashForSameSchema() {
        val a = SchemaHasher.compute(tableName, baseColumns)
        val b = SchemaHasher.compute(tableName, baseColumns)

        assertThat(a).isEqualTo(b)
        // SHA-256 lowercase hex contract: 64 chars, [0-9a-f].
        assertThat(a).hasSize(64).matches("^[0-9a-f]{64}$")
    }

    @Test
    fun producesIdenticalHashRegardlessOfColumnOrder() {
        val shuffled = baseColumns.reversed()
        assertThat(SchemaHasher.compute(tableName, baseColumns))
            .isEqualTo(SchemaHasher.compute(tableName, shuffled))
    }

    @Test
    fun producesDifferentHashOnColumnAdd() {
        val added = baseColumns + column(name = "name", type = "text", nullable = true)
        assertThat(SchemaHasher.compute(tableName, baseColumns))
            .isNotEqualTo(SchemaHasher.compute(tableName, added))
    }

    @Test
    fun producesDifferentHashOnColumnTypeChange() {
        val retyped = baseColumns.map {
            if (it.name == "email") it.copy(typeLiteral = "varchar") else it
        }
        assertThat(SchemaHasher.compute(tableName, baseColumns))
            .isNotEqualTo(SchemaHasher.compute(tableName, retyped))
    }

    @Test
    fun producesDifferentHashOnColumnDrop() {
        val dropped = baseColumns.filterNot { it.name == "updated_at" }
        assertThat(SchemaHasher.compute(tableName, baseColumns))
            .isNotEqualTo(SchemaHasher.compute(tableName, dropped))
    }
}
