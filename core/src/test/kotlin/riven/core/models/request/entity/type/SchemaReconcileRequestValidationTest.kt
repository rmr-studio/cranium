package riven.core.models.request.entity.type

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Regression: SchemaReconcileRequest used to lack field validation, so an empty entityTypeIds list
 * deserialized cleanly and caused the reconcile endpoint to silently no-op. The fix adds @NotEmpty,
 * paired with @Valid on the controller param. This test verifies the constraint fires on empty input
 * and stays silent for a non-empty list.
 */
class SchemaReconcileRequestValidationTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    @Test
    fun `empty entityTypeIds produces a NotEmpty violation`() {
        val request = SchemaReconcileRequest(entityTypeIds = emptyList())

        val violations = validator.validate(request)

        assertEquals(1, violations.size)
        val violation = violations.first()
        assertEquals("entityTypeIds", violation.propertyPath.toString())
        assertTrue(violation.message.contains("must not be empty", ignoreCase = true))
    }

    @Test
    fun `non-empty entityTypeIds is valid`() {
        val request = SchemaReconcileRequest(entityTypeIds = listOf(UUID.randomUUID()))

        val violations = validator.validate(request)

        assertTrue(violations.isEmpty())
    }
}
