package cranium.core.configuration.util

import io.hypersistence.utils.hibernate.type.util.JsonSerializer
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.util.StdDateFormat
import tools.jackson.module.kotlin.KotlinModule
import java.util.TimeZone

/**
 * Jackson-based deep-copy serializer for hypersistence-utils JSONB columns.
 *
 * Replaces the default [io.hypersistence.utils.hibernate.type.util.ObjectMapperJsonSerializer] whose
 * `convertValue` + Java serialization cloning requires every JSONB-mapped Kotlin data class to
 * implement [java.io.Serializable]. Our domain models don't, so we round-trip via JSON instead.
 *
 * Registered via `hypersistence-utils.properties` (`hibernate.types.json.serializer`).
 * Must have a public no-arg constructor — hypersistence instantiates via reflection.
 */
class JacksonRoundTripJsonSerializer : JsonSerializer {

    /**
     * Deep-copy [value] by serializing to JSON and reading back.
     *
     * Uses a [tools.jackson.databind.JavaType] derived from the runtime class so nested
     * parameterized containers (e.g. `Map<String, Schema<UUID>>`, `List<ColumnConfiguration>`)
     * retain parametric fidelity where the concrete runtime type preserves them.
     *
     * Limitation: if [value] itself is only a generic-erased `T` (e.g. `List<Foo>` passed as
     * `Any`), the runtime class is still erased and inner generics fall back to Jackson defaults.
     * Callers should pass concrete containers whose element types are recoverable from the class.
     */
    override fun <T> clone(value: T?): T? {
        if (value == null) return null
        val javaType = MAPPER.typeFactory.constructType(value::class.java)
        val json = MAPPER.writeValueAsBytes(value)
        @Suppress("UNCHECKED_CAST")
        return MAPPER.readValue(json, javaType) as T
    }

    companion object {
        // Self-contained mapper so the serializer works before the Spring @Bean ObjectMapper is built
        // (hypersistence instantiates this class during Hibernate bootstrap). Mirrors ObjectMapperConfig.
        private val MAPPER: ObjectMapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .defaultDateFormat(StdDateFormat().withTimeZone(TimeZone.getTimeZone("UTC")))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build()
    }
}
