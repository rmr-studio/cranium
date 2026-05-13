package cranium.core.validation.entity

import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import cranium.core.enums.entity.EntitySelectType
import cranium.core.enums.entity.query.FilterOperator
import cranium.core.models.entity.query.filter.FilterValue
import cranium.core.models.entity.query.filter.QueryFilter
import cranium.core.models.request.entity.DeleteEntityRequest
import java.util.*

class DeleteEntityRequestValidatorTest {

    private val validator = DeleteEntityRequestValidator()
    private lateinit var context: ConstraintValidatorContext
    private lateinit var violationBuilder: ConstraintValidatorContext.ConstraintViolationBuilder

    @BeforeEach
    fun setUp() {
        context = mock()
        violationBuilder = mock()
        whenever(context.buildConstraintViolationWithTemplate(any())).thenReturn(violationBuilder)
        whenever(violationBuilder.addConstraintViolation()).thenReturn(context)
    }

    private fun createFilter(): QueryFilter = QueryFilter.Attribute(
        attributeId = UUID.randomUUID(),
        operator = FilterOperator.EQUALS,
        value = FilterValue.Literal("test"),
    )

    @Nested
    inner class ByIdMode {

        @Test
        fun `valid BY_ID request with entityIds passes`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = listOf(UUID.randomUUID()),
            )
            assertTrue(validator.isValid(request, context))
        }

        @Test
        fun `BY_ID with null entityIds fails`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = null,
            )
            assertFalse(validator.isValid(request, context))
            verify(context).buildConstraintViolationWithTemplate("entityIds is required when type is BY_ID")
        }

        @Test
        fun `BY_ID with empty entityIds fails`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = emptyList(),
            )
            assertFalse(validator.isValid(request, context))
            verify(context).buildConstraintViolationWithTemplate("entityIds is required when type is BY_ID")
        }

        @Test
        fun `BY_ID with filter fails`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = listOf(UUID.randomUUID()),
                filter = createFilter(),
            )
            assertFalse(validator.isValid(request, context))
            verify(context).buildConstraintViolationWithTemplate("filter must not be provided when type is BY_ID")
        }

        @Test
        fun `BY_ID with excludeIds fails`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = listOf(UUID.randomUUID()),
                excludeIds = listOf(UUID.randomUUID()),
            )
            assertFalse(validator.isValid(request, context))
            verify(context).buildConstraintViolationWithTemplate("excludeIds must not be provided when type is BY_ID")
        }

        @Test
        fun `BY_ID with entityTypeId fails`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = listOf(UUID.randomUUID()),
                entityTypeId = UUID.randomUUID(),
            )
            assertFalse(validator.isValid(request, context))
            verify(context).buildConstraintViolationWithTemplate("entityTypeId must not be provided when type is BY_ID")
        }

        @Test
        fun `BY_ID with multiple violations reports all`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = null,
                filter = createFilter(),
                excludeIds = listOf(UUID.randomUUID()),
                entityTypeId = UUID.randomUUID(),
            )
            assertFalse(validator.isValid(request, context))
            verify(context, times(4)).buildConstraintViolationWithTemplate(any())
        }
    }

    @Nested
    inner class AllMode {

        @Test
        fun `valid ALL request with entityTypeId and filter passes`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.ALL,
                entityTypeId = UUID.randomUUID(),
                filter = createFilter(),
            )
            assertTrue(validator.isValid(request, context))
        }

        @Test
        fun `ALL with excludeIds and filter passes`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.ALL,
                entityTypeId = UUID.randomUUID(),
                filter = createFilter(),
                excludeIds = listOf(UUID.randomUUID()),
            )
            assertTrue(validator.isValid(request, context))
        }

        @Test
        fun `ALL without entityTypeId fails`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.ALL,
                filter = createFilter(),
            )
            assertFalse(validator.isValid(request, context))
            verify(context).buildConstraintViolationWithTemplate("entityTypeId is required when type is ALL")
        }

        @Test
        fun `ALL without filter fails`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.ALL,
                entityTypeId = UUID.randomUUID(),
            )
            assertFalse(validator.isValid(request, context))
            verify(context).buildConstraintViolationWithTemplate("filter is required when type is ALL")
        }

        @Test
        fun `ALL with entityIds fails`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.ALL,
                entityTypeId = UUID.randomUUID(),
                filter = createFilter(),
                entityIds = listOf(UUID.randomUUID()),
            )
            assertFalse(validator.isValid(request, context))
            verify(context).buildConstraintViolationWithTemplate("entityIds must not be provided when type is ALL")
        }

        @Test
        fun `ALL with multiple violations reports all`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.ALL,
                entityIds = listOf(UUID.randomUUID()),
            )
            assertFalse(validator.isValid(request, context))
            // Missing entityTypeId + missing filter + entityIds present = 3 violations
            verify(context, times(3)).buildConstraintViolationWithTemplate(any())
        }
    }
}
