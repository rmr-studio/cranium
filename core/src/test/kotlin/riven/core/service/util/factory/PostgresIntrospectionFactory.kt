package riven.core.service.util.factory

import riven.core.models.ingestion.adapter.ColumnSchema
import riven.core.models.ingestion.adapter.SchemaIntrospectionResult
import riven.core.models.ingestion.adapter.TableSchema

/** Test factory for Postgres adapter schema-introspection fixtures (Phase 3). */
object PostgresIntrospectionFactory {

    fun column(
        name: String = "id",
        type: String = "uuid",
        nullable: Boolean = false,
    ): ColumnSchema = ColumnSchema(name = name, typeLiteral = type, nullable = nullable)

    fun table(
        name: String = "customers",
        columns: List<ColumnSchema> = listOf(column()),
    ): TableSchema = TableSchema(name = name, columns = columns)

    fun result(
        tables: List<TableSchema> = listOf(table()),
    ): SchemaIntrospectionResult = SchemaIntrospectionResult(tables = tables)
}
