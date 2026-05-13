package cranium.core.models.connector.request

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import cranium.core.enums.connector.SslMode
import java.util.UUID

/**
 * DTO bean-validation coverage so the @field:NotBlank / @field:Min / @field:Max /
 * @field:Size / @field:NotNull annotations are exercised at the model layer
 * rather than only transitively at the controller MockMvc layer.
 *
 * Also asserts the toString-level password redaction guardrail.
 */
class CreateDataConnectorConnectionRequestValidationTest {

    private val validator: Validator =
        Validation.buildDefaultValidatorFactory().validator

    private fun valid() = CreateDataConnectorConnectionRequest(
        workspaceId = UUID.randomUUID(),
        name = "prod-warehouse",
        host = "db.example.com",
        port = 5432,
        database = "analytics",
        user = "readonly",
        password = "hunter2",
        sslMode = SslMode.REQUIRE,
    )

    @Test
    fun `valid request has zero constraint violations`() {
        val violations = validator.validate(valid())
        assertTrue(violations.isEmpty(), "Expected no violations, got: $violations")
    }

    @Test
    fun `blank password triggers NotBlank on password`() {
        val violations = validator.validate(valid().copy(password = ""))
        assertEquals(1, violations.size)
        val v = violations.first()
        assertEquals("password", v.propertyPath.toString())
        assertTrue(v.constraintDescriptor.annotation.annotationClass.simpleName == "NotBlank")
    }

    @Test
    fun `port 0 triggers Min on port`() {
        val violations = validator.validate(valid().copy(port = 0))
        assertEquals(1, violations.size)
        assertEquals("port", violations.first().propertyPath.toString())
    }

    @Test
    fun `port 70000 triggers Max on port`() {
        val violations = validator.validate(valid().copy(port = 70000))
        assertEquals(1, violations.size)
        assertEquals("port", violations.first().propertyPath.toString())
    }

    @Test
    fun `overlong name triggers Size on name`() {
        val violations = validator.validate(valid().copy(name = "a".repeat(300)))
        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    fun `toString does not leak password value`() {
        val rendered = valid().copy(password = "hunter2").toString()
        assertTrue(rendered.contains("password=***"))
        assertFalse(rendered.contains("hunter2"), "toString leaked password")
    }
}
