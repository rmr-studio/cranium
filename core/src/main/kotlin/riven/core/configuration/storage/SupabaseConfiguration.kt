package riven.core.configuration.storage

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.serializer.JacksonSerializer
import io.github.jan.supabase.storage.Storage
import riven.core.configuration.properties.ApplicationConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "supabase")
class SupabaseConfiguration(private val config: ApplicationConfigurationProperties) {
    @Bean
    fun supabaseClient(objectMapper: ObjectMapper): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = config.supabaseUrl,
            supabaseKey = config.supabaseKey
        ) {
            install(Storage)
            defaultSerializer = JacksonSerializer(objectMapper)
        }
    }
}