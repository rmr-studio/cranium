package riven.core.service.connector.postgres

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import riven.core.enums.common.validation.SchemaType

/**
 * Phase 3 plan 03-01 PG-04 coverage for [PgTypeMapper]. Each test name is
 * inherited verbatim from the 03-00 Wave-0 scaffold so the must-have row map
 * stays intact.
 */
class PgTypeMapperTest {

    // ------ TEXT family ------

    @ParameterizedTest
    @ValueSource(strings = ["text", "TEXT", "varchar", "VARCHAR(255)", "char", "CHAR(10)", "citext"])
    fun mapsTextFamilyToTEXT(pgType: String) {
        assertThat(PgTypeMapper.toSchemaType(pgType)).isEqualTo(SchemaType.TEXT)
    }

    // ------ NUMBER family ------

    @ParameterizedTest
    @ValueSource(
        strings = [
            "int2", "int4", "int8", "int", "integer", "bigint", "smallint",
            "numeric", "numeric(10,2)", "decimal", "real",
            "float4", "float8", "double precision", "money",
        ],
    )
    fun mapsNumericFamilyToNUMBER(pgType: String) {
        assertThat(PgTypeMapper.toSchemaType(pgType)).isEqualTo(SchemaType.NUMBER)
    }

    // ------ CHECKBOX ------

    @ParameterizedTest
    @ValueSource(strings = ["bool", "boolean", "BOOLEAN"])
    fun mapsBooleanToCHECKBOX(pgType: String) {
        assertThat(PgTypeMapper.toSchemaType(pgType)).isEqualTo(SchemaType.CHECKBOX)
    }

    // ------ DATE ------

    @Test
    fun mapsDateToDATE() {
        assertThat(PgTypeMapper.toSchemaType("date")).isEqualTo(SchemaType.DATE)
        assertThat(PgTypeMapper.toSchemaType("DATE")).isEqualTo(SchemaType.DATE)
    }

    // ------ DATETIME family ------

    @ParameterizedTest
    @ValueSource(
        strings = [
            "timestamp", "timestamptz",
            "timestamp with time zone", "timestamp without time zone",
            "time", "timetz",
        ],
    )
    fun mapsTimestampFamilyToDATETIME(pgType: String) {
        assertThat(PgTypeMapper.toSchemaType(pgType)).isEqualTo(SchemaType.DATETIME)
    }

    // ------ UUID (PK vs non-PK) ------

    @Test
    fun mapsUuidPkToID_andUuidNonPkToTEXT() {
        assertThat(PgTypeMapper.toSchemaType("uuid", isPrimaryKey = true)).isEqualTo(SchemaType.ID)
        assertThat(PgTypeMapper.toSchemaType("uuid", isPrimaryKey = false)).isEqualTo(SchemaType.TEXT)
        assertThat(PgTypeMapper.toSchemaType("UUID", isPrimaryKey = true)).isEqualTo(SchemaType.ID)
    }

    // ------ User-defined enum ------

    @Test
    fun mapsEnumToSELECTWithOptions() {
        // Caller supplies `enumOptions` when the pg type is a user-defined enum.
        // The actual pg type string is user-defined (e.g. "order_status") — the
        // presence of non-null `enumOptions` is the signal, not the type literal.
        val options = listOf("PENDING", "SHIPPED", "DELIVERED")
        assertThat(
            PgTypeMapper.toSchemaType("order_status", isPrimaryKey = false, enumOptions = options),
        ).isEqualTo(SchemaType.SELECT)

        // Empty list still counts as "is an enum" — distinguishes from null.
        assertThat(
            PgTypeMapper.toSchemaType("weird_enum", enumOptions = emptyList()),
        ).isEqualTo(SchemaType.SELECT)
    }

    // ------ OBJECT: jsonb/json preserving structure contract ------

    @Test
    fun mapsJsonbToOBJECT_preservingStructure() {
        assertThat(PgTypeMapper.toSchemaType("jsonb")).isEqualTo(SchemaType.OBJECT)
        assertThat(PgTypeMapper.toSchemaType("json")).isEqualTo(SchemaType.OBJECT)
        assertThat(PgTypeMapper.toSchemaType("JSONB")).isEqualTo(SchemaType.OBJECT)
    }

    // ------ OBJECT: array types ------

    @ParameterizedTest
    @ValueSource(strings = ["_text", "_int4", "_uuid", "text[]", "int[]", "uuid[]"])
    fun mapsArrayToOBJECT(pgType: String) {
        assertThat(PgTypeMapper.toSchemaType(pgType)).isEqualTo(SchemaType.OBJECT)
    }

    // ------ OBJECT: bytea ------

    @Test
    fun mapsByteaToOBJECTBase64() {
        assertThat(PgTypeMapper.toSchemaType("bytea")).isEqualTo(SchemaType.OBJECT)
        assertThat(PgTypeMapper.toSchemaType("BYTEA")).isEqualTo(SchemaType.OBJECT)
    }

    // ------ LOCATION: PostGIS geometry/geography ------

    @Test
    fun mapsGeometryToLOCATION() {
        assertThat(PgTypeMapper.toSchemaType("geometry")).isEqualTo(SchemaType.LOCATION)
        assertThat(PgTypeMapper.toSchemaType("geography")).isEqualTo(SchemaType.LOCATION)
        assertThat(PgTypeMapper.toSchemaType("GEOMETRY")).isEqualTo(SchemaType.LOCATION)
    }

    // ------ Fallback ------

    @Test
    fun mapsUnknownTypeToOBJECTFallback() {
        assertThat(PgTypeMapper.toSchemaType("hstore")).isEqualTo(SchemaType.OBJECT)
        assertThat(PgTypeMapper.toSchemaType("xml")).isEqualTo(SchemaType.OBJECT)
        assertThat(PgTypeMapper.toSchemaType("some_custom_domain_type")).isEqualTo(SchemaType.OBJECT)
    }
}
