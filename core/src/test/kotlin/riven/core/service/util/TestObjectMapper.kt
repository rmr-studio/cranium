package riven.core.service.util

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.text.SimpleDateFormat
import java.util.TimeZone

object TestObjectMapper {

    /** Preconfigured Jackson 3 mapper — Kotlin + auto-registered JSR-310 + UTC ISO-8601 dates. */
    fun init(): ObjectMapper {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .defaultDateFormat(dateFormat)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()
    }

    val objectMapper: ObjectMapper = init()
}
