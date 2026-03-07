package riven.core.service.integration.mapping

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import riven.core.configuration.util.LoggerConfig
import riven.core.enums.common.validation.SchemaType
import riven.core.models.integration.mapping.FieldTransform
import riven.core.models.integration.mapping.ResolvedFieldMapping
import java.util.*

/**
 * Comprehensive unit tests for SchemaMappingService.
 *
 * Covers all transform types (Direct, TypeCoercion, DefaultValue, JsonPathExtraction),
 * resilience behaviors (missing fields, failed transforms), key mapping translation,
 * and field coverage health reporting.
 */
@SpringBootTest(
    classes = [
        LoggerConfig::class,
        SchemaMappingService::class
    ]
)
class SchemaMappingServiceTest {

    @Autowired
    private lateinit var schemaMappingService: SchemaMappingService

    private val nameKey = "name"
    private val emailKey = "email"
    private val phoneKey = "phone"
    private val ageKey = "age"
    private val activeKey = "active"
    private val cityKey = "city"

    private val nameUuid = UUID.randomUUID()
    private val emailUuid = UUID.randomUUID()
    private val phoneUuid = UUID.randomUUID()
    private val ageUuid = UUID.randomUUID()
    private val activeUuid = UUID.randomUUID()
    private val cityUuid = UUID.randomUUID()

    private lateinit var defaultKeyMapping: Map<String, UUID>

    @BeforeEach
    fun setUp() {
        defaultKeyMapping = mapOf(
            nameKey to nameUuid,
            emailKey to emailUuid,
            phoneKey to phoneUuid,
            ageKey to ageUuid,
            activeKey to activeUuid,
            cityKey to cityUuid
        )
    }

    // ------ Core Mapping ------

    @Nested
    inner class CoreMapping {

        @Test
        fun `mapPayload with direct transforms maps source values to target attributes unchanged`() {
            val payload = mapOf(
                "first_name" to "Alice",
                "email_address" to "alice@example.com"
            )
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.Direct, SchemaType.TEXT),
                emailKey to ResolvedFieldMapping("email_address", FieldTransform.Direct, SchemaType.EMAIL)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(2, result.attributes.size)
            assertEquals("Alice", result.attributes[nameUuid.toString()]?.value)
            assertEquals(SchemaType.TEXT, result.attributes[nameUuid.toString()]?.schemaType)
            assertEquals("alice@example.com", result.attributes[emailUuid.toString()]?.value)
            assertEquals(SchemaType.EMAIL, result.attributes[emailUuid.toString()]?.schemaType)
            assertTrue(result.warnings.isEmpty())
            assertTrue(result.errors.isEmpty())
            assertTrue(result.success)
        }

        @Test
        fun `mapPayload with empty payload returns empty attributes with warnings for all fields`() {
            val payload = emptyMap<String, Any?>()
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.Direct, SchemaType.TEXT),
                emailKey to ResolvedFieldMapping("email_address", FieldTransform.Direct, SchemaType.EMAIL)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertTrue(result.attributes.isEmpty())
            assertEquals(2, result.warnings.size)
            assertTrue(result.errors.isEmpty())
            assertEquals(0, result.fieldCoverage.mapped)
            assertEquals(2, result.fieldCoverage.total)
            assertEquals(0.0, result.fieldCoverage.ratio)
        }

        @Test
        fun `mapPayload with empty field mappings returns empty result with zero coverage`() {
            val payload = mapOf("name" to "Alice")
            val fieldMappings = emptyMap<String, ResolvedFieldMapping>()

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertTrue(result.attributes.isEmpty())
            assertTrue(result.warnings.isEmpty())
            assertTrue(result.errors.isEmpty())
            assertEquals(0, result.fieldCoverage.mapped)
            assertEquals(0, result.fieldCoverage.total)
            assertEquals(0.0, result.fieldCoverage.ratio)
        }
    }

    // ------ Direct Transform ------

    @Nested
    inner class DirectTransform {

        @Test
        fun `Direct transform passes value through unchanged`() {
            val payload = mapOf("phone_number" to "+1234567890")
            val fieldMappings = mapOf(
                phoneKey to ResolvedFieldMapping("phone_number", FieldTransform.Direct, SchemaType.PHONE)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals("+1234567890", result.attributes[phoneUuid.toString()]?.value)
            assertEquals(SchemaType.PHONE, result.attributes[phoneUuid.toString()]?.schemaType)
        }

        @Test
        fun `Direct transform preserves null value`() {
            val payload = mapOf("first_name" to null)
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.Direct, SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(1, result.attributes.size)
            assertNull(result.attributes[nameUuid.toString()]?.value)
        }
    }

    // ------ TypeCoercion Transform ------

    @Nested
    inner class TypeCoercionTransform {

        @Test
        fun `TypeCoercion number converts string to Double`() {
            val payload = mapOf("age_str" to "123.45")
            val fieldMappings = mapOf(
                ageKey to ResolvedFieldMapping("age_str", FieldTransform.TypeCoercion("number"), SchemaType.NUMBER)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(123.45, result.attributes[ageUuid.toString()]?.value)
            assertEquals(SchemaType.NUMBER, result.attributes[ageUuid.toString()]?.schemaType)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `TypeCoercion number passes through numeric value unchanged`() {
            val payload = mapOf("age_num" to 42)
            val fieldMappings = mapOf(
                ageKey to ResolvedFieldMapping("age_num", FieldTransform.TypeCoercion("number"), SchemaType.NUMBER)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            // Numeric input coerced to Double
            assertEquals(42.0, result.attributes[ageUuid.toString()]?.value)
        }

        @Test
        fun `TypeCoercion number with non-numeric string produces error not exception`() {
            val payload = mapOf("age_str" to "abc")
            val fieldMappings = mapOf(
                ageKey to ResolvedFieldMapping("age_str", FieldTransform.TypeCoercion("number"), SchemaType.NUMBER)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertTrue(result.attributes.isEmpty())
            assertEquals(1, result.errors.size)
            assertEquals(ageKey, result.errors[0].targetKey)
            assertEquals("age_str", result.errors[0].sourcePath)
            assertFalse(result.success)
        }

        @Test
        fun `TypeCoercion boolean converts string true to Boolean`() {
            val payload = mapOf("is_active" to "true")
            val fieldMappings = mapOf(
                activeKey to ResolvedFieldMapping("is_active", FieldTransform.TypeCoercion("boolean"), SchemaType.CHECKBOX)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(true, result.attributes[activeUuid.toString()]?.value)
            assertEquals(SchemaType.CHECKBOX, result.attributes[activeUuid.toString()]?.schemaType)
        }

        @Test
        fun `TypeCoercion boolean converts string false to Boolean`() {
            val payload = mapOf("is_active" to "false")
            val fieldMappings = mapOf(
                activeKey to ResolvedFieldMapping("is_active", FieldTransform.TypeCoercion("boolean"), SchemaType.CHECKBOX)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(false, result.attributes[activeUuid.toString()]?.value)
        }

        @Test
        fun `TypeCoercion datetime parses ISO-8601 string`() {
            val dateKey = "created"
            val dateUuid = UUID.randomUUID()
            val keyMapping = defaultKeyMapping + (dateKey to dateUuid)

            val payload = mapOf("created_at" to "2024-01-15T10:30:00Z")
            val fieldMappings = mapOf(
                dateKey to ResolvedFieldMapping("created_at", FieldTransform.TypeCoercion("datetime"), SchemaType.DATETIME)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, keyMapping)

            assertEquals("2024-01-15T10:30:00Z", result.attributes[dateUuid.toString()]?.value)
            assertEquals(SchemaType.DATETIME, result.attributes[dateUuid.toString()]?.schemaType)
        }

        @Test
        fun `TypeCoercion date parses ISO date string`() {
            val dateKey = "birthday"
            val dateUuid = UUID.randomUUID()
            val keyMapping = defaultKeyMapping + (dateKey to dateUuid)

            val payload = mapOf("birth_date" to "1990-05-20")
            val fieldMappings = mapOf(
                dateKey to ResolvedFieldMapping("birth_date", FieldTransform.TypeCoercion("date"), SchemaType.DATE)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, keyMapping)

            assertEquals("1990-05-20", result.attributes[dateUuid.toString()]?.value)
            assertEquals(SchemaType.DATE, result.attributes[dateUuid.toString()]?.schemaType)
        }
    }

    // ------ DefaultValue Transform ------

    @Nested
    inner class DefaultValueTransform {

        @Test
        fun `DefaultValue returns default when source field is missing from payload`() {
            val payload = emptyMap<String, Any?>()
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.DefaultValue("Unknown"), SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals("Unknown", result.attributes[nameUuid.toString()]?.value)
            assertEquals(SchemaType.TEXT, result.attributes[nameUuid.toString()]?.schemaType)
            assertTrue(result.warnings.isEmpty())
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `DefaultValue returns actual source value when source field is present`() {
            val payload = mapOf("first_name" to "Alice")
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.DefaultValue("Unknown"), SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals("Alice", result.attributes[nameUuid.toString()]?.value)
        }

        @Test
        fun `DefaultValue returns default when source field is null`() {
            val payload = mapOf("first_name" to null)
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.DefaultValue("Unknown"), SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals("Unknown", result.attributes[nameUuid.toString()]?.value)
        }
    }

    // ------ JsonPathExtraction Transform ------

    @Nested
    inner class JsonPathExtractionTransform {

        @Test
        fun `JsonPathExtraction extracts nested value from dot-separated path`() {
            val payload = mapOf(
                "address" to mapOf(
                    "city" to "NYC",
                    "state" to "NY"
                )
            )
            val fieldMappings = mapOf(
                cityKey to ResolvedFieldMapping("address", FieldTransform.JsonPathExtraction("city"), SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals("NYC", result.attributes[cityUuid.toString()]?.value)
            assertEquals(SchemaType.TEXT, result.attributes[cityUuid.toString()]?.schemaType)
        }

        @Test
        fun `JsonPathExtraction traverses deeply nested path`() {
            val payload = mapOf(
                "data" to mapOf(
                    "contact" to mapOf(
                        "address" to mapOf(
                            "zip" to "10001"
                        )
                    )
                )
            )
            val zipKey = "zip"
            val zipUuid = UUID.randomUUID()
            val keyMapping = defaultKeyMapping + (zipKey to zipUuid)
            val fieldMappings = mapOf(
                zipKey to ResolvedFieldMapping("data", FieldTransform.JsonPathExtraction("contact.address.zip"), SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, keyMapping)

            assertEquals("10001", result.attributes[zipUuid.toString()]?.value)
        }

        @Test
        fun `JsonPathExtraction with missing nested path produces warning`() {
            val payload = mapOf(
                "address" to mapOf("state" to "NY")
            )
            val fieldMappings = mapOf(
                cityKey to ResolvedFieldMapping("address", FieldTransform.JsonPathExtraction("city"), SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertTrue(result.attributes.isEmpty())
            assertEquals(1, result.warnings.size)
            assertEquals(cityKey, result.warnings[0].targetKey)
        }

        @Test
        fun `JsonPathExtraction when source field is not a map produces error`() {
            val payload = mapOf("address" to "not-a-map")
            val fieldMappings = mapOf(
                cityKey to ResolvedFieldMapping("address", FieldTransform.JsonPathExtraction("city"), SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertTrue(result.attributes.isEmpty())
            assertEquals(1, result.errors.size)
        }
    }

    // ------ Resilience ------

    @Nested
    inner class Resilience {

        @Test
        fun `missing source field is skipped with warning, other fields still map`() {
            val payload = mapOf("email_address" to "alice@example.com")
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.Direct, SchemaType.TEXT),
                emailKey to ResolvedFieldMapping("email_address", FieldTransform.Direct, SchemaType.EMAIL)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(1, result.attributes.size)
            assertEquals("alice@example.com", result.attributes[emailUuid.toString()]?.value)
            assertEquals(1, result.warnings.size)
            assertEquals(nameKey, result.warnings[0].targetKey)
            assertEquals("first_name", result.warnings[0].sourcePath)
        }

        @Test
        fun `failed transform is skipped with error, other fields still map`() {
            val payload = mapOf(
                "first_name" to "Alice",
                "age_str" to "not-a-number"
            )
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.Direct, SchemaType.TEXT),
                ageKey to ResolvedFieldMapping("age_str", FieldTransform.TypeCoercion("number"), SchemaType.NUMBER)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(1, result.attributes.size)
            assertEquals("Alice", result.attributes[nameUuid.toString()]?.value)
            assertEquals(1, result.errors.size)
            assertEquals(ageKey, result.errors[0].targetKey)
        }

        @Test
        fun `multiple failures all captured with partial result returned`() {
            val payload = mapOf(
                "email_address" to "alice@example.com",
                "age_str" to "bad",
                "active_str" to "maybe"
            )
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("missing_field", FieldTransform.Direct, SchemaType.TEXT),
                emailKey to ResolvedFieldMapping("email_address", FieldTransform.Direct, SchemaType.EMAIL),
                ageKey to ResolvedFieldMapping("age_str", FieldTransform.TypeCoercion("number"), SchemaType.NUMBER),
                activeKey to ResolvedFieldMapping("active_str", FieldTransform.TypeCoercion("boolean"), SchemaType.CHECKBOX)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(1, result.attributes.size) // only email mapped
            assertEquals(1, result.warnings.size) // missing_field
            assertEquals(2, result.errors.size) // bad number + bad boolean
            assertFalse(result.success)
        }
    }

    // ------ Field Coverage ------

    @Nested
    inner class FieldCoverageTests {

        @Test
        fun `full success gives ratio 1_0`() {
            val payload = mapOf("first_name" to "Alice", "email_address" to "a@b.com")
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.Direct, SchemaType.TEXT),
                emailKey to ResolvedFieldMapping("email_address", FieldTransform.Direct, SchemaType.EMAIL)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(2, result.fieldCoverage.mapped)
            assertEquals(2, result.fieldCoverage.total)
            assertEquals(1.0, result.fieldCoverage.ratio)
        }

        @Test
        fun `partial success gives correct ratio`() {
            val payload = mapOf("email_address" to "a@b.com")
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.Direct, SchemaType.TEXT),
                emailKey to ResolvedFieldMapping("email_address", FieldTransform.Direct, SchemaType.EMAIL)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertEquals(1, result.fieldCoverage.mapped)
            assertEquals(2, result.fieldCoverage.total)
            assertEquals(0.5, result.fieldCoverage.ratio)
        }
    }

    // ------ Key Mapping ------

    @Nested
    inner class KeyMappingTests {

        @Test
        fun `output attributes use UUID string keys from keyMapping`() {
            val payload = mapOf("first_name" to "Alice")
            val fieldMappings = mapOf(
                nameKey to ResolvedFieldMapping("first_name", FieldTransform.Direct, SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertTrue(result.attributes.containsKey(nameUuid.toString()))
            assertFalse(result.attributes.containsKey(nameKey))
        }

        @Test
        fun `missing key in keyMapping produces error`() {
            val payload = mapOf("first_name" to "Alice")
            val fieldMappings = mapOf(
                "unmapped_key" to ResolvedFieldMapping("first_name", FieldTransform.Direct, SchemaType.TEXT)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, defaultKeyMapping)

            assertTrue(result.attributes.isEmpty())
            assertEquals(1, result.errors.size)
            assertEquals("unmapped_key", result.errors[0].targetKey)
        }
    }

    // ------ SchemaType Resolution ------

    @Nested
    inner class SchemaTypeResolution {

        @Test
        fun `output EntityAttributePrimitivePayload uses targetSchemaType from field mapping`() {
            val payload = mapOf("url_field" to "https://example.com")
            val urlKey = "website"
            val urlUuid = UUID.randomUUID()
            val keyMapping = defaultKeyMapping + (urlKey to urlUuid)
            val fieldMappings = mapOf(
                urlKey to ResolvedFieldMapping("url_field", FieldTransform.Direct, SchemaType.URL)
            )

            val result = schemaMappingService.mapPayload(payload, fieldMappings, keyMapping)

            assertEquals(SchemaType.URL, result.attributes[urlUuid.toString()]?.schemaType)
        }
    }
}
