package riven.core.enums.connector

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Postgres SSL mode for a data connector connection.
 *
 * Values are the canonical libpq `sslmode` parameter strings so the wire
 * representation matches what the JDBC driver consumes directly. Jackson
 * round-trips via [JsonValue]/[JsonCreator] for all four values.
 */
enum class SslMode(@get:JsonValue val value: String) {
    REQUIRE("require"),
    VERIFY_CA("verify-ca"),
    VERIFY_FULL("verify-full"),
    PREFER("prefer");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromValue(v: String): SslMode =
            entries.firstOrNull { it.value == v }
                ?: throw IllegalArgumentException("Unknown sslMode: '$v'")
    }
}
