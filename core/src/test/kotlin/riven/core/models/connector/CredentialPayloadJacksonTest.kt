package cranium.core.models.connector

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.DatabindException
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import cranium.core.enums.connector.SslMode

/**
 * Jackson round-trip coverage for [CredentialPayload] and [SslMode].
 *
 * Closes the silent-DataCorruption-on-read failure mode: if [SslMode]'s
 * `@JsonValue` / `@JsonCreator` pair ever regresses, stored credentials
 * become unreadable and the corresponding connection transitions to
 * FAILED. These tests catch the regression at build time.
 */
class CredentialPayloadJacksonTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    @ParameterizedTest
    @EnumSource(SslMode::class)
    fun `SslMode round-trips through Jackson for all four values`(mode: SslMode) {
        val payload = CredentialPayload(
            host = "db.example.com",
            port = 5432,
            database = "analytics",
            user = "readonly",
            password = "hunter2",
            sslMode = mode,
        )

        val json = mapper.writeValueAsString(payload)

        assertTrue(
            json.contains("\"sslMode\":\"${mode.value}\""),
            "Expected JSON to contain kebab-case sslMode ${mode.value}, got: $json"
        )

        val decoded: CredentialPayload = mapper.readValue(json)
        assertEquals(mode, decoded.sslMode)
    }

    @Test
    fun `full payload round-trips byte-for-byte`() {
        val payload = CredentialPayload(
            host = "db.example.com",
            port = 5432,
            database = "analytics",
            user = "readonly",
            password = "hunter2",
            sslMode = SslMode.VERIFY_FULL,
        )

        val json = mapper.writeValueAsString(payload)
        val decoded: CredentialPayload = mapper.readValue(json)

        assertEquals(payload, decoded)
        assertEquals("hunter2", decoded.password, "password must round-trip without redaction")
    }

    @Test
    fun `deserialising unknown sslMode string throws`() {
        val json =
            """{"host":"db","port":5432,"database":"d","user":"u","password":"p","sslMode":"disable"}"""

        // Jackson wraps the SslMode.fromValue IllegalArgumentException in a
        // ValueInstantiationException (subtype of JsonMappingException). Either
        // is acceptable — the contract is "deserialisation does not silently
        // succeed for an unknown sslMode".
        assertThrows(DatabindException::class.java) {
            mapper.readValue<CredentialPayload>(json)
        }
    }

    @Test
    fun `toString redacts password`() {
        val payload = CredentialPayload(
            host = "db",
            port = 5432,
            database = "d",
            user = "u",
            password = "hunter2",
            sslMode = SslMode.REQUIRE,
        )

        val rendered = payload.toString()
        assertTrue(rendered.contains("password=***"))
        assertFalse(rendered.contains("hunter2"), "toString must not leak password")
    }
}
