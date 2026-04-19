package riven.core.configuration.util

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.exc.InvalidTypeIdException
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import riven.core.models.entity.query.filter.FilterValue
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.models.integration.mapping.FieldTransform

/**
 * Verifies that [CaseInsensitiveTypeIdResolver] accepts any casing of type discriminator
 * values on deserialization, while serialization always outputs the canonical name
 * defined in @JsonSubTypes.
 *
 * Covers all three naming conventions used across the codebase:
 * - UPPER_SNAKE (QueryFilter: "AND", "OR", "ATTRIBUTE")
 * - lower_snake (FieldTransform: "direct", "type_coercion")
 * - UPPER (FilterValue: "LITERAL", "TEMPLATE")
 */
class CaseInsensitiveTypeIdResolverTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    // ------ QueryFilter (UPPER_SNAKE canonical names) ------

    @Nested
    inner class QueryFilterTests {

        @Test
        fun `deserializes uppercase type id`() {
            val json = """{"type":"OR","conditions":[]}"""
            val result = mapper.readValue<QueryFilter>(json)
            assertInstanceOf(QueryFilter.Or::class.java, result)
        }

        @Test
        fun `deserializes lowercase type id`() {
            val json = """{"type":"or","conditions":[]}"""
            val result = mapper.readValue<QueryFilter>(json)
            assertInstanceOf(QueryFilter.Or::class.java, result)
        }

        @Test
        fun `deserializes mixed-case type id`() {
            val json = """{"type":"Or","conditions":[]}"""
            val result = mapper.readValue<QueryFilter>(json)
            assertInstanceOf(QueryFilter.Or::class.java, result)
        }

        @Test
        fun `deserializes underscore type id case-insensitively`() {
            val json = """{"type":"is_related_to"}"""
            val result = mapper.readValue<QueryFilter>(json)
            assertInstanceOf(QueryFilter.IsRelatedTo::class.java, result)
        }

        @Test
        fun `serializes with canonical UPPER_SNAKE name`() {
            val filter: QueryFilter = QueryFilter.Or(conditions = emptyList())
            val json = mapper.writerFor(QueryFilter::class.java).writeValueAsString(filter)
            assert(json.contains(""""type":"OR"""")) { "Expected canonical 'OR' in: $json" }
        }
    }

    // ------ FilterValue (UPPER_SNAKE canonical names, "kind" property) ------

    @Nested
    inner class FilterValueTests {

        @Test
        fun `deserializes lowercase kind`() {
            val json = """{"kind":"literal","value":"Active"}"""
            val result = mapper.readValue<FilterValue>(json)
            assertInstanceOf(FilterValue.Literal::class.java, result)
            assertEquals("Active", (result as FilterValue.Literal).value)
        }

        @Test
        fun `deserializes mixed-case kind`() {
            val json = """{"kind":"Literal","value":42}"""
            val result = mapper.readValue<FilterValue>(json)
            assertInstanceOf(FilterValue.Literal::class.java, result)
        }

        @Test
        fun `serializes with canonical name`() {
            val value: FilterValue = FilterValue.Template(expression = "{{ x }}")
            val json = mapper.writerFor(FilterValue::class.java).writeValueAsString(value)
            assert(json.contains(""""kind":"TEMPLATE"""")) { "Expected canonical 'TEMPLATE' in: $json" }
        }
    }

    // ------ FieldTransform (lower_snake canonical names) ------

    @Nested
    inner class FieldTransformTests {

        @Test
        fun `deserializes uppercase type id`() {
            val json = """{"type":"DIRECT"}"""
            val result = mapper.readValue<FieldTransform>(json)
            assertInstanceOf(FieldTransform.Direct::class.java, result)
        }

        @Test
        fun `deserializes mixed-case type id`() {
            val json = """{"type":"Direct"}"""
            val result = mapper.readValue<FieldTransform>(json)
            assertInstanceOf(FieldTransform.Direct::class.java, result)
        }

        @Test
        fun `deserializes underscore variant case-insensitively`() {
            val json = """{"type":"TYPE_COERCION","targetType":"string"}"""
            val result = mapper.readValue<FieldTransform>(json)
            assertInstanceOf(FieldTransform.TypeCoercion::class.java, result)
        }

        @Test
        fun `serializes with canonical lower_snake name`() {
            val transform: FieldTransform = FieldTransform.Direct
            val json = mapper.writerFor(FieldTransform::class.java).writeValueAsString(transform)
            assert(json.contains(""""type":"direct"""")) { "Expected canonical 'direct' in: $json" }
        }
    }

    // ------ Error handling ------

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `unknown type id produces InvalidTypeIdException`() {
            val json = """{"type":"NONEXISTENT","conditions":[]}"""
            val ex = assertThrows<InvalidTypeIdException> {
                mapper.readValue<QueryFilter>(json)
            }
            assert(ex.message?.contains("NONEXISTENT") == true) {
                "Error should mention the invalid type id, got: ${ex.message}"
            }
        }
    }
}
