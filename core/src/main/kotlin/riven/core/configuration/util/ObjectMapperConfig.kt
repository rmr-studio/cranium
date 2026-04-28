package riven.core.configuration.util

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.util.StdDateFormat
import tools.jackson.module.kotlin.KotlinModule
import java.util.TimeZone

@Configuration
class ObjectMapperConfig { // Jackson 3 primary mapper

    @Bean
    fun objectMapper(): ObjectMapper {
        return JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .defaultDateFormat(StdDateFormat().withTimeZone(TimeZone.getTimeZone("UTC")))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build()
    }
}
